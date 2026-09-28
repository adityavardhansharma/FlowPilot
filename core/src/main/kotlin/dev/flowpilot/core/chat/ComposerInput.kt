package dev.flowpilot.core.chat

/**
 * The composer's typed shortcuts, the same ones OpenCode's own clients use:
 * `/` at the very start picks a slash command, `@` anywhere picks a file to add as context, and `!` at the start
 * of an empty box switches to running a shell command.
 */
object ComposerInput {
    sealed interface Trigger {
        /** The partial word being typed after the trigger character. */
        val query: String
        data class Command(override val query: String) : Trigger
        data class Mention(override val query: String, val start: Int) : Trigger
    }

    private val slash = Regex("^/([\\w:.-]*)$")
    private val mention = Regex("(?:^|\\s)@([^\\s@]*)$")

    /** What suggestions to offer for [text] with the cursor at [cursor], or null for none. */
    fun trigger(text: String, cursor: Int): Trigger? {
        val before = text.substring(0, cursor.coerceIn(0, text.length))
        slash.find(before)?.let { return Trigger.Command(it.groupValues[1]) }
        mention.find(before)?.let { m ->
            val at = m.range.last - m.groupValues[1].length
            return Trigger.Mention(m.groupValues[1], at)
        }
        return null
    }

    /** [text] with the `@query` that starts at [start] replaced by `@path `, and where the cursor goes after it. */
    fun completeMention(text: String, cursor: Int, start: Int, path: String): Pair<String, Int> {
        val end = cursor.coerceIn(start, text.length)
        val insert = "@$path "
        val out = text.substring(0, start) + insert + text.substring(end).trimStart(' ')
        return out to start + insert.length
    }

    /** `/name rest` → (name, rest) when [name] is one of [known]; anything else is an ordinary prompt. */
    fun command(text: String, known: Collection<String>): Pair<String, String>? {
        if (!text.startsWith("/")) return null
        val name = text.substring(1).substringBefore(' ').substringBefore('\n')
        if (name.isEmpty() || name !in known) return null
        return name to text.substring(1 + name.length).trim()
    }

    /** The `@path` mentions in [text] that name one of [paths], in order, without repeats. */
    fun mentioned(text: String, paths: Collection<String>): List<String> =
        paths.filter { p -> Regex("(?:^|\\s)@" + Regex.escape(p) + "(?=[,.;:!?)]*(?:\\s|$))").containsMatchIn(text) }.distinct()
}
