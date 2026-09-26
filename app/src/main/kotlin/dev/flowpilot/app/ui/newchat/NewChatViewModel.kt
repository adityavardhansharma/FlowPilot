package dev.flowpilot.app.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.friendly
import dev.flowpilot.core.api.FsEntry
import dev.flowpilot.core.api.Project
import dev.flowpilot.core.api.ProjectOps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NewChatStep { Pick, Folder, Clone }

data class CloneUi(val running: Boolean = false, val line: String = "", val percent: Int? = null, val failed: String? = null)

data class NewChatUi(
    val step: NewChatStep = NewChatStep.Pick,
    val projects: List<Project> = emptyList(),
    val loading: Boolean = true,
    val home: String? = null,
    val parent: String? = null,
    val folderName: String = "",
    val gitInit: Boolean = true,
    val creating: Boolean = false,
    val error: String? = null,
    val repo: String = "",
    val clone: CloneUi = CloneUi(),
    val browsing: Boolean = false,
    val browsePath: String? = null,
    val browseEntries: List<FsEntry> = emptyList(),
    val browseLoading: Boolean = false,
)

/** The new-chat sheet asks one question: where should the agent work? */
class NewChatViewModel(val conn: ServerConnection) : ViewModel() {
    private val _ui = MutableStateFlow(NewChatUi())
    val ui: StateFlow<NewChatUi> = _ui.asStateFlow()
    private var cloneJob: Job? = null

    init {
        viewModelScope.launch {
            val projects = runCatching { conn.client.projects() }.getOrDefault(emptyList())
            val scratch = conn.knownScratch
            _ui.update { it.copy(projects = projects.filterNot { p -> scratch != null && p.canonical.startsWith(scratch) || p.canonical == "/" }, loading = false) }
        }
        viewModelScope.launch {
            runCatching { conn.homeDir() }.getOrNull()?.let { h -> _ui.update { it.copy(home = h, parent = it.parent ?: h) } }
        }
    }

    fun go(step: NewChatStep) = _ui.update { it.copy(step = step, error = null) }
    fun setName(v: String) = _ui.update { it.copy(folderName = v, error = null) }
    fun setGit(v: Boolean) = _ui.update { it.copy(gitInit = v) }
    fun setRepo(v: String) = _ui.update { it.copy(repo = v, error = null, clone = CloneUi()) }

    fun createFolder(onDone: (String) -> Unit) {
        val s = _ui.value
        val parent = s.parent ?: return
        if (s.folderName.isBlank()) { _ui.update { it.copy(error = "Pick a folder name.") }; return }
        _ui.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            try {
                val dir = conn.ops.createFolder(parent, s.folderName, s.gitInit)
                _ui.update { it.copy(creating = false) }
                onDone(dir)
            } catch (e: Exception) {
                _ui.update { it.copy(creating = false, error = e.friendly()) }
            }
        }
    }

    fun clone(onDone: (String) -> Unit) {
        val s = _ui.value
        val url = ProjectOps.normalizeRepoUrl(s.repo)
        val parent = s.parent
        if (url == null) { _ui.update { it.copy(error = "Paste a git URL, or owner/repo for GitHub.") }; return }
        if (parent == null) return
        cloneJob?.cancel()
        _ui.update { it.copy(clone = CloneUi(running = true, line = "Starting git clone…"), error = null) }
        cloneJob = viewModelScope.launch {
            try {
                conn.ops.clone(url, parent).collect { p ->
                    when (p) {
                        is ProjectOps.CloneProgress.Running -> _ui.update { it.copy(clone = it.clone.copy(line = p.line, percent = p.percent ?: it.clone.percent)) }
                        is ProjectOps.CloneProgress.Done -> { _ui.update { it.copy(clone = CloneUi()) }; onDone(p.directory) }
                        is ProjectOps.CloneProgress.Failed -> _ui.update { it.copy(clone = CloneUi(failed = p.output.ifBlank { "git exited with an error." })) }
                    }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(clone = CloneUi(failed = e.friendly())) }
            }
        }
    }

    fun openBrowser() {
        _ui.update { it.copy(browsing = true) }
        browse(_ui.value.parent ?: _ui.value.home ?: "/")
    }

    fun closeBrowser() = _ui.update { it.copy(browsing = false) }

    fun browse(path: String) {
        _ui.update { it.copy(browsePath = path, browseLoading = true) }
        viewModelScope.launch {
            val entries = runCatching { conn.client.listDir(path) }.getOrDefault(emptyList())
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
            _ui.update { it.copy(browseEntries = entries, browseLoading = false) }
        }
    }

    fun useBrowsed() = _ui.update { it.copy(parent = it.browsePath ?: it.parent, browsing = false) }
}
