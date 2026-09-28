package dev.flowpilot.core

import dev.flowpilot.core.chat.*
import org.junit.Assert.*
import org.junit.Test

class FeedCacheTest {
    @Test fun cachedRowsMatchFeedAcrossTurnBoundaries() {
        val cache = FeedCache()
        val entries = listOf(
            ChatEntry.User("u", 1, "hello"),
            ChatEntry.Assistant("a", 2, parts = listOf(Part.Text(0, "one", false)), finish = "stop"),
            ChatEntry.Assistant("b", 3, parts = listOf(Part.Text(0, "two", false)), finish = "stop"),
        )
        for (running in listOf(true, false)) {
            val state = ChatState("s", entries = entries, running = running)
            assertEquals(state.feed(), cache.feed(state))
        }
    }
    @Test fun unchangedRowsAreReusedForStreamingTail() {
        val cache = FeedCache()
        val user = ChatEntry.User("u", 1, "hello")
        val first = cache.feed(ChatState("s", entries = listOf(user, ChatEntry.Assistant("a", 2))))
        val second = cache.feed(ChatState("s", entries = listOf(user, ChatEntry.Assistant("a", 2, parts = listOf(Part.Text(0, "new", true))))))
        assertSame(first[0], second[0])
    }

    @Test fun repeatedModeSwitchesCollapseToTheLast() {
        fun switch(id: String, to: String) = ChatEntry.Marker(id, 1, MarkerKind.AgentSwitched, "Switched to $to")
        val entries = listOf(
            switch("m1", "Plan"), switch("m2", "Build"), switch("m3", "Plan"),
            ChatEntry.User("u", 2, "hi"),
            switch("m4", "Build"),
            ChatEntry.Marker("m5", 3, MarkerKind.ModelSwitched, "Switched to x"),
            switch("m6", "Plan"),
        )
        val state = ChatState("s", entries = entries)
        val keys = FeedCache().feed(state).map { it.key }
        assertEquals(listOf("mm3", "uu", "mm4", "mm5", "mm6"), keys)
        assertEquals(state.feed(), FeedCache().feed(state))
    }
}
