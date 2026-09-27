package dev.flowpilot.core.chat

import dev.flowpilot.core.api.ApiError
import dev.flowpilot.core.api.Form
import dev.flowpilot.core.api.ModelRef
import dev.flowpilot.core.api.PermissionRequest
import dev.flowpilot.core.api.Tokens
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/** Everything the chat screen renders, derived from REST pages plus live events. Immutable. */
@Serializable
data class ChatState(
    val sessionID: String,
    val title: String? = null,
    val entries: List<ChatEntry> = emptyList(),
    val running: Boolean = false,
    val runStartedAt: Long? = null,
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<Form> = emptyList(),
    val queued: List<QueuedMessage> = emptyList(),
    val lastSeq: Long? = null,
    val error: ApiError? = null,
    val retry: RetryInfo? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val olderCursor: String? = null,
) {
    val needsYou: Int get() = permissions.size + forms.size
    val hasOlder: Boolean get() = olderCursor != null
}

@Serializable
data class RetryInfo(val attempt: Int, val at: Long, val error: ApiError)

@Serializable
data class QueuedMessage(val id: String, val text: String, val delivery: String)

@Serializable
sealed interface ChatEntry {
    val id: String
    val created: Long

    @Serializable
    data class User(
        override val id: String,
        override val created: Long,
        val text: String,
        val files: List<String> = emptyList(),
        val pending: Boolean = false,
        val failed: Boolean = false,
    ) : ChatEntry

    @Serializable
    data class Assistant(
        override val id: String,
        override val created: Long,
        val agent: String? = null,
        val model: ModelRef? = null,
        val parts: List<Part> = emptyList(),
        val finish: String? = null,
        val error: ApiError? = null,
        val cost: Double? = null,
        val tokens: Tokens? = null,
        val completed: Long? = null,
    ) : ChatEntry {
        val streaming: Boolean get() = completed == null && error == null && finish == null
    }

    /** One-line events in the feed: agent or model switched, compaction, stop. */
    @Serializable
    data class Marker(override val id: String, override val created: Long, val kind: MarkerKind, val text: String) : ChatEntry

    @Serializable
    data class Shell(
        override val id: String,
        override val created: Long,
        val command: String,
        val status: String,
        val output: String?,
        val exit: Double?,
    ) : ChatEntry
}

@Serializable
enum class MarkerKind { AgentSwitched, ModelSwitched, Compaction, Stopped, Failed, Moved, Synthetic }

@Serializable
sealed interface Part {
    val key: String

    @Serializable
    data class Text(val ordinal: Int, val text: String, val streaming: Boolean) : Part {
        override val key get() = "t$ordinal"
    }

    @Serializable
    data class Reasoning(val ordinal: Int, val text: String, val streaming: Boolean, val started: Long?, val completed: Long?) : Part {
        override val key get() = "r$ordinal"
    }

    @Serializable
    data class Tool(
        val id: String,
        val name: String,
        val status: ToolStatus,
        val input: JsonObject? = null,
        val rawInput: String = "",
        val output: String? = null,
        val files: List<String> = emptyList(),
        val metadata: JsonObject? = null,
        val error: ApiError? = null,
        val started: Long? = null,
        val completed: Long? = null,
    ) : Part {
        override val key get() = "x$id"
    }
}

@Serializable
enum class ToolStatus { Streaming, Running, Completed, Error }

/** Items as the feed draws them: consecutive tool calls fold into one work group between text blocks. */
sealed interface FeedItem {
    val key: String

    data class UserBubble(val entry: ChatEntry.User) : FeedItem { override val key get() = "u" + entry.id }
    data class Text(val messageID: String, val part: Part.Text) : FeedItem { override val key get() = "a$messageID${part.key}" }
    data class Reasoning(val messageID: String, val part: Part.Reasoning) : FeedItem { override val key get() = "a$messageID${part.key}" }
    data class Work(val messageID: String, val tools: List<Part.Tool>, val live: Boolean) : FeedItem {
        override val key get() = "w$messageID${tools.first().id}"
    }
    data class Stats(val entry: ChatEntry.Assistant) : FeedItem { override val key get() = "s" + entry.id }
    data class Error(val messageID: String, val error: ApiError) : FeedItem { override val key get() = "e$messageID" }
    data class Marker(val entry: ChatEntry.Marker) : FeedItem { override val key get() = "m" + entry.id }
    data class Shell(val entry: ChatEntry.Shell) : FeedItem { override val key get() = "h" + entry.id }
}

fun ChatState.feed(): List<FeedItem> {
    val out = ArrayList<FeedItem>(entries.size * 2)
    entries.forEachIndexed { index, entry ->
        when (entry) {
            is ChatEntry.User -> out += FeedItem.UserBubble(entry)
            is ChatEntry.Marker -> out += FeedItem.Marker(entry)
            is ChatEntry.Shell -> out += FeedItem.Shell(entry)
            is ChatEntry.Assistant -> {
                var run = ArrayList<Part.Tool>()
                fun flush() {
                    if (run.isNotEmpty()) {
                        val live = run.any { it.status == ToolStatus.Running || it.status == ToolStatus.Streaming }
                        out += FeedItem.Work(entry.id, run, live)
                        run = ArrayList()
                    }
                }
                for (part in entry.parts) {
                    when (part) {
                        is Part.Tool -> run += part
                        is Part.Text -> {
                            if (part.text.isBlank() && !part.streaming) continue
                            flush(); out += FeedItem.Text(entry.id, part)
                        }
                        is Part.Reasoning -> {
                            if (part.text.isBlank() && !part.streaming) continue
                            flush(); out += FeedItem.Reasoning(entry.id, part)
                        }
                    }
                }
                flush()
                entry.error?.let { out += FeedItem.Error(entry.id, it) }
                // Stats sit under the last assistant message of a finished turn.
                val next = entries.getOrNull(index + 1)
                val turnOver = next !is ChatEntry.Assistant && !(index == entries.lastIndex && running)
                if (!entry.streaming && entry.error == null && turnOver) out += FeedItem.Stats(entry)
            }
        }
    }
    return out
}

/** Reuses presentation rows for unchanged messages; a streaming tail does not rebuild old work groups. */
class FeedCache {
    private data class Cached(val entry: ChatEntry, val turnOver: Boolean, val rows: List<FeedItem>)
    private var previous = emptyMap<String, Cached>()
    @Synchronized fun feed(state: ChatState): List<FeedItem> {
        val next = HashMap<String, Cached>(state.entries.size)
        val rows = ArrayList<FeedItem>()
        state.entries.forEachIndexed { index, entry ->
            val turnOver = state.entries.getOrNull(index + 1) !is ChatEntry.Assistant && !(index == state.entries.lastIndex && state.running)
            val old = previous[entry.id]
            val cached = if (old?.entry === entry && old.turnOver == turnOver) old else
                Cached(entry, turnOver, ChatState(state.sessionID, entries = listOf(entry), running = !turnOver).feed())
            next[entry.id] = cached
            rows += cached.rows
        }
        previous = next
        return rows
    }
}
