package dev.flowpilot.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Manual dependency graph. One instance per process, owned by [dev.flowpilot.app.FlowPilotApp]. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("FlowPilot", "background work failed", e) })
    val prefs = Prefs(context)
    val cache = Cache(context)

    private val _connection = MutableStateFlow<ServerConnection?>(null)
    /** The computer FlowPilot is talking to, or null before pairing. */
    val connection: StateFlow<ServerConnection?> = _connection.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** False until the saved servers were read, so the app doesn't flash the pairing screen. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            combine(prefs.servers, prefs.currentServerId) { list, id -> list.firstOrNull { it.id == id } ?: list.lastOrNull() }
                .collect { server ->
                    val cur = _connection.value
                    if (cur?.server?.id != server?.id || cur?.server?.sealedSecret != server?.sealedSecret) {
                        cur?.close()
                        _connection.value = server?.let { s -> SecretBox.open(s.sealedSecret)?.let { ServerConnection(s, it, scope) } }
                    }
                    _ready.value = true
                }
        }
    }
}
