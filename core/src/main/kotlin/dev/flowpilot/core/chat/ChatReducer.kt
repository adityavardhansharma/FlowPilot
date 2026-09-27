package dev.flowpilot.core.chat

import dev.flowpilot.core.sync.catching

import dev.flowpilot.core.api.ApiError
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.InboxItem
import dev.flowpilot.core.api.ModelRef
import dev.flowpilot.core.api.OpenCodeJson
import dev.flowpilot.core.api.PermissionRequest
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.Tokens
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure reducer from OpenCode v2 messages and events to [ChatState]. No Android, no I/O, fully unit-tested.
 * Events are applied idempotently: `*.ended` events carry full values, so a missed delta self-heals.
 */
object ChatReducer {

    // ------------------------------------------------------------------ REST

    /** Converts a page of messages (any order) into entries, oldest first. */
    fun entriesFrom(messages: List<JsonObject>): List<ChatEntry> =
        messages.mapNotNull(::entryFrom).sortedWith(compareBy({ it.created }, { it.id }))

    fun entryFrom(m: JsonObject): ChatEntry? {
        val id = m.str("id") ?: return null
        val created = m.obj("time")?.long("created") ?: 0L
        return when (m.str("type")) {
            "user" -> ChatEntry.User(
                id = id, created = created, text = m.str("text").orEmpty(),
                files = m.arr("files")?.mapNotNull { (it as? JsonObject)?.str("name") ?: (it as? JsonObject)?.str("mime") } ?: emptyList(),
            )
            "assistant" -> ChatEntry.Assistant(
                id = id, created = created,
                agent = m.str("agent"),
                model = m.obj("model")?.let(::modelRef),
                parts = m.arr("content")?.mapIndexedNotNull { i, c -> (c as? JsonObject)?.let { partFrom(i, it, m.str("finish") == null && m.obj("time")?.long("completed") == null) } } ?: emptyList(),
                finish = m.str("finish"),
                error = m.obj("error")?.let(::error),
                cost = m.double("cost"),
                tokens = m.obj("tokens")?.let(::tokens),
                completed = m.obj("time")?.long("completed") ?: if (m.str("finish") != null) created else null,
            )
            "agent-switched" -> ChatEntry.Marker(id, created, MarkerKind.AgentSwitched, "Switched to ${m.str("agent")?.replaceFirstChar(Char::uppercase)}")
            "model-switched" -> ChatEntry.Marker(id, created, MarkerKind.ModelSwitched, "Switched to ${m.obj("model")?.str("id")}")
            "location-switched" -> ChatEntry.Marker(id, created, MarkerKind.Moved, "Moved to ${m.obj("location")?.str("directory")?.substringAfterLast('/')}")
            "compaction" -> when (m.str("status")) {
                "completed" -> ChatEntry.Marker(id, created, MarkerKind.Compaction, "Context compacted")
                "failed" -> ChatEntry.Marker(id, created, MarkerKind.Failed, "Compaction failed")
                else -> ChatEntry.Marker(id, created, MarkerKind.Compaction, "Compacting context…")
            }
            "idle" -> if (m.str("outcome") == "interrupted") ChatEntry.Marker(id, created, MarkerKind.Stopped, "Stopped") else null
            "shell" -> ChatEntry.Shell(
                id, created, m.str("command").orEmpty(), m.str("status") ?: "exited",
                m.obj("output")?.str("output"), m.double("exit"),
            )
            // Synthetic, system and skill messages are instructions to the model, not conversation.
            else -> null
        }
    }

    private fun partFrom(index: Int, c: JsonObject, streaming: Boolean): Part? = when (c.str("type")) {
        "text" -> Part.Text(index, c.str("text").orEmpty(), streaming = streaming)
        "reasoning" -> Part.Reasoning(index, c.str("text").orEmpty(), false, c.obj("time")?.long("created"), c.obj("time")?.long("completed"))
        "tool" -> {
            val state = c.obj("state")
            val status = when (state?.str("status")) {
                "streaming" -> ToolStatus.Streaming
                "running" -> ToolStatus.Running
                "error" -> ToolStatus.Error
                else -> ToolStatus.Completed
            }
            Part.Tool(
                id = c.str("id") ?: "tool$index",
                name = c.str("name").orEmpty(),
                status = status,
                input = state?.obj("input"),
                rawInput = state?.str("input").orEmpty(),
                output = state?.arr("content")?.let(::contentText),
                files = state?.arr("content")?.let(::contentFiles) ?: emptyList(),
                metadata = state?.obj("metadata"),
                error = state?.obj("error")?.let(::error),
                started = c.obj("time")?.long("created"),
                completed = c.obj("time")?.long("completed"),
            )
        }
        else -> null
    }

