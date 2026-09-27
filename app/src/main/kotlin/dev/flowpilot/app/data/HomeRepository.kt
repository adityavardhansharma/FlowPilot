package dev.flowpilot.app.data

import dev.flowpilot.core.api.*
import dev.flowpilot.core.home.HomeState
import dev.flowpilot.core.sync.catching
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface HomeStorage {
    suspend fun readHome(server: String): HomeSnapshot?
    suspend fun writeHome(server: String, snapshot: HomeSnapshot)
}

/** Shared Home state; invalidation delays run outside ingestion, and event overlays protect snapshots. */
class HomeRepository(
    private val server: String,
    private val client: OpenCodeClient,
    private val catalog: Catalog,
    private val cache: HomeStorage,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val paging = Mutex()
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val saves = Channel<HomeSnapshot>(Channel.CONFLATED)
    private val journal = ArrayDeque<Pair<Long, ServerEvent>>()
    private var revision = 0L
    @Volatile private var enabled = false
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    val loading = MutableStateFlow(true)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            val cached = catching { cache.readHome(server) }.getOrNull()
            synchronized(lock) {
                if (cached != null) {
                    var restored = HomeState().withPage(cached.sessions, cached.cursor, true).copy(projects = cached.projects.associateBy { it.id })
                    journal.forEach { restored = restored.reduce(it.second) }
                    _state.value = restored.copy(needsYou = _state.value.needsYou)
                    loading.value = false
                }
            }
            var failures = 0
            for (ignored in refreshes) {
                if (!enabled) continue
                delay(150)
                val success = refresh()
                if (success) failures = 0 else if (enabled) {
                    delay(minOf(30_000L, 1_000L shl minOf(failures++, 5)))
                    refreshes.trySend(Unit)
                }
            }
        }
        scope.launch { saves.receiveAsFlow().distinctUntilChanged().collect { snapshot -> catching { cache.writeHome(server, snapshot) }.onFailure { error.value = "Couldn't save Home: ${it.message}" } } }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) requestRefresh()
    }
    fun requestRefresh() { refreshes.trySend(Unit) }

    fun accept(event: ServerEvent) = synchronized(lock) {
        // Home ignores streamed text, and journaling it would push real changes out of the 4096-event window
        // during a refresh, which then gets thrown away and retried.
        if (event.type.endsWith(".delta")) return@synchronized
        journal.addLast(++revision to event)
        if (journal.size > 4096) journal.removeFirst()
        _state.value = _state.value.reduce(event)
        if (_state.value.stale) requestRefresh()
        save()
    }

    private suspend fun refresh(): Boolean {
        val start = synchronized(lock) { revision }
        refreshing.value = true
        try {
            coroutineScope {
                val p = async { catalog.projects() }
                val sessions = async { client.sessions(limit = 60) }
                val active = async { catching { catalog.active() } }
                val projects = p.await(); val page = sessions.await(); val running = active.await()
                synchronized(lock) {
                    if (journal.firstOrNull()?.first?.let { it > start + 1 } == true) {
                        requestRefresh()
                        return@coroutineScope
                    }
                    val old = _state.value
                    val ids = page.data.mapTo(HashSet()) { it.id }
                    val older = old.sessions.values.filter { it.id !in ids && page.data.isNotEmpty() && it.time.updated < page.data.last().time.updated }
                    var next = old.withPage(page.data + older, if (older.isEmpty()) page.cursor.next else old.nextCursor, true)
                        .copy(projects = projects.associateBy { it.id }, active = running.getOrNull() ?: old.active)
                    journal.filter { it.first > start }.forEach { next = next.reduce(it.second) }
                    _state.value = next.copy(stale = false)
                    error.value = running.exceptionOrNull()?.message
                    save()
                }
            }
            return true
        } catch (e: CancellationException) { throw e }
        catch (e: ApiException.Unauthorized) { error.value = e.message; enabled = false; return false }
        catch (e: Exception) { error.value = e.message; return false }
        finally { loading.value = false; refreshing.value = false }
    }

    suspend fun loadMore() = paging.withLock {
        val start = synchronized(lock) { revision to _state.value.nextCursor }
        val cursor = start.second ?: return@withLock
        val page = client.sessions(cursor = cursor, limit = 60)
        synchronized(lock) {
            if (_state.value.nextCursor != cursor) return@synchronized
            var next = _state.value.withPage(page.data, page.cursor.next, false)
            journal.filter { it.first > start.first }.forEach { next = next.reduce(it.second) }
            _state.value = next
            save()
        }
    }

    fun pending(asks: Map<String, Set<String>>) = synchronized(lock) { _state.value = _state.value.copy(needsYou = asks) }

    private fun save() {
        val state = _state.value
        saves.trySend(HomeSnapshot(state.sessions.values.toList(), state.projects.values.toList(), state.nextCursor))
    }
}
