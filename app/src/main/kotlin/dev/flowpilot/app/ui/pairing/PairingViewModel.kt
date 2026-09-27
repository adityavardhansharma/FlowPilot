package dev.flowpilot.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flowpilot.app.data.AppGraph
import dev.flowpilot.app.data.SavedServer
import dev.flowpilot.app.data.SecretBox
import dev.flowpilot.app.ui.LocalNetwork
import dev.flowpilot.core.api.ApiException
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.PairingLink
import dev.flowpilot.core.api.ServerAddress
import dev.flowpilot.core.api.ServerEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

enum class PairStep { Welcome, Scan, Manual, Connecting, Success, OldVersion }

data class PairUi(
    val step: PairStep = PairStep.Welcome,
    val address: String = "",
    val password: String = "",
    val addressError: String? = null,
    val passwordError: String? = null,
    val scanError: String? = null,
    val connectedName: String = "",
    val version: String = "",
    /** Where Connecting came from, so a failure returns there. */
    val from: PairStep = PairStep.Welcome,
    val done: Boolean = false,
) {
    /** A pasted `opencode pair` link carries its own token, so no password is needed. */
    val addressIsLink: Boolean get() = PairingLink.parse(address) != null
}

class PairingViewModel(private val graph: AppGraph) : ViewModel() {
    private val _ui = MutableStateFlow(PairUi())
    val ui: StateFlow<PairUi> = _ui.asStateFlow()
    private var job: Job? = null

    fun go(step: PairStep) = _ui.update { it.copy(step = step, scanError = null) }
    fun setAddress(v: String) = _ui.update { it.copy(address = v, addressError = null) }
    fun setPassword(v: String) = _ui.update { it.copy(password = v, passwordError = null) }

    /** A QR code was seen. Ignores anything that isn't a pairing link, so random codes don't interrupt. */
    fun onScanned(raw: String) {
        if (job?.isActive == true || _ui.value.step != PairStep.Scan) return
        if (PairingLink.parse(raw) == null) { _ui.update { it.copy(scanError = "That QR code isn't from opencode pair.") }; return }
        job = viewModelScope.launch {
            _ui.update { it.copy(step = PairStep.Connecting, from = PairStep.Scan, scanError = null) }
            try {
                finish(OpenCodeClient.redeemPairingLink(raw))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(step = PairStep.Scan, scanError = describe(e, PairingLink.parse(raw)?.baseUrl)) }
            }
        }
    }

    fun connectManually() {
        if (job?.isActive == true) return
        val s = _ui.value
        val input = s.address.trim()
        if (input.isEmpty()) { _ui.update { it.copy(addressError = "Paste the link from opencode pair, or an address like 192.168.1.20:49374") }; return }

        // A pasted pairing link works like a scanned one.
        PairingLink.parse(input)?.let { link ->
            job = viewModelScope.launch {
                _ui.update { it.copy(step = PairStep.Connecting, from = PairStep.Manual) }
                try {
                    finish(OpenCodeClient.redeemPairingLink(input))
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _ui.update { it.copy(step = PairStep.Manual, addressError = describe(e, link.baseUrl)) }
                }
            }
            return
        }

        val base = when (val r = ServerAddress.normalize(input)) {
            is ServerAddress.Result.Ok -> r.baseUrl
            ServerAddress.Result.Loopback -> {
                _ui.update { it.copy(addressError = "That address means \"this phone\". Use your computer's Wi-Fi address, like 192.168.1.20:49374 (ipconfig or ifconfig shows it).") }
                return
            }
            ServerAddress.Result.Invalid -> {
                _ui.update { it.copy(addressError = "Enter an address like 192.168.1.20:49374") }
                return
            }
        }
        if (s.password.isBlank()) {
            _ui.update { it.copy(passwordError = "Enter the server password, or paste the link from opencode pair above instead") }
            return
        }
        job = viewModelScope.launch {
            _ui.update { it.copy(step = PairStep.Connecting, from = PairStep.Manual) }
            try {
                finish(ServerEndpoint(base, s.password))
            } catch (e: ApiException.Unauthorized) {
                _ui.update { it.copy(step = PairStep.Manual, passwordError = "Wrong password. Easier: run opencode pair and paste its link above.") }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(step = PairStep.Manual, addressError = describe(e, base)) }
            }
        }
    }

    /** Plain words for why pairing failed, naming the most likely fix. */
    private fun describe(e: Exception, base: String?): String {
        val where = base?.substringAfter("://") ?: "that address"
        val cause = generateSequence<Throwable>(e) { it.cause }.toList()
        return when {
            e is ApiException.Http && e.code == 401 -> "This pairing link expired or was already used. Run opencode pair again."
            e is ApiException.Http && e.code == 404 -> "$where answered, but it isn't an OpenCode 2 server."
            e is ApiException.Http -> "The server answered ${e.code}."
            e !is ApiException.Unreachable -> e.message ?: "Couldn't connect. Try again."
            !LocalNetwork.granted(graph.context) ->
                "FlowPilot isn't allowed to reach devices on your network. Allow \"Nearby devices\" for FlowPilot in Android settings, then try again."
            cause.any { it is UnknownHostException } -> "Couldn't find $where. Use your computer's IP address instead of its name."
            cause.any { it is ConnectException } ->
                "Nothing is listening at $where. Run opencode service set hostname 0.0.0.0, then opencode service restart."
            cause.any { it is SocketTimeoutException } ->
                "No answer from $where. Check your phone and computer are on the same Wi-Fi, and that the computer's firewall allows port ${base?.toPortOrNull() ?: ServerAddress.DEFAULT_PORT}."
            else -> "Nothing answered at $where. Check that opencode service is running and your phone is on the same network."
        }
    }

    private fun String.toPortOrNull(): Int? = substringAfter("://").substringBefore('/').substringAfterLast(':', "").toIntOrNull()

    private suspend fun finish(endpoint: ServerEndpoint) {
        val info = OpenCodeClient(endpoint).info()
        val major = info.version.substringBefore('.').toIntOrNull()
        val host = endpoint.url.host
        if (major != null && major != 2) {
            _ui.update { it.copy(step = PairStep.OldVersion, version = info.version) }
            return
        }
        // Keystore work can take a moment on some phones; keep it off the main thread.
        val sealed = withContext(Dispatchers.Default) { SecretBox.seal(endpoint.secret) }
        _ui.update { it.copy(step = PairStep.Success, connectedName = host, version = info.version) }
        val server = SavedServer(
            id = UUID.randomUUID().toString(),
            name = host,
            baseUrl = endpoint.baseUrl,
            sealedSecret = sealed,
            version = info.version,
            addedAt = System.currentTimeMillis(),
        )
        delay(900)
        graph.prefs.saveServer(server)
        _ui.update { it.copy(done = true) }
    }
}
