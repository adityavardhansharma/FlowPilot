package dev.flowpilot.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.flowpilot.app.data.AppGraph
import dev.flowpilot.app.data.ModelVisibility
import dev.flowpilot.app.data.ServerConnection
import dev.flowpilot.app.ui.friendly
import dev.flowpilot.core.api.Agent
import dev.flowpilot.core.api.CreateSessionBody
import dev.flowpilot.core.api.Decision
import dev.flowpilot.core.api.Delivery
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.Location
import dev.flowpilot.core.api.Model
import dev.flowpilot.core.api.ModelRef
import dev.flowpilot.core.api.PermissionRequest
import dev.flowpilot.core.api.PromptBody
import dev.flowpilot.core.api.Session
import dev.flowpilot.core.chat.ChatEntry
import dev.flowpilot.core.chat.ChatReducer
import dev.flowpilot.core.chat.ChatState
import dev.flowpilot.core.sync.*
import dev.flowpilot.core.chat.QueuedMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID

data class ChatUi(
    val chat: ChatState,
    val session: Session? = null,
    val directory: String? = null,
    val projectName: String = "",
    val loading: Boolean = true,
    val loadError: String? = null,
    val loadingOlder: Boolean = false,
    val agents: List<Agent> = emptyList(),
    val agent: String? = null,
    val models: List<Model> = emptyList(),
    val model: ModelRef? = null,
    val visibility: ModelVisibility = ModelVisibility(),
    val recent: List<ModelRef> = emptyList(),
    val creating: Boolean = false,
    val syncing: Boolean = false,
    val message: String? = null,
    /** Bumped when the server confirms a send, so the feed scrolls to the new turn. */
    val sentTick: Int = 0,
) {
    val isNew: Boolean get() = chat.sessionID.isEmpty()
    val title: String get() = chat.title?.takeIf { it.isNotBlank() } ?: session?.title?.takeIf { it.isNotBlank() } ?: "New chat"
    val currentModel: Model? get() = model?.let { ref -> models.firstOrNull { it.id == ref.id && it.providerID == ref.providerID } }
    val visibleModels: List<Model>
        get() = models.filter { visibility.isVisible(it.key, defaultVisible(it)) || (model != null && it.id == model.id && it.providerID == model.providerID) }

    companion object {
        fun defaultVisible(m: Model) = m.enabled && m.status != "deprecated"
    }
}

