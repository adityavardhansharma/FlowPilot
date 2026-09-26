package dev.flowpilot.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flowpilot.app.data.AppGraph
import dev.flowpilot.app.data.SavedServer
import dev.flowpilot.app.data.SecretBox
import dev.flowpilot.core.api.ApiException
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.PairingLink
import dev.flowpilot.core.api.ServerEndpoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
)

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
        val link = PairingLink.parse(raw)
        if (link == null) { _ui.update { it.copy(scanError = "That QR code isn't from opencode pair.") }; return }
        job = viewModelScope.launch {
            _ui.update { it.copy(step = PairStep.Connecting, from = PairStep.Scan) }
            try {
                val endpoint = OpenCodeClient.redeemPairingLink(raw)
                finish(endpoint)
            } catch (e: Exception) {
                _ui.update { it.copy(step = PairStep.Scan, scanError = e.message ?: "Couldn't pair. Try again.") }
            }
        }
    }

    fun connectManually() {
        val s = _ui.value
        val raw = s.address.trim().let { if (it.contains("://")) it else "http://$it" }
        val url = raw.toHttpUrlOrNull()
        if (s.address.isBlank() || url == null) { _ui.update { it.copy(addressError = "Enter an address like http://100.64.1.2:4096") }; return }
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(step = PairStep.Connecting, from = PairStep.Manual) }
            try {
                finish(ServerEndpoint(raw.trimEnd('/'), s.password))
            } catch (e: ApiException.Unauthorized) {
                _ui.update { it.copy(step = PairStep.Manual, passwordError = "Wrong password") }
            } catch (e: ApiException.Unreachable) {
                _ui.update { it.copy(step = PairStep.Manual, addressError = "Nothing answered at that address") }
            } catch (e: ApiException.Http) {
                _ui.update { it.copy(step = PairStep.Manual, addressError = if (e.code == 404) "That address isn't an OpenCode server" else "The server answered ${e.code}") }
            } catch (e: Exception) {
                _ui.update { it.copy(step = PairStep.Manual, addressError = e.message ?: "Couldn't connect") }
            }
        }
    }

    private suspend fun finish(endpoint: ServerEndpoint) {
        val info = OpenCodeClient(endpoint).info()
        val major = info.version.substringBefore('.').toIntOrNull()
        val host = endpoint.url.host
        if (major != null && major != 2) {
            _ui.update { it.copy(step = PairStep.OldVersion, version = info.version) }
            return
        }
        _ui.update { it.copy(step = PairStep.Success, connectedName = host, version = info.version) }
        val server = SavedServer(
            id = UUID.randomUUID().toString(),
            name = host,
            baseUrl = endpoint.baseUrl,
            sealedSecret = SecretBox.seal(endpoint.secret),
            version = info.version,
            addedAt = System.currentTimeMillis(),
        )
        delay(900)
        graph.prefs.saveServer(server)
        _ui.update { it.copy(done = true) }
    }
}
