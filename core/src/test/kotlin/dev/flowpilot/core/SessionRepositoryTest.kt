@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.flowpilot.core

import dev.flowpilot.core.api.*
import dev.flowpilot.core.chat.*
import dev.flowpilot.core.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionRepositoryTest {
    private val id = "s"
    private fun message(id: String = "a", text: String = "old", created: Long = 1) =
        OpenCodeJson.parseToJsonElement("""{"id":"$id","type":"assistant","time":{"created":$created},"content":[{"type":"text","text":"$text"}]}""").jsonObject
    private fun event(type: String, data: String, seq: Long? = null) = ServerEvent.parse(
        """{"id":"e$seq$type","type":"$type","created":2,"data":{"sessionID":"s",$data}${seq?.let { ",\"durable\":{\"seq\":$it,\"aggregateID\":\"s\"}" }.orEmpty()}}"""
    )!!

    private class Memory(var saved: SavedChat? = null) : ChatStorage {
        override suspend fun readChat(server: String, session: String) = saved
        override suspend fun writeChat(server: String, snapshot: SavedChat) {
            // Exercise polymorphic serialization and atomic state/cursor roundtrip.
            saved = OpenCodeJson.decodeFromString(SavedChat.serializer(), OpenCodeJson.encodeToString(SavedChat.serializer(), snapshot))
        }
    }
    private inner class Source : SessionSource {
        var pages: suspend (String?) -> Pair<List<JsonObject>, Cursor> = { listOf(message()) to Cursor() }
        var replayEvents: Flow<StreamSignal> = emptyFlow()
        var asks: suspend () -> List<PermissionRequest> = { emptyList() }
        val cursors = mutableListOf<String?>()
        override suspend fun session(id: String) = Session(id = id, projectID = "p", location = Location("/p"))
        override suspend fun messages(id: String, cursor: String?): Pair<List<JsonObject>, Cursor> { cursors += cursor; return pages(cursor) }
        override suspend fun permissions(id: String) = asks()
        override suspend fun forms(id: String) = emptyList<Form>()
        override suspend fun inbox(id: String) = emptyList<InboxItem>()
        override suspend fun running(id: String) = false
        override fun replay(id: String, after: Long) = replayEvents
    }
    private fun TestScope.repo(source: Source, memory: Memory = Memory()) =
        SessionRepository("server", id, source, memory, backgroundScope, StandardTestDispatcher(testScheduler))
    private fun text(repo: SessionRepository) = ((repo.state.value.saved.chat.entries.first { it.id == "a" } as ChatEntry.Assistant).parts[0] as Part.Text).text

    @Test fun delayedSnapshotCannotEraseBufferedTerminalText() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = Source().apply { pages = { gate.await(); listOf(message()) to Cursor() } }
        val repo = repo(source)
        runCurrent()
        repo.accept(event("session.text.ended", "\"assistantMessageID\":\"a\",\"ordinal\":0,\"text\":\"new\"", 2))
        gate.complete(Unit); runCurrent()
        assertEquals("new", text(repo))
        assertEquals(2L, repo.state.value.saved.chat.lastSeq)
    }

    @Test fun transcriptDoesNotWaitForAuxiliaryEndpoints() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = Source().apply { asks = { gate.await(); emptyList() } }
        val repo = repo(source); runCurrent()
        assertEquals("old", text(repo))
        assertFalse(repo.state.value.loading)
        gate.complete(Unit); runCurrent()
    }

    @Test fun failedPermissionsRetainCachedAsks() = runTest {
        val permission = OpenCodeJson.decodeFromString(PermissionRequest.serializer(), """{"id":"p","sessionID":"s","action":"shell","resources":[]}""")
        val store = Memory(SavedChat(ChatState(id, permissions = listOf(permission))))
        val source = Source().apply { asks = { throw java.io.IOException("offline") } }
        val repo = repo(source, store); runCurrent()
        assertEquals(listOf(permission), repo.state.value.saved.chat.permissions)
        assertEquals("offline", repo.state.value.error)
    }

    @Test fun replayDeduplicatesAndPersistsCheckpointWithState() = runTest {
        val store = Memory(SavedChat(ChatState(id, lastSeq = 2)))
        val done = event("session.execution.interrupted", "\"reason\":\"user\"", 3)
        val source = Source().apply { replayEvents = flowOf(StreamSignal.Event(done), StreamSignal.Event(done)) }
        val repo = repo(source, store); runCurrent()
        repo.accept(done); runCurrent()
        assertEquals(3L, store.saved?.chat?.lastSeq)
        assertTrue(repo.state.value.saved.chat.entries.count { it is ChatEntry.Marker } <= 1)
    }

    @Test fun unavailableReplayBridgesMultiplePages() = runTest {
        val old = ChatReducer.entriesFrom(listOf(message("old", created = 1)))
        val store = Memory(SavedChat(ChatState(id, entries = old, lastSeq = 4)))
        val source = Source().apply {
            replayEvents = flow { throw ApiException.Http(404, "no log") }
            pages = { cursor -> when (cursor) {
                null -> listOf(message("new", created = 3)) to Cursor(next = "middle")
                "middle" -> listOf(message("middle", created = 2)) to Cursor(next = "old")
                else -> listOf(message("old", created = 1)) to Cursor()
            } }
        }
        val repo = repo(source, store); runCurrent()
        assertEquals(listOf(null, "middle", "old"), source.cursors)
        assertEquals(listOf("old", "middle", "new"), repo.state.value.saved.chat.entries.map { it.id })
        assertNull(store.saved?.chat?.lastSeq)
    }

    @Test fun oldConnectionResponseCannotCommitAfterInvalidation() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val source = Source().apply { pages = {
            calls++
            if (calls == 1) { gate.await(); listOf(message(text = "obsolete")) to Cursor() }
            else listOf(message(text = "current")) to Cursor()
        } }
        val repo = repo(source); runCurrent()
        repo.invalidate(); gate.complete(Unit); runCurrent()
        assertEquals("current", text(repo))
    }

    @Test fun deltasAreBatchedAndEndedIsAuthoritative() = runTest {
        val repo = repo(Source()); runCurrent()
        repeat(100) { repo.accept(event("session.text.delta", "\"assistantMessageID\":\"a\",\"ordinal\":0,\"delta\":\"x\"")) }
        assertEquals("old", text(repo))
        advanceTimeBy(16); runCurrent()
        assertEquals("old" + "x".repeat(100), text(repo))
        repo.accept(event("session.text.ended", "\"assistantMessageID\":\"a\",\"ordinal\":0,\"text\":\"final\"", 5))
        repo.accept(event("session.text.delta", "\"assistantMessageID\":\"a\",\"ordinal\":0,\"delta\":\"late\""))
        advanceTimeBy(16); runCurrent()
        assertEquals("final", text(repo))
    }

    @Test fun loadedOlderPagesSurviveRestart() = runTest {
        val store = Memory()
        val source = Source().apply { pages = { cursor ->
            if (cursor == null) listOf(message("new", created = 2)) to Cursor(next = "old")
            else listOf(message("old", created = 1)) to Cursor()
        } }
        val repo = repo(source, store); runCurrent()
        repo.loadOlder(); runCurrent(); repo.close()
        source.pages = { awaitCancellation() }
        val reopened = repo(source, store); runCurrent()
        assertEquals(listOf("old", "new"), reopened.state.value.saved.chat.entries.map { it.id })
    }

    @Test fun confirmedLocalChangeSurvivesAnOlderRefresh() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = Source().apply { pages = { gate.await(); listOf(message()) to Cursor() } }
        val repo = repo(source); runCurrent()
        repo.update { it.copy(title = "confirmed rename") }
        gate.complete(Unit); runCurrent()
        assertEquals("confirmed rename", repo.state.value.saved.chat.title)
    }

    @Test fun overflowInvalidatesIncompleteSnapshotAndRecovers() = runTest {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val source = Source().apply { pages = {
            reads++
            if (reads == 1) gate.await()
            listOf(message(text = "complete")) to Cursor()
        } }
        val repo = repo(source); runCurrent()
        repeat(4100) { repo.accept(event("session.text.delta", "\"assistantMessageID\":\"a\",\"ordinal\":0,\"delta\":\"x\"")) }
        gate.complete(Unit); runCurrent()
        assertEquals(2, reads)
        assertEquals("complete", text(repo))
        assertFalse(repo.state.value.syncing)
    }

    @Test fun cancelledOperationIsNeverConvertedToFailure() {
        try { catching { throw CancellationException("cancel") }; fail("swallowed cancellation") }
        catch (_: CancellationException) { }
    }

    @Test fun resourceReadsAreCoalescedAndInvalidated() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val resource = ResourceCache(60_000) { calls++; gate.await(); calls }
        val reads = List(20) { async { resource.get() } }
        runCurrent(); assertEquals(1, calls)
        gate.complete(Unit); reads.awaitAll()
        resource.invalidate(); assertEquals(2, resource.get())
    }
}
