package dev.flowpilot.app.data

import dev.flowpilot.core.api.ApiException
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.ProjectOps
import dev.flowpilot.core.api.ServerEndpoint
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.StreamSignal
import dev.flowpilot.core.api.events
import dev.flowpilot.core.sync.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LinkState { Offline, Connecting, CatchingUp, Online, Reconnecting, Unauthorized, Unsupported }

/**
 * One paired computer: its API client, the single shared event stream, and folders we look up once.
 * The stream runs for as long as the connection lives; screens subscribe to [events] and refetch on [reconnects].
 */
class ServerConnection(val server: SavedServer, secret: String, parent: CoroutineScope, private val cache: Cache) {
    val identity = java.util.UUID.randomUUID().toString()
    val client = OpenCodeClient(ServerEndpoint(server.baseUrl, secret))
    val ops = ProjectOps(client)
    val catalog = Catalog(client)
    val serverVersion = MutableStateFlow(server.version)
    private val sessions = ConcurrentHashMap<String, SessionRepository>()
    @Volatile private var foreground = true
    @Volatile private var network = true
    @Volatile private var streamOnline = false

    // Supervised, so one failed background refresh never takes the event stream down with it.
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val _state = MutableStateFlow(LinkState.Connecting)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    /** Ticks every time the stream reopens, so screens can refetch what they missed. */
    private val _reconnects = MutableStateFlow(0)
    val reconnects: StateFlow<Int> = _reconnects.asStateFlow()

    /** Approvals and questions waiting anywhere on this computer. */
    val pending = Pending(client, events, reconnects, scope, catalog::projects, cache, server.id)

    private val folderLock = Mutex()
    private var homePath: String? = null
    private var scratch: String? = null

    private var stream: Job? = null

    val home = HomeRepository(server.id, client, catalog, cache, scope)
    private var lastStart = 0L
    @Volatile private var connectionEpoch = 0L

    init {
        scope.launch { pending.asks.collect { home.pending(pending.bySession()) } }
        startStream()
    }

    /**
     * Reopens the stream now instead of waiting out the backoff, for example after network access was granted.
     * [force] also replaces a stream that looks online, for when the network changed under it and it may be dead.
     */
    @Synchronized
    fun retryNow(force: Boolean = false) {
        if (streamOnline && !force) return
        // Several triggers often fire together (network back and app foregrounded); one restart is enough.
        if (!force && stream?.isActive == true && System.nanoTime() / 1_000_000 - lastStart < 500) return
        if (foreground && network && _state.value != LinkState.Unauthorized) startStream()
    }

    @Synchronized
    private fun startStream() {
        val epoch = ++connectionEpoch
        lastStart = System.nanoTime() / 1_000_000
        streamOnline = false
        _state.value = LinkState.Connecting
        sessions.values.forEach { it.invalidate() }
        stream?.cancel()
        stream = scope.launch {
            client.events().collect { signal ->
                when (signal) {
                    StreamSignal.Connecting -> {
                        streamOnline = false
                        _state.value = LinkState.Connecting
                        sessions.values.forEach { it.setEnabled(false) }
                        home.setEnabled(false)
                    }
                    StreamSignal.Connected -> {
                        streamOnline = true
                        catalog.invalidate()
                        home.setEnabled(true)
                        sessions.values.forEach { it.setEnabled(true) }
                        updateState()
                        _reconnects.value += 1
                        scope.launch {
                            catching { client.info() }.onSuccess { info ->
                                if (epoch != connectionEpoch) return@onSuccess
                                serverVersion.value = info.version
                                if (info.version.substringBefore('.').toIntOrNull() != 2) {
                                    suspendStream()
                                    _state.value = LinkState.Unsupported
                                }
                            }
                        }
                    }
                    is StreamSignal.Event -> {
                        catalog.event(signal.event.type)
                        home.accept(signal.event)
                        sessions.values.forEach { it.accept(signal.event) }
                        if (!_events.tryEmit(signal.event)) startStream()
                    }
                    is StreamSignal.Disconnected -> {
                        streamOnline = false
                        sessions.values.forEach { it.setEnabled(false) }
                        home.setEnabled(false)
                        _state.value = if (signal.error is ApiException.Unauthorized) LinkState.Unauthorized else LinkState.Reconnecting
                    }
                }
            }
        }
    }

    fun session(id: String): SessionRepository = sessions.computeIfAbsent(id) {
        val source = object : SessionSource by ApiSessionSource(client) {
            override suspend fun running(id: String) = id in catalog.active()
        }
        SessionRepository(server.id, id, source, cache, scope).also { repo ->
            repo.setEnabled(streamOnline)
            scope.launch { repo.state.collect { updateState() } }
        }
    }

    private fun updateState() {
        if (streamOnline) _state.value = if (sessions.values.any { it.state.value.syncing }) LinkState.CatchingUp else LinkState.Online
    }

    @Synchronized fun setForeground(value: Boolean) {
        foreground = value
        if (!value) suspendStream() else retryNow()
    }

    @Synchronized fun setNetwork(value: Boolean) {
        network = value
        if (!value) suspendStream() else retryNow(force = true)
    }

    @Synchronized private fun suspendStream() {
        connectionEpoch++
        stream?.cancel()
        streamOnline = false
        sessions.values.forEach { it.setEnabled(false) }
        home.setEnabled(false)
        if (_state.value != LinkState.Unauthorized) _state.value = LinkState.Offline
    }

    fun clone(url: String, parent: String) = kotlinx.coroutines.flow.flow {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$parent\u0000$url".toByteArray()).joinToString("") { "%02x".format(it) }
        val raw = cache.readValue(server.id, "clone", key)
        val job = raw?.takeIf { it.isNotEmpty() }?.let {
            dev.flowpilot.core.api.OpenCodeJson.decodeFromString(ProjectOps.CloneJob.serializer(), it)
        }
        ops.clone(url, parent, resume = job, onStarted = {
            cache.writeValue(server.id, "clone", key, dev.flowpilot.core.api.OpenCodeJson.encodeToString(ProjectOps.CloneJob.serializer(), it))
        }).collect { progress ->
            if (progress is ProjectOps.CloneProgress.Done || progress is ProjectOps.CloneProgress.Failed) cache.writeValue(server.id, "clone", key, "")
            emit(progress)
        }
    }

    suspend fun homeDir(): String = folderLock.withLock { homePath ?: ops.homeDir().also { homePath = it } }

    /** The "No project" folder, created the first time it is needed. */
    suspend fun scratchDir(): String {
        val h = homeDir()
        return folderLock.withLock { scratch ?: ops.scratchDir(h).also { scratch = it } }
    }

    /** Known without a network call once looked up, so rows can label "No project" chats. */
    val knownScratch: String? get() = scratch ?: homePath?.let { "${it.trimEnd('/')}/FlowPilot/Scratch" }

    fun close() { scope.cancel(); client.http.dispatcher.cancelAll() }
}
