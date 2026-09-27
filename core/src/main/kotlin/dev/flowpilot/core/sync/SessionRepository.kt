package dev.flowpilot.core.sync

import dev.flowpilot.core.api.*
import dev.flowpilot.core.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SavedChat(val chat: ChatState, val session: Session? = null)

interface ChatStorage {
    suspend fun readChat(server: String, session: String): SavedChat?
    suspend fun writeChat(server: String, snapshot: SavedChat)
}

interface SessionSource {
    suspend fun session(id: String): Session
    suspend fun messages(id: String, cursor: String?): Pair<List<JsonObject>, Cursor>
    suspend fun permissions(id: String): List<PermissionRequest>
    suspend fun forms(id: String): List<Form>
    suspend fun inbox(id: String): List<InboxItem>
    suspend fun running(id: String): Boolean
    fun replay(id: String, after: Long): Flow<StreamSignal>
}

class ApiSessionSource(private val client: OpenCodeClient) : SessionSource {
    override suspend fun session(id: String) = client.session(id)
    override suspend fun messages(id: String, cursor: String?) = client.messages(id, cursor, 60)
    override suspend fun permissions(id: String) = client.permissions(id)
    override suspend fun forms(id: String) = client.forms(id)
    override suspend fun inbox(id: String) = client.inbox(id)
    override suspend fun running(id: String) = id in client.activeSessions()
    override fun replay(id: String, after: Long) = client.sessionLog(id, after, follow = false)
}

data class SessionSnapshot(
    val saved: SavedChat,
    val loading: Boolean = true,
    val syncing: Boolean = true,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    /** The chat no longer exists on the computer. Sync has stopped; the saved copy stays readable. */
    val gone: Boolean = false,
)

