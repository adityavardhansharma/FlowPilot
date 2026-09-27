package dev.flowpilot.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.flowpilot.app.data.Cache
import dev.flowpilot.app.data.HomeSnapshot
import dev.flowpilot.core.chat.*
import dev.flowpilot.core.sync.SavedChat
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineStorageTest {
    @Test fun historyAndCursorSurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        var cache = Cache(context, name)
        try {
            val entries = (1..1000).map { ChatEntry.User("m$it", it.toLong(), "message $it") }
            val saved = SavedChat(ChatState("session", entries = entries, lastSeq = 500, olderCursor = "older"))
            cache.writeChat("one", saved)
            cache.writeChat("two", SavedChat(ChatState("session", title = "other server")))
            cache.close(); cache = Cache(context, name)
            assertEquals(saved, cache.readChat("one", "session"))
            assertEquals("other server", cache.readChat("two", "session")?.chat?.title)
            cache.clear("one")
            assertNull(cache.readChat("one", "session"))
            assertNotNull(cache.readChat("two", "session"))
        } finally { cache.close(); context.deleteDatabase(name) }
    }

    @Test fun legacyJsonMigratesWithoutLosingMessages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        val server = "legacy-${UUID.randomUUID()}"
        val folder = java.io.File(context.filesDir, "cache/$server").apply { mkdirs() }
        val legacy = java.io.File(folder, "chat-s.json")
        legacy.writeText("""[{"id":"u","type":"user","text":"saved draft","time":{"created":1}}]""")
        val cache = Cache(context, name)
        try {
            assertEquals("saved draft", (cache.readChat(server, "s")!!.chat.entries.single() as ChatEntry.User).text)
            assertFalse(legacy.exists())
            assertEquals("u", cache.readChat(server, "s")!!.chat.entries.single().id)
        } finally { cache.close(); context.deleteDatabase(name); folder.deleteRecursively() }
    }

    @Test fun concurrentWritesKeepCursorAndMessagesInSameTransaction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        val cache = Cache(context, name)
        try {
            coroutineScope {
                (1..20).map { n -> async(Dispatchers.Default) {
                    cache.writeChat("server", SavedChat(ChatState("s", title = "$n", lastSeq = n.toLong(), entries = listOf(ChatEntry.User("m$n", n.toLong(), "$n")))))
                } }.awaitAll()
            }
            val saved = cache.readChat("server", "s")!!.chat
            assertEquals(saved.lastSeq.toString(), saved.title)
            assertEquals("m${saved.lastSeq}", saved.entries.single().id)
        } finally { cache.close(); context.deleteDatabase(name) }
    }

    @Test fun homeKeepsLoadedPagesBeyondInitialSixty() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        val cache = Cache(context, name)
        try {
            val sessions = (1..150).map { dev.flowpilot.core.api.Session("s$it", "p", location = dev.flowpilot.core.api.Location("/p")) }
            cache.writeHome("server", HomeSnapshot(sessions, cursor = "next"))
            assertEquals(150, cache.readHome("server")!!.sessions.size)
            assertEquals("next", cache.readHome("server")!!.cursor)
        } finally { cache.close(); context.deleteDatabase(name) }
    }
}
