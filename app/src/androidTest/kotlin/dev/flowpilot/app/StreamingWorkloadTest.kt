package dev.flowpilot.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.flowpilot.core.chat.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

/** Repeatable CPU workload; this is not a Compose frame-rate or network benchmark. */
@RunWith(AndroidJUnit4::class)
class StreamingWorkloadTest {
    @Test fun incrementalMarkdownAndFeedMatchBaselineOnLongChat() {
        val settled = (1..200).joinToString("\n\n") { "Paragraph $it with **bold**, `code`, and [a link](https://example.com)." } + "\n\n"
        val outputs = (1..100).map { settled + "Streaming tail " + "x".repeat(it * 4) }
        repeat(3) { MarkdownParser.parse(outputs.last()) }
        var baseline = emptyList<MdBlock>()
        val baselineNs = measureNanoTime { outputs.forEach { baseline = MarkdownParser.parse(it) } }
        val parser = MarkdownStreamParser()
        var incremental = emptyList<MdBlock>()
        val incrementalNs = measureNanoTime { outputs.forEach { incremental = parser.parse(it) } }
        assertEquals(baseline, incremental)
        val entries = (1..1000).map { ChatEntry.User("u$it", it.toLong(), "Message $it") }
        val cache = FeedCache()
        val first = cache.feed(ChatState("s", entries = entries))
        val updated = ChatState("s", entries = entries + ChatEntry.Assistant("a", 1001, parts = listOf(Part.Text(0, "tail", true))))
        val rows = cache.feed(updated)
        assertEquals(updated.feed(), rows)
        assertSame(first.first(), rows.first())
        Log.i("FlowPilotBenchmark", "200 settled paragraphs, 100 updates: full_parse_ms=${baselineNs / 1_000_000.0}, incremental_ms=${incrementalNs / 1_000_000.0}; 1000-message feed equivalence passed")
    }
}
