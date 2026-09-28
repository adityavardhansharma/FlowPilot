package dev.flowpilot.core

import dev.flowpilot.core.chat.ComposerInput
import dev.flowpilot.core.chat.ComposerInput.Trigger
import org.junit.Assert.*
import org.junit.Test

class ComposerInputTest {
    @Test fun slashOnlyAtTheStart() {
        assertEquals(Trigger.Command(""), ComposerInput.trigger("/", 1))
        assertEquals(Trigger.Command("rev"), ComposerInput.trigger("/rev", 4))
        assertNull(ComposerInput.trigger("/review now", 11))
        assertNull(ComposerInput.trigger("a /rev", 6))
    }

    @Test fun mentionAnywhereAtCursor() {
        assertEquals(Trigger.Mention("", 0), ComposerInput.trigger("@", 1))
        assertEquals(Trigger.Mention("src/Ma", 4), ComposerInput.trigger("fix @src/Ma", 11))
        // Cursor back inside an earlier mention.
        assertEquals(Trigger.Mention("ab", 0), ComposerInput.trigger("@abc and more", 3))
        assertNull(ComposerInput.trigger("mail me@example.com", 19))
        assertNull(ComposerInput.trigger("@done ", 6))
    }

    @Test fun completingAMentionReplacesTheQuery() {
        val (text, cursor) = ComposerInput.completeMention("fix @Ma please", 7, 4, "src/Main.kt")
        assertEquals("fix @src/Main.kt please", text)
        assertEquals(17, cursor)
    }

    @Test fun commandsOnlyForKnownNames() {
        assertEquals("review" to "the diff", ComposerInput.command("/review the diff", listOf("review")))
        assertEquals("init" to "", ComposerInput.command("/init", listOf("init")))
        assertNull(ComposerInput.command("/usr/bin is a path", listOf("review")))
        assertNull(ComposerInput.command("review", listOf("review")))
    }

    @Test fun mentionedKeepsOnlyPathsStillInTheText() {
        val text = "compare @a/b.kt with @c.kt, not d.kt"
        assertEquals(listOf("a/b.kt", "c.kt"), ComposerInput.mentioned(text, listOf("a/b.kt", "c.kt", "d.kt", "a/b")))
    }
}
