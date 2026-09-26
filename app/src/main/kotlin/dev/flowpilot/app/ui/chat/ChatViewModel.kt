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
    val message: String? = null,
    /** Bumped when the server confirms a send, so the feed scrolls to the new turn. */
    val sentTick: Int = 0,
) {
    val isNew: Boolean get() = session == null
    val title: String get() = chat.title?.takeIf { it.isNotBlank() } ?: session?.title?.takeIf { it.isNotBlank() } ?: "New chat"
    val currentModel: Model? get() = model?.let { ref -> models.firstOrNull { it.id == ref.id && it.providerID == ref.providerID } }
    val visibleModels: List<Model>
        get() = models.filter { visibility.isVisible(it.key, defaultVisible(it)) || (model != null && it.id == model.id && it.providerID == model.providerID) }

    companion object {
        fun defaultVisible(m: Model) = m.enabled && m.status != "deprecated"
    }
}

/**
 * One chat. [sessionID] is null for a chat that hasn't been sent yet: the session is created on first send,
 * so abandoned drafts never litter the server.
 */
@OptIn(FlowPreview::class)
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
    private var eventsJob: Job? = null

    private val sid: String? get() = _ui.value.chat.sessionID.ifEmpty { null }

    init {
        viewModelScope.launch {
            draft.value = graph.prefs.draft(sessionID ?: "new:$directory")
            draft.drop(1).debounce(300).collect { graph.prefs.saveDraft(sid ?: "new:${_ui.value.directory}", it) }
        }
        viewModelScope.launch { graph.prefs.visibility(server).collect { v -> _ui.update { it.copy(visibility = v) } } }
        viewModelScope.launch { graph.prefs.recentModels(server).collect { r -> _ui.update { it.copy(recent = r) } } }
        if (sessionID != null) {
            viewModelScope.launch { graph.prefs.setLastChat(server, sessionID) }
            viewModelScope.launch {
                graph.cache.readMessages(server, sessionID)?.let { cached ->
                    _ui.update { it.copy(chat = it.chat.copy(entries = ChatReducer.entriesFrom(cached)), loading = false) }
                }
                load()
            }
            follow(sessionID)
        } else {
            viewModelScope.launch { loadCatalog(directory) }
            viewModelScope.launch { runCatching { conn.client.projects() }.getOrNull()?.firstOrNull { it.canonical == directory }?.let { p -> _ui.update { it.copy(projectName = p.displayName) } } }
        }
        viewModelScope.launch { conn.reconnects.drop(1).collect { if (sid != null) load(silent = true) } }
    }

    private fun follow(id: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            conn.events.collect { e ->
                _ui.update { u -> u.copy(chat = ChatReducer.reduce(u.chat, e)) }
                if (e.sessionID == id && (e.type == "session.execution.succeeded" || e.type == "session.execution.failed" || e.type == "session.execution.interrupted")) {
                    markViewed()
                    saveCache()
                }
            }
        }
    }

    fun load(silent: Boolean = false) {
        val id = sid ?: return
        viewModelScope.launch {
            if (!silent) _ui.update { it.copy(loadError = null) }
            try {
                val session = conn.client.session(id)
                val (messages, cursor) = conn.client.messages(id, limit = 40)
                val perms = runCatching { conn.client.permissions(id) }.getOrDefault(emptyList())
                val forms = runCatching { conn.client.forms(id) }.getOrDefault(emptyList())
                val inbox = runCatching { conn.client.inbox(id) }.getOrDefault(emptyList())
                val running = runCatching { id in conn.client.activeSessions() }.getOrDefault(false)
                _ui.update { u ->
                    var chat = ChatReducer.mergeLatest(u.chat, messages, cursor.next)
                    chat = ChatReducer.withPending(chat, perms, forms, inbox)
                    chat = chat.copy(
                        title = session.title,
                        running = running,
                        runStartedAt = if (running) chat.runStartedAt ?: session.time.updated else null,
                        agent = chat.agent ?: session.agent,
                        model = chat.model ?: session.model,
                    )
                    u.copy(chat = chat, session = session, directory = session.location.directory, loading = false, loadError = null)
                }
                graph.cache.writeMessages(server, id, messages)
                loadCatalog(session.location.directory)
                if (!running) markViewed()
                runCatching { conn.client.projects() }.getOrNull()?.firstOrNull { it.id == session.projectID }?.let { p ->
                    _ui.update { it.copy(projectName = p.displayName) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, loadError = if (it.chat.entries.isEmpty()) e.friendly() else null, message = if (silent || it.chat.entries.isEmpty()) null else e.friendly()) }
            }
        }
    }

    private suspend fun loadCatalog(directory: String?) {
        val agents = runCatching { conn.client.agents(directory) }.getOrDefault(emptyList()).filter { it.selectable }
        val models = runCatching { conn.client.models(directory) }.getOrDefault(emptyList())
        val u = _ui.value
        val lastAgent = graph.prefs.lastAgent(server).first()
        val agent = u.chat.agent ?: u.session?.agent ?: lastAgent?.takeIf { a -> agents.any { it.id == a } } ?: agents.firstOrNull()?.id
        val model = u.chat.model ?: u.session?.model ?: graph.prefs.lastModel(server).first()
            ?: runCatching { conn.client.defaultModel(directory) }.getOrNull()?.ref
        _ui.update { it.copy(agents = agents, models = models, agent = it.agent ?: agent, model = it.model ?: model) }
    }

    fun loadOlder() {
        val id = sid ?: return
        val cursor = _ui.value.chat.olderCursor ?: return
        if (_ui.value.loadingOlder) return
        _ui.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try {
                val (older, next) = conn.client.messages(id, cursor = cursor, limit = 40)
                _ui.update { it.copy(chat = ChatReducer.prependOlder(it.chat, older, next.next), loadingOlder = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(loadingOlder = false, message = e.friendly()) }
            }
        }
    }

    /** Sends [text]. While the agent works, [queue] waits for the turn to finish instead of steering it. */
    fun send(text: String, queue: Boolean) {
        val body = text.trim()
        if (body.isEmpty() || _ui.value.creating) return
        val running = _ui.value.chat.running
        val localId = "local-" + UUID.randomUUID()
        val showBubble = !(running && queue)
        if (showBubble) _ui.update { it.copy(chat = ChatReducer.optimisticUser(it.chat, localId, body, System.currentTimeMillis())) }
        draft.value = ""
        viewModelScope.launch {
            try {
                val id = sid ?: createSession()
                conn.client.prompt(id, PromptBody(text = body, delivery = if (running && queue) Delivery.Queue.wire else Delivery.Steer.wire))
                _ui.update { it.copy(sentTick = it.sentTick + 1, chat = if (!running) it.chat.copy(running = true, runStartedAt = it.chat.runStartedAt ?: System.currentTimeMillis()) else it.chat) }
            } catch (e: Exception) {
                _ui.update { it.copy(chat = if (showBubble) ChatReducer.failOptimistic(it.chat, localId) else it.chat, creating = false, message = "Didn't send. ${e.friendly()}") }
                if (draft.value.isEmpty()) draft.value = body
            }
        }
    }

    private suspend fun createSession(): String {
        _ui.update { it.copy(creating = true) }
        val u = _ui.value
        val dir = u.directory ?: conn.scratchDir()
        val session = conn.client.createSession(CreateSessionBody(location = Location(dir), agent = u.agent, model = u.model))
        graph.prefs.saveDraft("new:${u.directory}", "")
        graph.prefs.setLastChat(server, session.id)
        _ui.update {
            it.copy(
                session = session, directory = session.location.directory, creating = false, loading = false,
                chat = it.chat.copy(sessionID = session.id, title = session.title),
            )
        }
        follow(session.id)
        return session.id
    }

    fun retry(entry: ChatEntry.User) {
        _ui.update { u -> u.copy(chat = u.chat.copy(entries = u.chat.entries.filterNot { it.id == entry.id })) }
        send(entry.text, queue = false)
    }

    /** Re-sends the last thing the user said, after a failed turn. */
    fun retryLastTurn() {
        val last = _ui.value.chat.entries.lastOrNull { it is ChatEntry.User } as? ChatEntry.User ?: return
        send(last.text, queue = false)
    }

    fun stop() {
        val id = sid ?: return
        viewModelScope.launch {
            runCatching { conn.client.interrupt(id) }.onFailure { e -> _ui.update { it.copy(message = "Couldn't stop. ${e.friendly()}") } }
        }
    }

    fun cancelQueued(inboxID: String) {
        val id = sid ?: return
        val before = _ui.value.chat.queued
        _ui.update { it.copy(chat = it.chat.copy(queued = it.chat.queued.filterNot { q -> q.id == inboxID })) }
        viewModelScope.launch {
            runCatching { conn.client.cancelInbox(id, inboxID) }.onFailure { e ->
                _ui.update { it.copy(chat = it.chat.copy(queued = before), message = e.friendly()) }
            }
        }
    }

    /** Pulls a queued message back into the composer to edit it. */
    fun editQueued(inboxID: String) {
        val q = _ui.value.chat.queued.firstOrNull { it.id == inboxID } ?: return
        draft.value = q.text
        cancelQueued(inboxID)
    }

    fun selectAgent(agent: String) {
        _ui.update { it.copy(agent = agent) }
        viewModelScope.launch {
            graph.prefs.useAgent(server, agent)
            sid?.let { id -> runCatching { conn.client.setAgent(id, agent) }.onFailure { e -> _ui.update { it.copy(message = e.friendly()) } } }
        }
    }

    fun selectModel(ref: ModelRef) {
        _ui.update { it.copy(model = ref) }
        viewModelScope.launch {
            graph.prefs.useModel(server, ref)
            sid?.let { id -> runCatching { conn.client.setModel(id, ref) }.onFailure { e -> _ui.update { it.copy(message = e.friendly()) } } }
        }
    }

    fun reply(p: PermissionRequest, decision: Decision) {
        _ui.update { it.copy(chat = it.chat.copy(permissions = it.chat.permissions.filterNot { x -> x.id == p.id })) }
        viewModelScope.launch {
            runCatching { conn.pending.reply(p, decision) }.onFailure { e ->
                _ui.update { it.copy(chat = it.chat.copy(permissions = it.chat.permissions + p), message = "Couldn't answer. ${e.friendly()}") }
            }
        }
    }

    fun answer(f: Form, answer: JsonObject) {
        _ui.update { it.copy(chat = it.chat.copy(forms = it.chat.forms.filterNot { x -> x.id == f.id })) }
        viewModelScope.launch {
            runCatching { conn.pending.answer(f, answer) }.onFailure { e ->
                _ui.update { it.copy(chat = it.chat.copy(forms = it.chat.forms + f), message = "Couldn't answer. ${e.friendly()}") }
            }
        }
    }

    fun dismiss(f: Form) {
        _ui.update { it.copy(chat = it.chat.copy(forms = it.chat.forms.filterNot { x -> x.id == f.id })) }
        viewModelScope.launch { runCatching { conn.pending.dismiss(f) } }
    }

    fun rename(title: String) {
        val id = sid ?: return
        val old = _ui.value.chat.title
        _ui.update { it.copy(chat = it.chat.copy(title = title)) }
        viewModelScope.launch {
            runCatching { conn.client.rename(id, title) }.onFailure { e -> _ui.update { it.copy(chat = it.chat.copy(title = old), message = e.friendly()) } }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun markViewed() {
        val id = sid ?: return
        viewModelScope.launch { runCatching { conn.client.markViewed(id, System.currentTimeMillis()) } }
    }

    private fun saveCache() {
        val id = sid ?: return
        viewModelScope.launch { runCatching { graph.cache.writeMessages(server, id, conn.client.messages(id, limit = 40).first) } }
    }
}
