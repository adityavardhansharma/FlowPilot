package dev.flowpilot.core

import dev.flowpilot.core.chat.MarkdownParser
import dev.flowpilot.core.chat.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test fun parsesCommonBlocks() {
        val md = """
            # Title
            Some **bold** text
            that wraps.

            - one
            - [x] two
              continued
            1. first
            2. second

            > quoted

            | a | b |
            |---|---|
            | 1 | 2 |

            ---
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        val b = MarkdownParser.parse(md)
        assertEquals(MdBlock.Heading(1, "Title"), b[0])
        assertEquals(MdBlock.Paragraph("Some **bold** text\nthat wraps."), b[1])
        val list = b[2] as MdBlock.Bullets
        assertFalse(list.ordered)
        assertEquals(true, list.items[1].checked)
        assertEquals("two continued", list.items[1].text)
        assertTrue((b[3] as MdBlock.Bullets).ordered)
        assertEquals(MdBlock.Quote("quoted"), b[4])
        assertEquals(listOf("a", "b"), (b[5] as MdBlock.Table).header)
        assertEquals(MdBlock.Rule, b[6])
        assertEquals(MdBlock.Code("kotlin", "val x = 1", closed = true), b[7])
    }

    @Test fun openFenceWhileStreaming() {
        val b = MarkdownParser.parse("Here:\n```sh\nnpm te")
        assertEquals(MdBlock.Code("sh", "npm te", closed = false), b.last())
    }
}