/** Presentation and commands. The connection-owned repository is the only chat state writer. */
class ChatViewModel(
    private val graph: AppGraph,
    val conn: ServerConnection,
    sessionID: String?,
    directory: String?,
) : ViewModel() {
    private val server = conn.server.id
    private val _ui = MutableStateFlow(ChatUi(ChatState(sessionID ?: ""), directory = directory, loading = sessionID != null))
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()
    val draft = MutableStateFlow("")
    private var repository: SessionRepository? = null
    private var observeJob: Job? = null
    private val sendLock = Mutex()
    private val modelLock = Mutex()
    private val agentLock = Mutex()
    private val queueEdits = mutableSetOf<String>()
    private val sid get() = repository?.id
    private var draftKey = sessionID ?: "new:$directory"
    private var draftLoaded = false
    private var draftEdited = false

    fun editDraft(text: String) { draftEdited = true; draft.value = text }

    init {
        viewModelScope.launch {
            val saved = catching { graph.prefs.draft(server, draftKey) }.getOrDefault("")
            if (!draftEdited) draft.value = saved
            draftLoaded = true
            draft.collect { graph.prefs.saveDraft(server, draftKey, it) }
        }
        viewModelScope.launch { graph.prefs.visibility(server).collect { v -> _ui.update { it.copy(visibility = v) } } }
        viewModelScope.launch { graph.prefs.recentModels(server).collect { r -> _ui.update { it.copy(recent = r) } } }
        if (sessionID != null) {
            follow(sessionID)
            viewModelScope.launch { graph.prefs.setLastChat(server, sessionID) }
        } else viewModelScope.launch { loadCatalog(directory) }
        viewModelScope.launch {
            conn.reconnects.drop(1).collect { if (sid == null) loadCatalog(_ui.value.directory) }
        }
    }

    override fun onCleared() {
        if (draftLoaded || draftEdited) {
            val key = draftKey
            val text = draft.value
            graph.scope.launch { catching { graph.prefs.saveDraft(server, key, text) } }
        }
    }

    private fun follow(id: String) {
        observeJob?.cancel()
        val repo = conn.session(id)
        repository = repo
        observeJob = viewModelScope.launch {
            launch {
                repo.state.collect { snapshot ->
                    val session = snapshot.saved.session
                    _ui.update { old -> old.copy(
                        chat = snapshot.saved.chat, session = session ?: old.session,
                        directory = session?.location?.directory ?: old.directory,
                        loading = snapshot.loading, loadingOlder = snapshot.loadingOlder, syncing = snapshot.syncing,
                        loadError = snapshot.error,
                        model = snapshot.saved.chat.model ?: old.model,
                        agent = snapshot.saved.chat.agent ?: old.agent,
                    ) }
                }
            }
            launch {
                repo.state.map { it.saved.session?.location?.directory }.distinctUntilChanged().collect { dir ->
                    if (dir != null) loadCatalog(dir)
                }
            }
            launch {
                repo.state.map { it.saved.chat.running }.distinctUntilChanged().collect { running ->
                    if (!running && !repo.state.value.loading && !repo.state.value.syncing) markViewed()
                }
            }
        }
    }

    private fun change(f: (ChatState) -> ChatState) {
        val repo = repository
        if (repo != null) repo.update(f) else _ui.update { it.copy(chat = f(it.chat)) }
    }

    fun load(silent: Boolean = false) { repository?.invalidate() }

    private suspend fun loadCatalog(directory: String?) = coroutineScope {
        val agentsJob = async { catching { conn.catalog.agents(directory) } }
        val modelsJob = async { catching { conn.catalog.models(directory) } }
        val defaultJob = async { catching { conn.catalog.defaultModel(directory) } }
        val projectsJob = async { catching { conn.catalog.projects() } }
        val agents = agentsJob.await().getOrNull()?.filter { it.selectable }
        val models = modelsJob.await().getOrNull()
        val default = defaultJob.await().getOrNull()?.ref
        val projects = projectsJob.await().getOrNull()
        val lastAgent = graph.prefs.lastAgent(server).first()
        val lastModel = graph.prefs.lastModel(server).first()
        _ui.update { u -> u.copy(
            agents = agents ?: u.agents, models = models ?: u.models,
            agent = u.chat.agent ?: u.agent ?: lastAgent?.takeIf { a -> agents?.any { it.id == a } == true } ?: agents?.firstOrNull()?.id,
            model = u.chat.model ?: u.model ?: lastModel ?: default,
            projectName = projects?.firstOrNull { it.canonical == directory }?.displayName ?: u.projectName,
        ) }
    }

    fun loadOlder() {
        viewModelScope.launch { catching { repository?.loadOlder() }.onFailure { show(it) } }
    }

    fun send(text: String, queue: Boolean) {
        val body = text.trim()
        if (body.isEmpty() || !sendLock.tryLock()) return
        val running = _ui.value.chat.running
        val localId = "local-" + UUID.randomUUID()
        val bubble = !(running && queue)
        _ui.update { it.copy(creating = true) }
        if (bubble) change { ChatReducer.optimisticUser(it, localId, body, System.currentTimeMillis()) }
        draft.value = ""
        viewModelScope.launch {
            try {
                val id = sid ?: createSession()
                val item = conn.client.prompt(id, PromptBody(text = body, delivery = if (running && queue) Delivery.Queue.wire else Delivery.Steer.wire))
                change { chat ->
                    if (bubble) {
                        val exists = chat.entries.any { it.id == item.id }
                        chat.copy(entries = chat.entries.mapNotNull { entry ->
                            if (entry.id != localId) entry
                            else if (exists) null
                            else (entry as ChatEntry.User).copy(id = item.id, pending = false, failed = false)
                        })
                    } else if (chat.queued.none { it.id == item.id } && chat.entries.none { it.id == item.id }) {
                        chat.copy(queued = chat.queued + QueuedMessage(item.id, body, item.delivery))
                    } else chat
                }
                _ui.update { it.copy(sentTick = it.sentTick + 1) }
                repository?.invalidate()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (bubble) change { ChatReducer.failOptimistic(it, localId) }
                // A transport failure cannot tell whether the server accepted the mutation.
                val uncertain = e is dev.flowpilot.core.api.ApiException.Unreachable
                _ui.update { it.copy(message = if (uncertain) "Send wasn't confirmed. Check the refreshed chat before retrying." else "Couldn't send. ${e.friendly()}") }
                if (draft.value.isEmpty()) draft.value = body
                repository?.invalidate()
            } finally { _ui.update { it.copy(creating = false) }; sendLock.unlock() }
        }
    }

    private suspend fun createSession(): String {
        val before = _ui.value
        val dir = before.directory ?: conn.scratchDir()
        val session = conn.client.createSession(CreateSessionBody(location = Location(dir), agent = before.agent, model = before.model))
        val optimistic = _ui.value.chat.entries
        graph.prefs.saveDraft(server, draftKey, "")
        draftKey = session.id
        graph.prefs.saveDraft(server, draftKey, draft.value)
        graph.prefs.setLastChat(server, session.id)
        _ui.update { it.copy(session = session, directory = session.location.directory, loading = false) }
        follow(session.id)
        change { it.copy(entries = (it.entries + optimistic).distinctBy { e -> e.id }, title = session.title, agent = session.agent, model = session.model) }
        return session.id
    }

    fun retry(entry: ChatEntry.User) {
        change { it.copy(entries = it.entries.filterNot { e -> e.id == entry.id }) }
        send(entry.text, queue = false)
    }
    fun retryLastTurn() {
        val last = _ui.value.chat.entries.lastOrNull { it is ChatEntry.User } as? ChatEntry.User ?: return
        send(last.text, queue = false)
    }
    fun stop() { sid?.let { id -> viewModelScope.launch { catching { conn.client.interrupt(id) }.onFailure { show(it) } } } }

    private fun removeQueued(id: String, edit: Boolean) {
        val session = sid ?: return
        val item = _ui.value.chat.queued.firstOrNull { it.id == id } ?: return
        if (!queueEdits.add(id)) return
        viewModelScope.launch {
            try {
                conn.client.cancelInbox(session, id)
                change { it.copy(queued = it.queued.filterNot { q -> q.id == id }) }
                if (edit) draft.value = if (draft.value.isBlank()) item.text else draft.value + "\n" + item.text
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { show(e) }
            finally { queueEdits.remove(id) }
        }
    }
    fun cancelQueued(inboxID: String) = removeQueued(inboxID, false)
    fun editQueued(inboxID: String) = removeQueued(inboxID, true)

    fun selectAgent(agent: String) {
        viewModelScope.launch { agentLock.withLock {
            catching {
                sid?.let { conn.client.setAgent(it, agent) }
                change { it.copy(agent = agent) }
                _ui.update { it.copy(agent = agent) }
                graph.prefs.useAgent(server, agent)
            }.onFailure { show(it) }
        } }
    }
    fun selectModel(ref: ModelRef) {
        viewModelScope.launch { modelLock.withLock {
            catching {
                sid?.let { conn.client.setModel(it, ref) }
                change { it.copy(model = ref) }
                _ui.update { it.copy(model = ref) }
                graph.prefs.useModel(server, ref)
            }.onFailure { show(it) }
        } }
    }
    fun reply(p: PermissionRequest, decision: Decision) {
        viewModelScope.launch { catching {
            conn.pending.reply(p, decision)
            change { it.copy(permissions = it.permissions.filterNot { x -> x.id == p.id }) }
        }.onFailure { show(it) } }
    }
    fun answer(f: Form, answer: JsonObject) {
        viewModelScope.launch { catching {
            conn.pending.answer(f, answer)
            change { it.copy(forms = it.forms.filterNot { x -> x.id == f.id }) }
        }.onFailure { show(it) } }
    }
    fun dismiss(f: Form) {
        viewModelScope.launch { catching {
            conn.pending.dismiss(f)
            change { it.copy(forms = it.forms.filterNot { x -> x.id == f.id }) }
        }.onFailure { show(it) } }
    }
    fun rename(title: String) {
        val id = sid ?: return
        viewModelScope.launch { catching { conn.client.rename(id, title); change { it.copy(title = title) } }.onFailure { show(it) } }
    }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    private fun show(error: Throwable) = _ui.update { it.copy(message = error.friendly()) }
    private fun markViewed() {
        val id = sid ?: return
        val idle = repository?.state?.value?.saved?.session?.time?.idle ?: return
        viewModelScope.launch { catching { conn.client.markViewed(id, idle) } }
    }
}
