package dev.flowpilot.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Wire types for the OpenCode v2 HTTP API (verified against @opencode/cli 2.0.18). Decoded leniently. */

@Serializable
data class ServerInfo(val version: String, val pid: Int? = null, val urls: List<String> = emptyList())

@Serializable
data class PairResponse(val token: String)

@Serializable
data class Location(val directory: String)

@Serializable
data class ModelRef(val id: String, val providerID: String, val variant: String? = null)

@Serializable
data class Tokens(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val reasoning: Double = 0.0,
    val cache: Cache = Cache(),
) {
    @Serializable
    data class Cache(val read: Double = 0.0, val write: Double = 0.0)

    val total: Double get() = input + output + reasoning + cache.read + cache.write
}

@Serializable
data class Project(
    val id: String,
    val canonical: String,
    val vcs: String? = null,
    val name: String? = null,
    val time: Time = Time(),
) {
    @Serializable
    data class Time(val created: Long = 0, val updated: Long = 0, val active: Long = 0)

    /** The folder name when the project has no explicit name. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: canonical.trimEnd('/').substringAfterLast('/').ifEmpty { canonical }
}

@Serializable
data class Session(
    val id: String,
    val projectID: String,
    val parentID: String? = null,
    val title: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: Tokens = Tokens(),
    val outcome: String? = null,
    val time: Time = Time(),
    val location: Location,
    val metadata: JsonObject? = null,
) {
    @Serializable
    data class Time(
        val created: Long = 0,
        val updated: Long = 0,
        val idle: Long? = null,
        val viewed: Long? = null,
        val archived: Long? = null,
    )
}

@Serializable
data class Cursor(val previous: String? = null, val next: String? = null)

@Serializable
data class Page<T>(val data: List<T>, val cursor: Cursor = Cursor())

@Serializable
data class DataEnvelope<T>(val data: T)

@Serializable
data class Model(
    val id: String,
    val modelID: String = id,
    val providerID: String,
    val name: String,
    val family: String? = null,
    val capabilities: Capabilities = Capabilities(),
    val variants: List<Variant> = emptyList(),
    val status: String = "active",
    val enabled: Boolean = true,
    val limit: Limit = Limit(),
    val cost: List<Cost> = emptyList(),
) {
    @Serializable
    data class Capabilities(val tools: Boolean = true, val input: List<String> = emptyList(), val output: List<String> = emptyList())

    @Serializable
    data class Variant(val id: String)

    @Serializable
    data class Limit(val context: Long = 0, val output: Long = 0)

    @Serializable
    data class Cost(val input: Double = 0.0, val output: Double = 0.0)

    val key: String get() = "$providerID/$id"
    val ref: ModelRef get() = ModelRef(id = id, providerID = providerID)
}

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String? = null,
    val mode: String = "primary",
    val hidden: Boolean = false,
    val color: String? = null,
) {
    val selectable: Boolean get() = !hidden && mode != "subagent"
}

@Serializable
data class LocatedList<T>(val location: Location? = null, val data: List<T>)

@Serializable
data class Located<T>(val location: Location? = null, val data: T)

@Serializable
data class FsEntry(val path: String, val type: String) {
    val isDirectory: Boolean get() = type == "directory"
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
}

@Serializable
data class ShellInfo(
    val id: String,
    val status: String,
    val command: String,
    val cwd: String = "",
    val exit: Double? = null,
) {
    val running: Boolean get() = status == "running"
}

@Serializable
data class ShellOutput(val output: String, val cursor: Long = 0, val size: Long = 0, val truncated: Boolean = false)

@Serializable
data class PermissionRequest(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: List<String> = emptyList(),
    val save: List<String>? = null,
    val metadata: JsonObject? = null,
    val source: Source? = null,
    val message: String? = null,
) {
    @Serializable
    data class Source(val type: String, val messageID: String? = null, val id: String? = null)
}

@Serializable
data class Form(
    val id: String,
    val sessionID: String,
    val title: String,
    val fields: List<FormField> = emptyList(),
)

@Serializable
data class FormField(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val hidden: Boolean = false,
    val placeholder: String? = null,
    val options: List<FormOption> = emptyList(),
    val custom: Boolean = false,
    val default: JsonElement? = null,
    val url: String? = null,
)

@Serializable
data class FormOption(val value: String, val label: String = value, val description: String? = null)

@Serializable
data class CreateSessionBody(
    val location: Location? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val title: String? = null,
)

@Serializable
data class PromptBody(
    val text: String,
    val files: List<PromptFile>? = null,
    val agents: List<PromptAgent>? = null,
    val delivery: String? = null,
)

/** A file sent with a prompt: `file://` for a file on the computer, or a `data:` URI (images, base64). */
@Serializable
data class PromptFile(val uri: String, val name: String? = null)

/** A slash command defined on the computer (built in, or from the project's or user's command folders). */
@Serializable
data class CommandInfo(val name: String, val description: String? = null)

@Serializable
data class CommandBody(
    val name: String,
    val text: String,
    val files: List<PromptFile>? = null,
    val delivery: String? = null,
)

@Serializable
data class SessionShellBody(val command: String)

@Serializable
data class PromptAgent(val name: String)

@Serializable
data class PermissionReplyBody(val decision: String, val message: String? = null)

@Serializable
data class FormReplyBody(val answer: JsonObject)

@Serializable
data class ShellBody(val command: String, val cwd: String? = null, val timeout: Int? = null)

@Serializable
data class ViewBody(val idle: Long)

@Serializable
data class RenameBody(val title: String)

@Serializable
data class ModelBody(val model: ModelRef)

@Serializable
data class AgentBody(val agent: String)

@Serializable
data class InboxItem(
    val id: String,
    val sessionID: String,
    val type: String,
    val payload: JsonElement? = null,
    val delivery: String = "queue",
)

/** Shape of an error returned by a failed turn, tool or step. */
@Serializable
data class ApiError(val type: String = "unknown", val message: String = "", val status: Int? = null)

@Serializable
data class ToolFileContent(val uri: String, val mime: String, val name: String? = null)

enum class Decision(val wire: String) { Once("once"), Always("always"), Reject("reject") }

enum class Delivery(val wire: String) { Steer("steer"), Queue("queue") }

@Serializable
data class ActiveEntry(@SerialName("type") val type: String? = null)