    /** Replaces the loaded window with a fresh newest page, keeping older entries that are not in it. */
    fun mergeLatest(state: ChatState, latest: List<JsonObject>, cursor: String?): ChatState {
        val fresh = entriesFrom(latest)
        val oldest = fresh.firstOrNull()?.created ?: Long.MAX_VALUE
        val freshIds = fresh.mapTo(HashSet()) { it.id }
        val keptOlder = state.entries.filter { it.created < oldest && it.id !in freshIds && !(it is ChatEntry.User && it.pending) }
        val pending = state.entries.filter { it is ChatEntry.User && it.pending && it.id !in freshIds }
        val older = if (state.entries.isEmpty() || keptOlder.isEmpty()) cursor else state.olderCursor
        return state.copy(entries = keptOlder + fresh + pending, olderCursor = older)
    }

    fun prependOlder(state: ChatState, older: List<JsonObject>, cursor: String?): ChatState {
        val have = state.entries.mapTo(HashSet()) { it.id }
        val add = entriesFrom(older).filter { it.id !in have }
        return state.copy(entries = add + state.entries, olderCursor = cursor)
    }

    fun withPending(state: ChatState, permissions: List<PermissionRequest>, forms: List<Form>, inbox: List<InboxItem>): ChatState =
        state.copy(
            permissions = permissions,
            forms = forms,
            queued = inbox.filter { it.type == "user" }.map { QueuedMessage(it.id, inboxText(it.payload), it.delivery) },
        )

    /** Adds the user's words to the feed before the server confirms them. */
    fun optimisticUser(state: ChatState, localId: String, text: String, now: Long): ChatState =
        state.copy(entries = state.entries + ChatEntry.User(localId, now, text, pending = true), error = null)

    fun failOptimistic(state: ChatState, localId: String): ChatState =
        state.copy(entries = state.entries.map { if (it is ChatEntry.User && it.id == localId) it.copy(failed = true, pending = false) else it })

    // ------------------------------------------------------------------ events

