package dev.flowpilot.core

import dev.flowpilot.core.chat.StreamPacer
import org.junit.Assert.*
import org.junit.Test

class StreamPacerTest {
    private val frame = 16_666_667L

    /** Runs [frames] display frames over a fixed [text] and returns what each one showed. */
    private fun StreamPacer.run(text: String, frames: Int, from: Long = 0): List<Int> = (1..frames).map { advance(text, from + it * frame) }

    @Test fun revealsGraduallyAndMonotonically() {
        val text = "word ".repeat(200)
        val shown = StreamPacer().run(text, 60)
        assertTrue(shown.zipWithNext().all { (a, b) -> b >= a })
        // One second of frames shows a readable amount, not the whole kilobyte burst at once.
        assertTrue(shown.last() in 40 until text.length)
    }

    @Test fun finishesEventuallyAndStops() {
        val text = "x".repeat(500)
        val shown = StreamPacer().run(text, 60 * 5)
        assertEquals(text.length, shown.last())
    }

    @Test fun largeBacklogNeverLagsFarBehind() {
        val text = "y".repeat(20_000)
        val shown = StreamPacer().run(text, 60 * 4)
        assertEquals(text.length, shown.last())
    }

    @Test fun cadenceIsIndependentOfRefreshRate() {
        val text = "z".repeat(2_000)
        val at60 = StreamPacer().let { p -> (1..60).map { p.advance(text, it * frame) }.last() }
        val at120 = StreamPacer().let { p -> (1..120).map { p.advance(text, it * frame / 2) }.last() }
        assertTrue("60Hz=$at60 120Hz=$at120", kotlin.math.abs(at60 - at120) <= 8)
    }

    @Test fun pauseDoesNotDumpText() {
        val text = "a".repeat(3_000)
        val pacer = StreamPacer()
        val before = pacer.run(text, 10).last()
        pacer.resume()
        // Ten seconds later, one frame moves only a frame's worth.
        val after = pacer.advance(text, 10_000_000_000L)
        assertEquals(before, after)
    }

    @Test fun seededPositionIsNotReplayed() {
        val pacer = StreamPacer(start = 100)
        assertTrue(pacer.advance("b".repeat(120), frame) >= 100)
    }

    @Test fun neverSplitsAGrapheme() {
        val text = "hi 👋🏽 there 👩‍💻 done ".repeat(20)
        val pacer = StreamPacer()
        val iterator = java.text.BreakIterator.getCharacterInstance()
        iterator.setText(text)
        pacer.run(text, 300).forEach { assertTrue("split at $it", iterator.isBoundary(it)) }
    }

    @Test fun shrinkingTextClamps() {
        val pacer = StreamPacer()
        pacer.run("c".repeat(400), 120)
        assertEquals(10, pacer.advance("c".repeat(10), 200 * frame))
    }
}
