package dev.flowpilot.core

import dev.flowpilot.core.chat.MarkdownParser
import dev.flowpilot.core.chat.MarkdownStreamParser
import org.junit.Assert.*
import org.junit.Test

class MarkdownStreamTest {
    @Test fun everyPrefixMatchesFullParser() {
        val samples = listOf(
            "Hello **world**\n\n# Heading\n\nMore text",
            "Intro\n\n```kotlin\n\nval x = 1\n```\n\nDone",
            "- one\n- two\n\n- next\n\nend",
            "| a | b |\n| -- | -- |\n| c | d |\n\nend",
            "> quote\n> more\n\n---\n\nend",
            "start\r\n\r\n~~~txt\r\nbody\r\n~~~\r\n\r\nend",
        )
        for (sample in samples) {
            val parser = MarkdownStreamParser()
            for (i in 1..sample.length) assertEquals("prefix $i of $sample", MarkdownParser.parse(sample.take(i)), parser.parse(sample.take(i)))
        }
    }
    @Test fun authoritativeReplacementResetsParser() {
        val parser = MarkdownStreamParser()
        parser.parse("old\n\n```text\nunfinished")
        val corrected = "corrected\n\nclosed"
        assertEquals(MarkdownParser.parse(corrected), parser.parse(corrected))
    }
    @Test fun settledBlocksRetainIdentity() {
        val parser = MarkdownStreamParser()
        val first = parser.parse("stable\n\ngrowing")
        val second = parser.parse("stable\n\ngrowing tail")
        assertSame(first[0], second[0])
    }
}