    fun reduce(state: ChatState, e: ServerEvent): ChatState {
        val d = e.data
        if (e.id != null && (e.type == "session.compaction.started" || e.type == "session.execution.interrupted") && state.entries.any { it.id == e.id }) return state
        if (e.type.startsWith("session.") && e.sessionID != state.sessionID) return state
        if ((e.type.startsWith("permission.") || e.type.startsWith("form.")) && d.str("sessionID") != state.sessionID &&
            d.obj("form")?.str("sessionID") != state.sessionID
        ) return state
        if (e.seq != null && e.aggregateID == state.sessionID && state.lastSeq != null && e.seq <= state.lastSeq) return state
        val s = if (e.seq != null && e.aggregateID == state.sessionID) state.copy(lastSeq = maxOf(state.lastSeq ?: 0, e.seq)) else state
        val now = e.created ?: System.currentTimeMillis()
        val msgId = d.str("assistantMessageID")
        return when (e.type) {
            "session.renamed" -> s.copy(title = d.str("title"))
            "session.agent.selected" -> s.copy(agent = d.str("agent"))
            "session.model.selected" -> s.copy(model = d.obj("model")?.let(::modelRef))

            "session.execution.started" -> s.copy(running = true, runStartedAt = s.runStartedAt ?: now, error = null, retry = null)
            "session.execution.succeeded" -> s.finishRun()
            "session.execution.failed" -> s.finishRun().copy(error = d.obj("error")?.let(::error))
            "session.execution.interrupted" -> s.finishRun().let {
                if (d.str("reason") == "user") it.copy(entries = it.entries + ChatEntry.Marker(e.id ?: "stop$now", now, MarkerKind.Stopped, "Stopped by you")) else it
            }

            "session.inbox.enqueued" -> {
                val item = d.obj("item")
                val inboxID = d.str("inboxID") ?: return s
                val text = inboxText(item?.get("payload"))
                if (item?.str("type") != "user") s
                else if (item.str("delivery") == "queue" && s.running) s.copy(queued = s.queued.filter { it.id != inboxID } + QueuedMessage(inboxID, text, "queue"))
                else s.upsertUser(inboxID, now, text, pending = true)
            }
            "session.inbox.delivered" -> {
                val inboxID = d.str("inboxID") ?: return s
                val q = s.queued.firstOrNull { it.id == inboxID }
                val base = s.copy(queued = s.queued.filter { it.id != inboxID })
                if (q != null) base.upsertUser(inboxID, now, q.text, pending = false)
                else base.copy(entries = base.entries.map { if (it is ChatEntry.User && it.id == inboxID) it.copy(pending = false) else it })
            }
            "session.inbox.cancelled" -> {
                val inboxID = d.str("inboxID")
                s.copy(queued = s.queued.filter { it.id != inboxID }, entries = s.entries.filterNot { it is ChatEntry.User && it.id == inboxID && it.pending })
            }
            "session.inbox.delivery.changed" -> s.copy(queued = s.queued.map { if (it.id == d.str("inboxID")) it.copy(delivery = d.str("delivery") ?: it.delivery) else it })

            "session.step.started" -> s.withAssistant(msgId ?: return s, now) { it.copy(agent = d.str("agent") ?: it.agent, model = d.obj("model")?.let(::modelRef) ?: it.model) }
                .copy(running = true, runStartedAt = s.runStartedAt ?: now)
            "session.step.ended" -> s.withAssistant(msgId ?: return s, now) {
                it.copy(
                    finish = d.str("finish") ?: "stop", cost = d.double("cost"), tokens = d.obj("tokens")?.let(::tokens), completed = now,
                    parts = it.parts.map(::settle),
                )
            }
            "session.step.failed" -> s.withAssistant(msgId ?: return s, now) {
                it.copy(error = d.obj("error")?.let(::error), completed = now, parts = it.parts.map(::settle))
            }
            "session.retry.scheduled" -> s.copy(retry = d.obj("error")?.let { RetryInfo(d.int("attempt") ?: 1, d.long("at") ?: now, error(it)) })

            "session.text.started" -> s.withPart(msgId, now, "t${d.int("ordinal")}", create = { Part.Text(d.int("ordinal") ?: 0, "", streaming = true) })
            "session.text.delta" -> s.withPart(msgId, now, "t${d.int("ordinal")}", { Part.Text(d.int("ordinal") ?: 0, "", true) }) {
                (it as Part.Text).let { p -> if (p.streaming) p.copy(text = p.text + d.str("delta").orEmpty()) else p }
            }
            "session.text.ended" -> s.withPart(msgId, now, "t${d.int("ordinal")}", { Part.Text(d.int("ordinal") ?: 0, "", true) }) {
                (it as Part.Text).copy(text = d.str("text") ?: it.text, streaming = false)
            }
            "session.reasoning.started" -> s.withPart(msgId, now, "r${d.int("ordinal")}", create = { Part.Reasoning(d.int("ordinal") ?: 0, "", true, now, null) })
            "session.reasoning.delta" -> s.withPart(msgId, now, "r${d.int("ordinal")}", { Part.Reasoning(d.int("ordinal") ?: 0, "", true, now, null) }) {
                (it as Part.Reasoning).let { p -> if (p.streaming) p.copy(text = p.text + d.str("delta").orEmpty()) else p }
            }
            "session.reasoning.ended" -> s.withPart(msgId, now, "r${d.int("ordinal")}", { Part.Reasoning(d.int("ordinal") ?: 0, "", true, now, null) }) {
                (it as Part.Reasoning).copy(text = d.str("text") ?: it.text, streaming = false, completed = now)
            }

            "session.tool.input.started" -> s.withTool(msgId, now, d) { it.copy(name = d.str("name") ?: it.name) }
            "session.tool.input.delta" -> s.withTool(msgId, now, d) { it.copy(rawInput = it.rawInput + d.str("delta").orEmpty()) }
            "session.tool.input.ended" -> s.withTool(msgId, now, d) { it.copy(rawInput = d.str("text") ?: it.rawInput) }
            "session.tool.called" -> s.withTool(msgId, now, d) { it.copy(status = ToolStatus.Running, input = d.obj("input") ?: it.input, started = it.started ?: now) }
            "session.tool.progress" -> s.withTool(msgId, now, d) { it.copy(metadata = d.obj("metadata") ?: it.metadata) }
            "session.tool.success" -> s.withTool(msgId, now, d) {
                it.copy(
                    status = ToolStatus.Completed, output = d.arr("content")?.let(::contentText),
                    files = d.arr("content")?.let(::contentFiles) ?: emptyList(), metadata = d.obj("metadata") ?: it.metadata, completed = now,
                )
            }
            "session.tool.failed" -> s.withTool(msgId, now, d) {
                it.copy(status = ToolStatus.Error, error = d.obj("error")?.let(::error), output = d.arr("content")?.let(::contentText) ?: it.output, completed = now)
            }

            "session.compaction.started" -> s.copy(entries = s.entries + ChatEntry.Marker(e.id ?: "cmp$now", now, MarkerKind.Compaction, "Compacting context…"))
            "session.compaction.ended" -> s.replaceLastMarker(MarkerKind.Compaction, "Context compacted")
            "session.compaction.failed" -> s.replaceLastMarker(MarkerKind.Compaction, "Compaction failed")

            "permission.asked" -> {
                val req = catching { OpenCodeJson.decodeFromJsonElement(PermissionRequest.serializer(), d) }.getOrNull() ?: return s
                s.copy(permissions = s.permissions.filter { it.id != req.id } + req)
            }
            "permission.replied" -> s.copy(permissions = s.permissions.filter { it.id != d.str("requestID") })
            "form.created" -> {
                val form = d.obj("form")?.let { catching { OpenCodeJson.decodeFromJsonElement(Form.serializer(), it) }.getOrNull() } ?: return s
                s.copy(forms = s.forms.filter { it.id != form.id } + form)
            }
            "form.replied", "form.cancelled" -> s.copy(forms = s.forms.filter { it.id != d.str("id") })
            else -> s
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun ChatState.finishRun() = copy(
        running = false, runStartedAt = null, retry = null,
        entries = entries.map { if (it is ChatEntry.Assistant && it.streaming) it.copy(completed = System.currentTimeMillis(), parts = it.parts.map(::settle)) else it },
    )

    private fun settle(p: Part): Part = when (p) {
        is Part.Text -> p.copy(streaming = false)
        is Part.Reasoning -> p.copy(streaming = false)
        is Part.Tool -> p
    }

    private fun ChatState.upsertUser(id: String, now: Long, text: String, pending: Boolean): ChatState {
        // A local optimistic bubble with the same words is the same message: adopt the server id.
        val idx = entries.indexOfFirst { it is ChatEntry.User && (it.id == id || (it.pending && it.id.startsWith("local-") && it.text == text)) }
        return if (idx >= 0) {
            copy(entries = entries.toMutableList().also { it[idx] = (it[idx] as ChatEntry.User).copy(id = id, pending = pending, failed = false) })
        } else copy(entries = entries + ChatEntry.User(id, now, text, pending = pending))
    }

    private fun ChatState.withAssistant(id: String, now: Long, f: (ChatEntry.Assistant) -> ChatEntry.Assistant): ChatState {
        val idx = entries.indexOfFirst { it.id == id }
        val list = entries.toMutableList()
        if (idx >= 0) {
            val cur = list[idx] as? ChatEntry.Assistant ?: return this
            list[idx] = f(cur)
        } else {
            list += f(ChatEntry.Assistant(id, now))
        }
        return copy(entries = list)
    }

    private fun ChatState.withPart(msgId: String?, now: Long, key: String, create: () -> Part, update: (Part) -> Part = { it }): ChatState {
        if (msgId == null) return this
        return withAssistant(msgId, now) { a ->
            val i = a.parts.indexOfFirst { it.key == key }
            val parts = a.parts.toMutableList()
            if (i >= 0) parts[i] = update(parts[i]) else parts += update(create())
            a.copy(parts = parts)
        }
    }

    private fun ChatState.withTool(msgId: String?, now: Long, d: JsonObject, update: (Part.Tool) -> Part.Tool): ChatState {
        val toolId = d.str("id") ?: return this
        return withPart(msgId, now, "x$toolId", { Part.Tool(toolId, d.str("name").orEmpty(), ToolStatus.Streaming, started = now) }) { update(it as Part.Tool) }
    }

    private fun ChatState.replaceLastMarker(kind: MarkerKind, text: String): ChatState {
        val idx = entries.indexOfLast { it is ChatEntry.Marker && it.kind == kind }
        if (idx < 0) return this
        return copy(entries = entries.toMutableList().also { it[idx] = (it[idx] as ChatEntry.Marker).copy(text = text) })
    }

    fun inboxText(payload: JsonElement?): String = (payload as? JsonObject)?.str("text").orEmpty()

    private fun contentText(arr: JsonArray): String? =
        arr.mapNotNull { (it as? JsonObject)?.takeIf { o -> o.str("type") == "text" }?.str("text") }.joinToString("\n").ifEmpty { null }

    private fun contentFiles(arr: JsonArray): List<String> =
        arr.mapNotNull { (it as? JsonObject)?.takeIf { o -> o.str("type") == "file" }?.let { o -> o.str("name") ?: o.str("uri") } }

    fun modelRef(o: JsonObject) = ModelRef(o.str("id").orEmpty(), o.str("providerID").orEmpty(), o.str("variant"))

    fun error(o: JsonObject) = ApiError(o.str("type") ?: "unknown", o.str("message").orEmpty(), o.int("status"))

    private fun tokens(o: JsonObject) = catching { OpenCodeJson.decodeFromJsonElement(Tokens.serializer(), o) }.getOrNull()
}

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
internal fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
internal fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull ?: (this[k] as? JsonPrimitive)?.doubleOrNull?.toLong()
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.double(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
