package dev.flowpilot.app.data

import dev.flowpilot.core.api.Decision
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.OpenCodeJson
import dev.flowpilot.core.api.PermissionRequest
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Something an agent is waiting on, anywhere on the computer. */
sealed interface Ask {
    val id: String
    val sessionID: String
    data class Permission(val request: PermissionRequest) : Ask {
        override val id get() = request.id
        override val sessionID get() = request.sessionID
    }
    data class Question(val form: Form) : Ask {
        override val id get() = form.id
        override val sessionID get() = form.sessionID
    }
}

/**
 * Every pending approval and question across projects. Loaded per project folder (the list routes are
 * location-scoped), then kept live from the event stream.
 */
class Pending(
    private val client: OpenCodeClient,
    events: Flow<ServerEvent>,
    reconnects: Flow<Int>,
    private val scope: CoroutineScope,
) {
    private val _asks = MutableStateFlow<List<Ask>>(emptyList())
    val asks: StateFlow<List<Ask>> = _asks.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, Session>>(emptyMap())
    /** Titles and folders for the chats that are asking, for Inbox cards. */
    val sessions: StateFlow<Map<String, Session>> = _sessions.asStateFlow()

    init {
        scope.launch { reconnects.collect { if (it > 0) runCatching { refresh() } } }
        scope.launch { events.collect { reduce(it) } }
    }

    suspend fun refresh() {
        val dirs = runCatching { client.projects().map { it.canonical } }.getOrDefault(emptyList())
        val lists = kotlinx.coroutines.coroutineScope {
            dirs.map { dir ->
                async {
                    val p = runCatching { client.pendingPermissions(dir) }.getOrDefault(emptyList()).map { Ask.Permission(it) }
                    val f = runCatching { client.pendingForms(dir) }.getOrDefault(emptyList()).map { Ask.Question(it) }
                    p + f
                }
            }.awaitAll()
        }
        _asks.value = lists.flatten().distinctBy { it.id }
        lists.flatten().map { it.sessionID }.distinct().forEach { ensureSession(it) }
    }

    fun bySession(): Map<String, Set<String>> = _asks.value.groupBy { it.sessionID }.mapValues { e -> e.value.map { it.id }.toSet() }

    suspend fun reply(permission: PermissionRequest, decision: Decision) {
        client.replyPermission(permission.sessionID, permission.id, decision)
        remove(permission.id)
    }

    suspend fun answer(form: Form, answer: JsonObject) {
        client.replyForm(form.sessionID, form.id, answer)
        remove(form.id)
    }

    suspend fun dismiss(form: Form) {
        client.cancelForm(form.sessionID, form.id)
        remove(form.id)
    }

    private fun remove(id: String) = _asks.update { list -> list.filterNot { it.id == id } }

    private fun ensureSession(id: String) {
        if (id in _sessions.value) return
        scope.launch { runCatching { client.session(id) }.getOrNull()?.let { s -> _sessions.update { it + (id to s) } } }
    }

    private fun reduce(e: ServerEvent) {
        val d = e.data
        when (e.type) {
            "permission.asked" -> runCatching { OpenCodeJson.decodeFromJsonElement(PermissionRequest.serializer(), d) }.getOrNull()?.let { p ->
                _asks.update { list -> list.filterNot { it.id == p.id } + Ask.Permission(p) }
                ensureSession(p.sessionID)
            }
            "permission.replied" -> d.s("requestID")?.let(::remove)
            "form.created" -> (d["form"] as? JsonObject)?.let { runCatching { OpenCodeJson.decodeFromJsonElement(Form.serializer(), it) }.getOrNull() }?.let { f ->
                _asks.update { list -> list.filterNot { it.id == f.id } + Ask.Question(f) }
                ensureSession(f.sessionID)
            }
            "form.replied", "form.cancelled" -> d.s("id")?.let(::remove)
            "session.deleted" -> e.sessionID?.let { sid -> _asks.update { list -> list.filterNot { it.sessionID == sid } } }
            "session.renamed" -> e.sessionID?.let { sid -> _sessions.update { m -> m[sid]?.let { m + (sid to it.copy(title = d.s("title"))) } ?: m } }
        }
    }
}

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
