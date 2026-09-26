package dev.flowpilot.core.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Which Material Symbol a tool row shows. The app maps these to drawables. */
enum class ToolIcon { Read, Search, Glob, Edit, Patch, Write, Shell, WebSearch, WebFetch, Browser, Subagent, Skill, Question, Mcp }

enum class ToolKind { Explore, Edit, Create, Run, Web, Delegate, Ask, Other }

data class FileChange(val file: String, val additions: Int, val deletions: Int, val patch: String?, val status: String?)

/** A tool call described in plain words, the way the design system's ToolRow reads. */
data class ToolText(
    val icon: ToolIcon,
    val kind: ToolKind,
    /** Sentence prefix in the right tense ("Read", "Reading"). */
    val verb: String,
    /** The thing acted on: a path, command, query or URL. Shown in code style when [code] is true. */
    val target: String,
    val code: Boolean,
    /** Trailing meta such as "212 lines", "7 matches", "exit 0". */
    val meta: String? = null,
    val changes: List<FileChange> = emptyList(),
) {
    val additions: Int get() = changes.sumOf { it.additions }
    val deletions: Int get() = changes.sumOf { it.deletions }
}

object ToolDescriber {

    /** Tool names arrive as "read" or "opencode.tool.read"; MCP tools as "server_tool". */
    fun shortName(name: String): String = name.substringAfterLast('.')

