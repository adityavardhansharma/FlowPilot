package dev.flowpilot.core.sync

/** Bounded numerical diagnostics only: never URLs, credentials, prompts, or tool output. */
object Diagnostics {
    private val started = System.nanoTime()
    private val points = ArrayDeque<String>()
    @Synchronized fun record(name: String, value: Long = 1) {
        require(name.matches(Regex("[a-z_.]+")))
        points.addLast("${(System.nanoTime() - started) / 1_000_000}ms $name=$value")
        while (points.size > 128) points.removeFirst()
    }
    @Synchronized fun export(): String = points.joinToString("\n")
}
