package dev.flowpilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flowpilot.app.data.AppGraph
import dev.flowpilot.app.data.HomeSnapshot
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.friendly
import dev.flowpilot.core.api.Session
import dev.flowpilot.core.home.HomeFilter
import dev.flowpilot.core.home.HomeState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUi(
    val state: HomeState = HomeState(),
    val firstLoad: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val filter: HomeFilter = HomeFilter.All,
    val query: String = "",
    val searchHits: List<Session> = emptyList(),
    val message: String? = null,
)

@OptIn(FlowPreview::class)
class HomeViewModel(private val graph: AppGraph, val conn: ServerConnection) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()
    val pinned = graph.prefs.pinned(conn.server.id)
    private val serverId = conn.server.id
    private var refreshJob: Job? = null
    private var retryJob: Job? = null
    private var failures = 0
    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            graph.cache.readHome(serverId)?.let { snap ->
                _ui.update {
                    it.copy(
                        state = it.state.withPage(snap.sessions, snap.cursor, replace = true).copy(projects = snap.projects.associateBy { p -> p.id }),
                        firstLoad = false,
                    )
                }
            }
            refresh()
        }
        viewModelScope.launch {
            conn.events.collect { e ->
                var stale = false
                // One odd event must never take the app down; skip it and let the next refresh correct things.
                _ui.update { u -> u.copy(state = runCatching { u.state.reduce(e) }.getOrDefault(u.state).also { stale = it.stale }) }
                if (stale) { delay(400); refresh(silent = true) }
            }
        }
        viewModelScope.launch { conn.reconnects.drop(1).collect { refresh(silent = true) } }
        viewModelScope.launch {
            conn.pending.asks.collect { _ui.update { u -> u.copy(state = u.state.copy(needsYou = conn.pending.bySession())) } }
        }
        viewModelScope.launch {
            queries.debounce(300).distinctUntilChanged().collect { q ->
                if (q.length < 2) { _ui.update { it.copy(searchHits = emptyList()) }; return@collect }
                val hits = runCatching { conn.client.sessions(limit = 30, search = q).data }.getOrDefault(emptyList())
                _ui.update { it.copy(searchHits = hits) }
            }
        }
        viewModelScope.launch { runCatching { conn.homeDir() } }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true) return
        if (!silent) conn.retryNow()
        refreshJob = viewModelScope.launch {
            if (!silent) _ui.update { it.copy(refreshing = !it.firstLoad) }
            try {
                val projects = conn.client.projects()
                val page = conn.client.sessions(limit = 60)
                val active = runCatching { conn.client.activeSessions() }.getOrDefault(emptySet())
                _ui.update { u ->
                    val keep = u.state.sessions.values.filter { s -> page.data.none { it.id == s.id } && page.data.isNotEmpty() && s.time.updated < page.data.last().time.updated }
                    val merged = u.state.withPage(page.data + keep, page.cursor.next, replace = true)
                    u.copy(
                        state = merged.copy(projects = projects.associateBy { it.id }, active = active, needsYou = conn.pending.bySession()),
                        firstLoad = false, refreshing = false, error = null,
                    )
                }
                failures = 0
                retryJob?.cancel()
                graph.cache.writeHome(serverId, HomeSnapshot(page.data, projects, page.cursor.next))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(firstLoad = false, refreshing = false, error = e.friendly()) }
                scheduleRetry()
            }
        }
    }

    /** Keeps trying in the background while offline: 2s, 4s, 8s… capped at 30s. A reconnect also refreshes. */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val wait = minOf(30_000L, 2_000L shl minOf(failures, 4))
        failures++
        retryJob = viewModelScope.launch { delay(wait); refresh(silent = true) }
    }

    fun loadMore() {
        val cursor = _ui.value.state.nextCursor ?: return
        if (_ui.value.loadingMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = conn.client.sessions(cursor = cursor, limit = 60)
                _ui.update { it.copy(state = it.state.withPage(page.data, page.cursor.next, replace = false), loadingMore = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(loadingMore = false, message = e.friendly()) }
            }
        }
    }

    fun setFilter(f: HomeFilter) = _ui.update { it.copy(filter = f) }
    fun setQuery(q: String) { _ui.update { it.copy(query = q) }; queries.value = q.trim() }

    fun togglePin(id: String) = viewModelScope.launch { graph.prefs.togglePin(serverId, id) }

    fun rename(id: String, title: String) = viewModelScope.launch {
        val old = _ui.value.state.sessions[id] ?: return@launch
        _ui.update { it.copy(state = it.state.copy(sessions = it.state.sessions + (id to old.copy(title = title)))) }
        try { conn.client.rename(id, title) } catch (e: Exception) {
            _ui.update { it.copy(state = it.state.copy(sessions = it.state.sessions + (id to old)), message = "Couldn't rename. ${e.friendly()}") }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        val old = _ui.value.state.sessions[id] ?: return@launch
        _ui.update { it.copy(state = it.state.copy(sessions = it.state.sessions - id)) }
        try { conn.client.deleteSession(id) } catch (e: Exception) {
            _ui.update { it.copy(state = it.state.copy(sessions = it.state.sessions + (id to old)), message = "Couldn't delete. ${e.friendly()}") }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
