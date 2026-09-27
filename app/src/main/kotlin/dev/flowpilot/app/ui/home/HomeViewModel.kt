package dev.flowpilot.app.ui.home

import dev.flowpilot.core.sync.catching
import kotlinx.coroutines.flow.collectLatest
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
    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch { conn.home.state.collect { state -> _ui.update { it.copy(state = state) } } }
        viewModelScope.launch { conn.home.loading.collect { v -> _ui.update { it.copy(firstLoad = v) } } }
        viewModelScope.launch { conn.home.refreshing.collect { v -> _ui.update { it.copy(refreshing = v) } } }
        viewModelScope.launch { conn.home.error.collect { v -> _ui.update { it.copy(error = v) } } }
        viewModelScope.launch {
            queries.debounce(300).distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) return@collectLatest
                catching { conn.client.sessions(limit = 30, search = q).data }
                    .onSuccess { hits -> if (queries.value == q) _ui.update { it.copy(searchHits = hits) } }
                    .onFailure { e -> if (queries.value == q) _ui.update { it.copy(message = e.friendly()) } }
            }
        }
        viewModelScope.launch { catching { conn.homeDir() } }
    }

    fun refresh(silent: Boolean = false) {
        if (!silent) conn.retryNow()
        conn.home.requestRefresh()
    }
    fun loadMore() {
        if (_ui.value.loadingMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try { conn.home.loadMore() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it.copy(message = e.friendly()) } }
            finally { _ui.update { it.copy(loadingMore = false) } }
        }
    }
    fun setFilter(f: HomeFilter) = _ui.update { it.copy(filter = f) }
    fun setQuery(q: String) { _ui.update { it.copy(query = q, searchHits = emptyList()) }; queries.value = q.trim() }
    fun togglePin(id: String) = viewModelScope.launch { graph.prefs.togglePin(conn.server.id, id) }
    fun rename(id: String, title: String) = viewModelScope.launch {
        catching { conn.client.rename(id, title); conn.home.requestRefresh() }.onFailure { e -> _ui.update { it.copy(message = e.friendly()) } }
    }
    fun delete(id: String) = viewModelScope.launch {
        catching { conn.client.deleteSession(id); conn.home.requestRefresh() }.onFailure { e -> _ui.update { it.copy(message = e.friendly()) } }
    }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
}
