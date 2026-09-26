package dev.flowpilot.app.data

import dev.flowpilot.core.api.ApiException
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.ProjectOps
import dev.flowpilot.core.api.ServerEndpoint
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.StreamSignal
import dev.flowpilot.core.api.events
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

enum class LinkState { Connecting, Online, Reconnecting, Unauthorized }

/**
 * One paired computer: its API client, the single shared event stream, and folders we look up once.
 * The stream runs for as long as the connection lives; screens subscribe to [events] and refetch on [reconnects].
 */
class ServerConnection(val server: SavedServer, secret: String, parent: CoroutineScope) {
    val client = OpenCodeClient(ServerEndpoint(server.baseUrl, secret))
    val ops = ProjectOps(client)

    private val scope = CoroutineScope(parent.coroutineContext + Job(parent.coroutineContext[Job]))
    private val _state = MutableStateFlow(LinkState.Connecting)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    /** Ticks every time the stream reopens, so screens can refetch what they missed. */
    private val _reconnects = MutableStateFlow(0)
    val reconnects: StateFlow<Int> = _reconnects.asStateFlow()

    /** Approvals and questions waiting anywhere on this computer. */
    val pending = Pending(client, events, reconnects, scope)

    private val folderLock = Mutex()
    private var home: String? = null
    private var scratch: String? = null

    init {
        scope.launch {
            client.events().collect { signal ->
                when (signal) {
                    StreamSignal.Connected -> {
                        _state.value = LinkState.Online
                        _reconnects.value += 1
                    }
                    is StreamSignal.Event -> _events.emit(signal.event)
                    is StreamSignal.Disconnected -> _state.value =
                        if (signal.error is ApiException.Unauthorized) LinkState.Unauthorized else LinkState.Reconnecting
                }
            }
        }
    }

    suspend fun homeDir(): String = folderLock.withLock { home ?: ops.homeDir().also { home = it } }

    /** The "No project" folder, created the first time it is needed. */
    suspend fun scratchDir(): String {
        val h = homeDir()
        return folderLock.withLock { scratch ?: ops.scratchDir(h).also { scratch = it } }
    }

    /** Known without a network call once looked up, so rows can label "No project" chats. */
    val knownScratch: String? get() = scratch ?: home?.let { "${it.trimEnd('/')}/FlowPilot/Scratch" }

    fun close() = scope.cancel()
}
