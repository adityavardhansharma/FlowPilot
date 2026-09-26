package dev.flowpilot.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
    val context: Context = context.applicationContext
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
            try {
                combine(prefs.servers, prefs.currentServerId) { list, id -> list.firstOrNull { it.id == id } ?: list.lastOrNull() }
                    .collect { server ->
                        val cur = _connection.value
                        if (cur?.server?.id != server?.id || cur?.server?.sealedSecret != server?.sealedSecret) {
                            cur?.close()
                            _connection.value = server?.let { s ->
                                runCatching { SecretBox.open(s.sealedSecret)?.let { ServerConnection(s, it, scope) } }
                                    .onFailure { e -> Log.w("FlowPilot", "couldn't open saved server", e) }
                                    .getOrNull()
                            }
                        }
                        _ready.value = true
                    }
            } finally {
                // Never leave the splash screen up forever, even if reading settings failed.
                _ready.value = true
            }
        }
        watchNetwork()
        watchForeground()
    }

    /** A new or restored network (Wi-Fi back, switched networks) means the old stream is dead; reopen it now. */
    private fun watchNetwork() {
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _connection.value?.retryNow(force = true) }
            })
        }.onFailure { Log.w("FlowPilot", "couldn't watch the network", it) }
    }

    /** Coming back to the app after a while: reconnect straight away instead of waiting out the backoff. */
    private fun watchForeground() {
        scope.launch(Dispatchers.Main) {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) { _connection.value?.retryNow(force = true) }
                })
            }.onFailure { Log.w("FlowPilot", "couldn't watch the app lifecycle", it) }
        }
    }
}
