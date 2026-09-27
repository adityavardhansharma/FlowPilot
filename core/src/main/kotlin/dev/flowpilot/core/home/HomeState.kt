package dev.flowpilot.core.home

import dev.flowpilot.core.sync.catching

import dev.flowpilot.core.api.Project
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.Session
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.Part
import dev.flowpilot.core.chat.ToolDescriber
import dev.flowpilot.core.chat.ToolStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class RowStatus { NeedsYou, Failed, Working, Unread, Idle }

data class ThreadRowModel(
    val session: Session,
    val project: Project?,
    val status: RowStatus,
    /** Live action while working, the pending ask when it needs you, else the project folder. */
    val supporting: String,
    val supportingIsLive: Boolean,
    val pinned: Boolean,
) {
    val title: String get() = session.title?.takeIf { it.isNotBlank() } ?: "New chat"
    val lastActivity: Long get() = maxOf(session.time.updated, session.time.idle ?: 0)
}

data class HomeGroup(val label: String, val rows: List<ThreadRowModel>, val attention: Boolean = false)

/** Live, event-driven model of every chat on the computer. Pure and unit-tested. */
data class HomeState(
    val sessions: Map<String, Session> = emptyMap(),
    val projects: Map<String, Project> = emptyMap(),
    val active: Set<String> = emptySet(),
    val needsYou: Map<String, Set<String>> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val live: Map<String, String> = emptyMap(),
    val nextCursor: String? = null,
    /** True when the list changed in ways only a refetch can fix (a chat was created or moved). */
    val stale: Boolean = false,
) {
    fun row(s: Session, pinned: Set<String>, scratchDir: String?): ThreadRowModel {
        val ask = needsYou[s.id].orEmpty()
        val status = when {
            ask.isNotEmpty() -> RowStatus.NeedsYou
            s.id in active -> RowStatus.Working
            s.id in failed || (s.outcome == "failed" && (s.time.viewed ?: 0) < (s.time.idle ?: 0)) -> RowStatus.Failed
            (s.time.idle ?: 0) > (s.time.viewed ?: 0) -> RowStatus.Unread
            else -> RowStatus.Idle
        }
        val project = projects[s.projectID]
        val folder = if (scratchDir != null && s.location.directory.startsWith(scratchDir)) "No project" else project?.displayName ?: s.location.directory.substringAfterLast('/')
        val liveLine = live[s.id]
        val supporting = when {
            status == RowStatus.NeedsYou -> "Waiting for your approval"
            status == RowStatus.Working && liveLine != null -> liveLine
            status == RowStatus.Working -> "Working…"
            else -> folder
        }
        return ThreadRowModel(s, project, status, supporting, status == RowStatus.Working, s.id in pinned)
    }

    fun groups(pinned: Set<String>, scratchDir: String?, filter: HomeFilter = HomeFilter.All, now: Long = System.currentTimeMillis()): List<HomeGroup> {
        val rows = sessions.values
            .filter { it.parentID == null && it.time.archived == null }
            .map { row(it, pinned, scratchDir) }
            .filter { filter.matches(it) }
            .sortedByDescending { it.lastActivity }
        val attention = rows.filter { it.status == RowStatus.NeedsYou }
        val pinnedRows = rows.filter { it.pinned && it.status != RowStatus.NeedsYou }
        val rest = rows.filter { it.status != RowStatus.NeedsYou && !it.pinned }
        return buildList {
            if (attention.isNotEmpty()) add(HomeGroup("Needs you", attention, attention = true))
            if (pinnedRows.isNotEmpty()) add(HomeGroup("Pinned", pinnedRows))
            rest.groupBy { Format.bucket(it.lastActivity, now) }.toSortedMap().forEach { (b, list) -> add(HomeGroup(b.label, list)) }
        }
    }

    fun withPage(page: List<Session>, cursor: String?, replace: Boolean): HomeState {
        val base = if (replace) emptyMap() else sessions
        return copy(sessions = base + page.associateBy { it.id }, nextCursor = cursor, stale = false)
    }

    fun reduce(e: ServerEvent): HomeState {
        val id = e.sessionID
        val d = e.data
        return when (e.type) {
            "session.created", "session.moved", "session.forked" -> copy(stale = true)
            "session.deleted" -> copy(sessions = sessions - (id ?: return this), active = active - id)
            "session.renamed" -> update(id) { it.copy(title = d.s("title")) }
            "session.viewed" -> update(id) { it.copy(time = it.time.copy(viewed = d.l("idle") ?: System.currentTimeMillis())) }.copy(failed = failed - (id ?: ""))
            "session.execution.started" -> copy(active = active + (id ?: return this), failed = failed - id).update(id) { it.copy(time = it.time.copy(updated = e.created ?: it.time.updated)) }
            "session.execution.succeeded", "session.execution.interrupted" ->
                copy(active = active - (id ?: return this), live = live - id).update(id) { it.copy(time = it.time.copy(idle = e.created ?: System.currentTimeMillis())) }
            "session.execution.failed" -> copy(active = active - (id ?: return this), failed = failed + id, live = live - id)
                .update(id) { it.copy(time = it.time.copy(idle = e.created ?: System.currentTimeMillis())) }
            "session.tool.called" -> {
                val sid = id ?: return this
                val tool = Part.Tool(d.s("id").orEmpty(), d.s("name").orEmpty(), ToolStatus.Running, input = d["input"] as? JsonObject)
                val t = ToolDescriber.describe(tool)
                copy(live = live + (sid to "${t.verb} ${t.target}".trim() + "…"), active = active + sid)
            }
            "session.text.started", "session.reasoning.started" -> copy(live = live + ((id ?: return this) to if (e.type.startsWith("session.text")) "Writing…" else "Thinking…"))
            "permission.asked" -> ask(d.s("sessionID"), d.s("id"), true)
            "permission.replied" -> ask(d.s("sessionID"), d.s("requestID"), false)
            "form.created" -> ask((d["form"] as? JsonObject)?.s("sessionID"), (d["form"] as? JsonObject)?.s("id"), true)
            "form.replied", "form.cancelled" -> ask(d.s("sessionID"), d.s("id"), false)
            "project.updated" -> catching {
                val p = dev.flowpilot.core.api.OpenCodeJson.decodeFromJsonElement(Project.serializer(), d)
                copy(projects = projects + (p.id to p))
            }.getOrDefault(this)
            else -> this
        }
    }

    private fun ask(sessionID: String?, askID: String?, add: Boolean): HomeState {
        if (sessionID == null || askID == null) return this
        val cur = needsYou[sessionID].orEmpty()
        val next = if (add) cur + askID else cur - askID
        return copy(needsYou = if (next.isEmpty()) needsYou - sessionID else needsYou + (sessionID to next))
    }

    private inline fun update(id: String?, f: (Session) -> Session): HomeState {
        val s = sessions[id ?: return this] ?: return this
        return copy(sessions = sessions + (s.id to f(s)))
    }
}

enum class HomeFilter(val label: String) {
    All("All"), NeedsYou("Needs you"), Working("Working"), Pinned("Pinned");

    fun matches(r: ThreadRowModel) = when (this) {
        All -> true
        NeedsYou -> r.status == RowStatus.NeedsYou
        Working -> r.status == RowStatus.Working
        Pinned -> r.pinned
    }
}

private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