/** One writer per session. Network reads never hold the state lock or block event ingestion. */
class SessionRepository(
    private val server: String,
    val id: String,
    private val source: SessionSource,
    private val storage: ChatStorage,
    private val parent: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + dispatcher)
    private val lock = Any()
    private var current = SavedChat(ChatState(id))
    private var recovering = true
    @Volatile private var enabled = true
    @Volatile private var gone = false
    private var failures = 0
    private var generation = 0L
    private var revision = 0L
    private var dirty = false
    private var flushJob: Job? = null
    @Volatile private var recoveryJob: Job? = null
    private var controlsRevision = 0L
    private val buffered = ArrayList<ServerEvent>()
    private val localChanges = ArrayList<(ChatState) -> ChatState>()
    private val deltas = LinkedHashMap<Pair<String, String>, Pair<ServerEvent, StringBuilder>>()
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private data class Save(val version: Long, val snapshot: SavedChat)
    private var saveVersion = 0L
    private var writtenVersion = 0L
    private val writeLock = Mutex()
    private val saves = Channel<Save>(Channel.CONFLATED)
    private val olderLock = Mutex()
    private val _state = MutableStateFlow(SessionSnapshot(current))
    val state: StateFlow<SessionSnapshot> = _state.asStateFlow()

    init {
        scope.launch {
            for (snapshot in saves) {
                try { persist(snapshot) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { synchronized(lock) { _state.value = _state.value.copy(error = "Couldn't save offline history: ${e.message}") } }
            }
        }
        scope.launch {
            val cached = catching { storage.readChat(server, id) }.getOrNull()
            synchronized(lock) {
                if (cached != null) {
                    current = cached.copy(chat = cached.chat.copy(entries = (cached.chat.entries + current.chat.entries).distinctBy { it.id }))
                    publish(loading = false)
                }
            }
            refreshes.trySend(Unit)
            for (ignored in refreshes) {
                if (enabled && !gone) {
                    recoveryJob = scope.launch { recover() }
                    recoveryJob?.join()
                }
            }
        }
    }

    private fun schedulePublish() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(16)
            synchronized(lock) {
                flush()
                publish()
                flushJob = null
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (gone) return
        enabled = value
        if (value) invalidate() else synchronized(lock) {
            recoveryJob?.cancel()
            generation++
            recovering = true
            publish(syncing = true)
        }
    }

    /** Called before new connection events are distributed, closing the replay/live race. */
    fun invalidate() = synchronized(lock) {
        if (gone) return@synchronized
        recoveryJob?.cancel()
        flush()
        generation++
        recovering = true
        publish(syncing = true)
        refreshes.trySend(Unit)
        Unit
    }

    /** Never waits on a screen, disk, or network. Overflow invalidates the whole synchronization attempt. */
    fun accept(event: ServerEvent) = synchronized(lock) {
        if (!belongs(event) || gone) return@synchronized
        if (event.type == "session.deleted") {
            markGone()
            return@synchronized
        }
        if (recovering) {
            if (buffered.size == 4096) {
                buffered.clear()
                generation++
                refreshes.trySend(Unit)
            }
            buffered += event
        } else if (event.type.endsWith(".delta")) {
            val message = event.data["assistantMessageID"]?.jsonPrimitive?.content ?: return@synchronized
            val part = event.data["ordinal"]?.toString() ?: event.data["id"]?.toString().orEmpty()
            val key = message to "${event.type}:$part"
            val delta = event.data["delta"]?.jsonPrimitive?.content.orEmpty()
            deltas.getOrPut(key) { event to StringBuilder() }.second.append(delta)
            schedulePublish()
        } else {
            flush()
            apply(event)
            schedulePublish()
            queueSave()
        }
    }

    /** Stops syncing for good: every route answers 404 for a deleted chat, so retrying would never end. */
    private fun markGone() {
        gone = true
        recoveryJob?.cancel()
        generation++
        recovering = false
        buffered.clear()
        localChanges.clear()
        deltas.clear()
        _state.value = _state.value.copy(loading = false, syncing = false, gone = true, error = "This chat was deleted on your computer.")
    }

    private fun belongs(e: ServerEvent): Boolean = e.sessionID == id ||
        (e.data["form"] as? JsonObject)?.get("sessionID")?.jsonPrimitive?.content == id

    private fun apply(e: ServerEvent) {
        current = current.copy(chat = ChatReducer.reduce(current.chat, e))
        revision++
        dirty = true
        controlsRevision = revision
    }

    private fun flush() {
        deltas.values.forEach { (event, text) ->
            apply(event.copy(data = JsonObject(event.data + ("delta" to JsonPrimitive(text.toString())))))
        }
        deltas.clear()
    }

    fun update(transform: (ChatState) -> ChatState) = synchronized(lock) {
        flush()
        current = current.copy(chat = transform(current.chat))
        if (recovering) localChanges += transform
        controlsRevision = ++revision
        publish()
        queueSave()
    }

    private fun publish(
        loading: Boolean = _state.value.loading,
        syncing: Boolean = recovering,
    ) {
        dirty = false
        _state.value = _state.value.copy(saved = current, loading = loading, syncing = syncing)
    }

    private suspend fun recover() {
        val started = System.nanoTime()
        Diagnostics.record("sync.start")
        val start = synchronized(lock) { flush(); Triple(generation, current, revision) }
        try {
            // Replay into a private state. Only commit its cursor together with a reconciled snapshot.
            var replayed = start.second.chat
            val after = replayed.lastSeq
            var replaySucceeded = false
            if (after != null) {
                try {
                    withTimeout(20_000) {
                        source.replay(id, after).collect { signal ->
                            if (signal is StreamSignal.Event) replayed = ChatReducer.reduce(replayed, signal.event)
                        }
                    }
                    replaySucceeded = true
                } catch (e: TimeoutCancellationException) {
                    // Fall back to paged REST; this timeout does not cancel the owning scope.
                } catch (e: CancellationException) { throw e }
                catch (e: ApiException.Unauthorized) { throw e }
                catch (_: Exception) { /* Experimental log absent, expired, or temporarily unavailable. */ }
            }
            val known = start.second.chat.entries.filterNot { it.id.startsWith("local-") }.mapTo(HashSet()) { it.id }
            val pages = ArrayList<JsonObject>()
            var cursor: String? = null
            val seenCursors = HashSet<String>()
            do {
                val (page, next) = source.messages(id, cursor)
                pages += page
                cursor = next.next
                if (known.isEmpty() || page.any { it["id"]?.jsonPrimitive?.content in known }) break
                check(cursor == null || seenCursors.add(cursor)) { "Server repeated a history cursor" }
            } while (cursor != null)
            // Publish transcript before optional metadata/approval/catalog requests.
            synchronized(lock) {
                if (generation != start.first) return
                var chat = ChatReducer.mergeLatest(replayed, pages, cursor)
                chat = chat.copy(lastSeq = if (replaySucceeded) replayed.lastSeq else null)
                // An older-page load or local action may have completed while the network was busy.
                val restoredIds = chat.entries.mapTo(HashSet()) { it.id }
                val missingOlder = current.chat.entries.filter { old ->
                    old.id !in restoredIds && old.created < (chat.entries.firstOrNull()?.created ?: Long.MAX_VALUE)
                }
                chat = chat.copy(entries = (missingOlder + chat.entries).distinctBy { it.id })
                // Durable events are authoritative. Deltas captured during REST are intentionally omitted:
                // REST may already contain them; *.ended heals any missing ephemeral tail.
                buffered.filterNot { it.type.endsWith(".delta") }.forEach { chat = ChatReducer.reduce(chat, it) }
                Diagnostics.record("sync.buffered_events", buffered.size.toLong())
                buffered.clear()
                localChanges.forEach { chat = it(chat) }
                localChanges.clear()
                if (controlsRevision > start.third) {
                    chat = chat.copy(entries = (chat.entries + current.chat.entries.filter { it is ChatEntry.User && it.pending }).distinctBy { it.id })
                }
                current = current.copy(chat = chat)
                recovering = false
                failures = 0
                _state.value = _state.value.copy(error = null)
                publish(loading = false)
                queueSave()
            }
            Diagnostics.record("sync.duration_ms", (System.nanoTime() - started) / 1_000_000)
            refreshDetails(start.first)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val wait = synchronized(lock) {
                if (generation != start.first) return
                if (e is ApiException.Http && e.code == 404) {
                    markGone()
                    return
                }
                // Retain cached/live state. Stay in catch-up mode; don't advance a cursor across a gap.
                _state.value = _state.value.copy(loading = false, syncing = true, error = e.message)
                // 2s, 4s, 8s, 16s, then every 30s: an unreachable computer isn't asked every two seconds.
                minOf(30_000L, 2_000L shl minOf(failures++, 4))
            }
            if (e !is ApiException.Unauthorized) {
                delay(wait)
                refreshes.trySend(Unit)
            }
        }
    }

    private suspend fun refreshDetails(epoch: Long) = supervisorScope {
        val at = synchronized(lock) { revision }
        val session = async { catching { source.session(id) } }
        val permissions = async { catching { source.permissions(id) } }
        val forms = async { catching { source.forms(id) } }
        val inbox = async { catching { source.inbox(id) } }
        val running = async { catching { source.running(id) } }
        val s = session.await(); val p = permissions.await(); val f = forms.await(); val q = inbox.await(); val r = running.await()
        synchronized(lock) {
            if (epoch != generation) return@synchronized
            val old = current.chat
            // Never let a snapshot roll back live or locally confirmed control changes.
            val chat = if (controlsRevision > at) old else old.copy(
                title = s.getOrNull()?.title ?: old.title,
                agent = s.getOrNull()?.agent ?: old.agent,
                model = s.getOrNull()?.model ?: old.model,
                permissions = p.getOrNull() ?: old.permissions,
                forms = f.getOrNull() ?: old.forms,
                queued = q.getOrNull()?.filter { it.type == "user" }?.map { QueuedMessage(it.id, ChatReducer.inboxText(it.payload), it.delivery) } ?: old.queued,
                running = r.getOrNull() ?: old.running,
                runStartedAt = if (r.getOrNull() == false) null else old.runStartedAt ?: s.getOrNull()?.time?.updated,
            )
            current = SavedChat(chat, s.getOrNull() ?: current.session)
            val error = listOf(s, p, f, q, r).firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            _state.value = _state.value.copy(error = error)
            publish()
            queueSave()
        }
    }

    suspend fun loadOlder() = olderLock.withLock {
        val cursor = synchronized(lock) {
            if (recovering) return@withLock
            val next = current.chat.olderCursor ?: return@withLock
            _state.value = _state.value.copy(loadingOlder = true)
            next
        }
        try {
            val (messages, next) = source.messages(id, cursor)
            synchronized(lock) {
                if (current.chat.olderCursor == cursor) {
                    current = current.copy(chat = ChatReducer.prependOlder(current.chat, messages, next.next))
                    publish()
                    queueSave()
                }
            }
            val saved = synchronized(lock) { Save(++saveVersion, current) }
            persist(saved)
        } finally { synchronized(lock) { _state.value = _state.value.copy(loadingOlder = false) } }
    }

    private fun queueSave() { saves.trySend(Save(++saveVersion, current)) }

    private suspend fun persist(save: Save) = writeLock.withLock {
        if (save.version > writtenVersion) {
            storage.writeChat(server, save.snapshot)
            writtenVersion = save.version
        }
    }

    fun close() {
        val final = synchronized(lock) { flush(); Save(++saveVersion, current) }
        scope.cancel()
        parent.launch { catching { persist(final) } }
    }
}

/** runCatching must not turn structured cancellation into an empty successful-looking snapshot. */
inline fun <T> catching(block: () -> T): Result<T> = try { Result.success(block()) }
catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }
