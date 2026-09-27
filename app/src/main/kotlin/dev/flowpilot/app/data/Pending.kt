package dev.flowpilot.app.data

import dev.flowpilot.core.api.Decision
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.OpenCodeJson
import dev.flowpilot.core.api.PermissionRequest
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.Session
import dev.flowpilot.core.sync.catching
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Something an agent is waiting on, anywhere on the computer. */
@Serializable
sealed interface Ask {
    val id: String
    val sessionID: String
    @Serializable
    data class Permission(val request: PermissionRequest) : Ask {
        override val id get() = request.id
        override val sessionID get() = request.sessionID
    }
    @Serializable
    data class Question(val form: Form) : Ask {
        override val id get() = form.id
        override val sessionID get() = form.sessionID
    }
}

/**
 * Every pending approval and question across projects. Loaded per project folder (the list routes are
 * location-scoped), then kept live from the event stream.
 */
class Pending(
    private val client: OpenCodeClient,
    events: Flow<ServerEvent>,
    reconnects: Flow<Int>,
    private val scope: CoroutineScope,
    private val projects: suspend () -> List<dev.flowpilot.core.api.Project> = { client.projects() },
    private val cache: Cache? = null,
    private val server: String = "",
) {
    private val _asks = MutableStateFlow<List<Ask>>(emptyList())
    val asks: StateFlow<List<Ask>> = _asks.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, Session>>(emptyMap())
    /** Titles and folders for the chats that are asking, for Inbox cards. */
    val sessions: StateFlow<Map<String, Session>> = _sessions.asStateFlow()

    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val lock = Any()
    private val refreshLock = Mutex()
    private val permits = Semaphore(4)
    private var revision = 0L
    private val changed = mutableMapOf<String, Long>()
    private val locations = mutableMapOf<String, String>()
    private val fetching = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val saves = Channel<List<Ask>>(Channel.CONFLATED)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            val at = synchronized(lock) { revision }
            val raw = catching { cache?.readValue(server, "pending", "pending") }.getOrNull()
            val saved = raw?.let { catching { OpenCodeJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Ask.serializer()), it) }.getOrNull() }
            synchronized(lock) {
                if (saved != null) _asks.value = (_asks.value + saved.filter { (changed[it.id] ?: 0) <= at }).distinctBy { it.id }
            }
            ready.complete(Unit)
        }
        scope.launch {
            for (list in saves) catching {
                cache?.writeValue(server, "pending", "pending", OpenCodeJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Ask.serializer()), list))
            }.onFailure { _error.value = "Couldn't save approvals: ${it.message}" }
        }
        scope.launch { reconnects.collect { if (it > 0) catching { refresh() }.onFailure { _error.value = it.message } } }
        scope.launch { events.collect { synchronized(lock) { reduce(it); saves.trySend(_asks.value) } } }
    }

    suspend fun refresh() = refreshLock.withLock {
        ready.await()
        val at = synchronized(lock) { revision }
        val dirs = projects().map { it.canonical }.toMutableSet()
        kotlinx.coroutines.coroutineScope {
            _asks.value.map { it.sessionID }.distinct().filterNot { it in _sessions.value }.map { id ->
                async { permits.withPermit {
                    catching { client.session(id) }.getOrNull()?.let { s ->
                        _sessions.update { it + (id to s) }
                        synchronized(lock) { _asks.value.filter { it.sessionID == id }.forEach { locations[it.id] = s.location.directory } }
                    }
                } }
            }.awaitAll()
        }
        // Include worktree/session locations already learned from live asks.
        dirs += _sessions.value.values.map { it.location.directory }
        data class Result(val dir: String, val questions: Boolean, val asks: kotlin.Result<List<Ask>>)
        val results = kotlinx.coroutines.coroutineScope {
            dirs.flatMap { dir ->
                listOf(
                    async { permits.withPermit { Result(dir, false, catching { client.pendingPermissions(dir).map { Ask.Permission(it) } }) } },
                    async { permits.withPermit { Result(dir, true, catching { client.pendingForms(dir).map { Ask.Question(it) } }) } },
                )
            }.awaitAll()
        }
        synchronized(lock) {
            var list = _asks.value
            for (result in results) {
                val fresh = result.asks.getOrNull() ?: continue
                val incoming = fresh.mapTo(HashSet()) { it.id }
                list = list.filterNot { old ->
                    locations[old.id] == result.dir && (old is Ask.Question) == result.questions &&
                        old.id !in incoming && (changed[old.id] ?: 0) <= at
                }
                for (ask in fresh) {
                    if ((changed[ask.id] ?: 0) > at) continue
                    locations[ask.id] = result.dir
                    list = list.filterNot { it.id == ask.id } + ask
                }
            }
            _asks.value = list
            _error.value = results.firstNotNullOfOrNull { it.asks.exceptionOrNull()?.message }
            saves.trySend(list)
            changed.entries.removeAll { it.value <= at }
        }
        _asks.value.map { it.sessionID }.distinct().forEach(::ensureSession)
    }

    fun bySession(): Map<String, Set<String>> = _asks.value.groupBy { it.sessionID }.mapValues { e -> e.value.map { it.id }.toSet() }

    suspend fun reply(permission: PermissionRequest, decision: Decision) {
        client.replyPermission(permission.sessionID, permission.id, decision)
        remove(permission.id)
    }

    suspend fun answer(form: Form, answer: JsonObject) {
        client.replyForm(form.sessionID, form.id, answer)
        remove(form.id)
    }

    suspend fun dismiss(form: Form) {
        client.cancelForm(form.sessionID, form.id)
        remove(form.id)
    }

    private fun remove(id: String) = synchronized(lock) {
        changed[id] = ++revision
        _asks.update { list -> list.filterNot { it.id == id } }
        saves.trySend(_asks.value)
        Unit
    }

    private fun ensureSession(id: String) {
        if (id in _sessions.value || !fetching.add(id)) return
        scope.launch {
            try {
                catching { client.session(id) }.getOrNull()?.let { s ->
                    _sessions.update { it + (id to s) }
                    synchronized(lock) { _asks.value.filter { it.sessionID == id }.forEach { locations[it.id] = s.location.directory } }
                }
            } finally { fetching.remove(id) }
        }
    }

    private fun reduce(e: ServerEvent) {
        val d = e.data
        when (e.type) {
            "permission.asked" -> catching { OpenCodeJson.decodeFromJsonElement(PermissionRequest.serializer(), d) }.getOrNull()?.let { p ->
                e.directory?.let { locations[p.id] = it }
                changed[p.id] = ++revision
                _asks.update { list -> list.filterNot { it.id == p.id } + Ask.Permission(p) }
                ensureSession(p.sessionID)
            }
            "permission.replied" -> d.s("requestID")?.let(::remove)
            "form.created" -> (d["form"] as? JsonObject)?.let { catching { OpenCodeJson.decodeFromJsonElement(Form.serializer(), it) }.getOrNull() }?.let { f ->
                e.directory?.let { locations[f.id] = it }
                changed[f.id] = ++revision
                _asks.update { list -> list.filterNot { it.id == f.id } + Ask.Question(f) }
                ensureSession(f.sessionID)
            }
            "form.replied", "form.cancelled" -> d.s("id")?.let(::remove)
            "session.deleted" -> e.sessionID?.let { sid -> _asks.value.filter { it.sessionID == sid }.forEach { remove(it.id) } }
            "session.renamed" -> e.sessionID?.let { sid -> _sessions.update { m -> m[sid]?.let { m + (sid to it.copy(title = d.s("title"))) } ?: m } }
        }
    }
}

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