    fun describe(t: Part.Tool): ToolText {
        val done = t.status == ToolStatus.Completed || t.status == ToolStatus.Error
        val input = t.input ?: parsePartial(t.rawInput)
        val meta = t.metadata
        fun v(present: String, past: String) = if (done) past else present
        fun s(k: String) = (input?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val changes = fileChanges(meta)
        return when (val n = shortName(t.name)) {
            "read" -> ToolText(ToolIcon.Read, ToolKind.Explore, v("Reading", "Read"), shortPath(s("path")), true,
                meta = t.output?.let { out -> out.lineSequence().count().takeIf { it > 1 }?.let { "$it lines" } })
            "glob" -> ToolText(ToolIcon.Glob, ToolKind.Explore, v("Finding", "Found"), s("pattern").orEmpty(), true,
                meta = meta.num("count")?.let { "$it files" })
            "grep" -> ToolText(ToolIcon.Search, ToolKind.Explore, v("Searching", "Searched"), s("pattern").orEmpty(), true,
                meta = meta.num("matches")?.let { if (it == 1) "1 match" else "$it matches" })
            "edit" -> ToolText(ToolIcon.Edit, ToolKind.Edit, v("Editing", "Edited"), shortPath(s("path")), true, changes = changes)
            "patch" -> ToolText(ToolIcon.Patch, ToolKind.Edit, v("Patching", "Patched"),
                if (changes.size == 1) shortPath(changes[0].file) else if (changes.isEmpty()) "files" else "${changes.size} files", changes.size == 1, changes = changes)
            "write" -> ToolText(ToolIcon.Write, ToolKind.Create, v("Writing", "Wrote"), shortPath(s("path")), true, changes = changes)
            "shell", "bash" -> ToolText(ToolIcon.Shell, ToolKind.Run, v("Running", "Ran"), (s("command") ?: "").lineSequence().firstOrNull().orEmpty(), true,
                meta = exitMeta(t))
            "websearch" -> ToolText(ToolIcon.WebSearch, ToolKind.Web, v("Searching the web for", "Searched the web for"), s("query").orEmpty(), false)
            "webfetch" -> ToolText(ToolIcon.WebFetch, ToolKind.Web, v("Fetching", "Fetched"), host(s("url")), false)
            "subagent", "task" -> ToolText(ToolIcon.Subagent, ToolKind.Delegate, v("Delegating to", "Delegated to"),
                listOfNotNull(s("agent"), s("description")).joinToString(" · "), false)
            "skill" -> ToolText(ToolIcon.Skill, ToolKind.Other, v("Loading skill", "Loaded skill"), s("name") ?: s("skill") ?: "", false)
            "question" -> ToolText(ToolIcon.Question, ToolKind.Ask, v("Asking", "Asked"), "a question", false)
            else -> when {
                n.startsWith("browser") || t.name.contains("browser") -> ToolText(ToolIcon.Browser, ToolKind.Web, v("Using the browser:", "Used the browser:"), n.removePrefix("browser_").replace('_', ' '), false)
                else -> ToolText(ToolIcon.Mcp, ToolKind.Other, v("Using", "Used"), n.replace('_', ' '), false)
            }
        }
    }

    /**
     * Past-tense summary for a finished work group: "Explored 6 files, edited 1, ran 2 commands".
     * Present-tense line for a live group comes from the latest running tool instead.
     */
    fun summary(tools: List<Part.Tool>): String {
        val texts = tools.map(::describe)
        val counts = texts.groupingBy { it.kind }.eachCount()
        val parts = buildList {
            counts[ToolKind.Explore]?.let { add("explored ${plural(it, "file")}") }
            counts[ToolKind.Web]?.let { add("searched the web ${times(it)}") }
            counts[ToolKind.Edit]?.let { add("edited ${plural(it, "file")}") }
            counts[ToolKind.Create]?.let { add("created ${plural(it, "file")}") }
            counts[ToolKind.Run]?.let { add("ran ${plural(it, "command")}") }
            counts[ToolKind.Delegate]?.let { add("started ${plural(it, "subagent")}") }
            counts[ToolKind.Ask]?.let { add("asked ${plural(it, "question")}") }
            counts[ToolKind.Other]?.let { add("used ${plural(it, "tool")}") }
        }
        val failed = tools.count { it.status == ToolStatus.Error }
        val base = parts.joinToString(", ").replaceFirstChar(Char::uppercase)
        return if (failed > 0) "$base · $failed failed" else base
    }

    private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
    private fun times(n: Int) = if (n == 1) "once" else "$n times"

    private fun exitMeta(t: Part.Tool): String? {
        val m = t.metadata ?: return null
        val exit = m.num("exit") ?: (m["output"] as? JsonObject).num("exit") ?: return null
        val secs = if (t.started != null && t.completed != null) Format.duration(t.completed - t.started) else null
        return listOfNotNull("exit $exit", secs).joinToString(" · ")
    }

    fun fileChanges(meta: JsonObject?): List<FileChange> {
        val files = meta?.get("files") as? JsonArray ?: return emptyList()
        return files.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            FileChange(
                file = (o["file"] as? JsonPrimitive)?.content ?: return@mapNotNull null,
                additions = o.num("additions") ?: 0,
                deletions = o.num("deletions") ?: 0,
                patch = (o["patch"] as? JsonPrimitive)?.content,
                status = (o["status"] as? JsonPrimitive)?.content,
            )
        }
    }

    /** Pulls the first string fields out of streaming, possibly unterminated JSON input. */
    fun parsePartial(raw: String): JsonObject? {
        if (raw.isBlank()) return null
        val map = LinkedHashMap<String, JsonPrimitive>()
        Regex("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)").findAll(raw).forEach { m ->
            map.putIfAbsent(m.groupValues[1], JsonPrimitive(m.groupValues[2].replace("\\n", "\n").replace("\\\"", "\"")))
        }
        return JsonObject(map)
    }

    /** Keeps the file name and trims the middle: "src/…/auth/redirect.kt". */
    fun shortPath(path: String?, max: Int = 42): String {
        if (path.isNullOrBlank()) return ""
        if (path.length <= max) return path
        val name = path.substringAfterLast('/')
        val head = path.take((max - name.length - 2).coerceAtLeast(4)).substringBeforeLast('/')
        return "$head/…/$name"
    }

    private fun host(url: String?): String = url?.substringAfter("://")?.substringBefore('/')?.removePrefix("www.").orEmpty()
}

private fun JsonObject?.num(k: String): Int? = (this?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
