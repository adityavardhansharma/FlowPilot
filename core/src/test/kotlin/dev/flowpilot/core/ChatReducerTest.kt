package dev.flowpilot.core

import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.chat.ChatEntry
import dev.flowpilot.core.chat.ChatReducer
import dev.flowpilot.core.chat.ChatState
import dev.flowpilot.core.chat.FeedItem
import dev.flowpilot.core.chat.Part
import dev.flowpilot.core.chat.ToolStatus
import dev.flowpilot.core.chat.feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {
    private val sid = "ses_1"

    private fun ev(type: String, data: String, seq: Long? = null): ServerEvent {
        val durable = seq?.let { ""","durable":{"aggregateID":"$sid","seq":$it,"version":1}""" }.orEmpty()
        return ServerEvent.parse("""{"id":"evt_$type$seq","created":1000,"type":"$type","data":$data$durable}""")!!
    }

    private fun run(vararg events: ServerEvent, start: ChatState = ChatState(sid)) = events.fold(start, ChatReducer::reduce)

    @Test fun streamsTextToolsAndFinishes() {
        val s = run(
            ev("session.inbox.enqueued", """{"sessionID":"$sid","inboxID":"msg_u","item":{"type":"user","payload":{"text":"Fix it"},"delivery":"steer"}}""", 1),
            ev("session.execution.started", """{"sessionID":"$sid"}""", 2),
            ev("session.inbox.delivered", """{"sessionID":"$sid","inboxID":"msg_u"}""", 3),
            ev("session.step.started", """{"sessionID":"$sid","assistantMessageID":"msg_a","agent":"build","model":{"id":"m","providerID":"p"},"started":1}""", 4),
            ev("session.text.started", """{"sessionID":"$sid","assistantMessageID":"msg_a","ordinal":0}""", 5),
            ev("session.text.delta", """{"sessionID":"$sid","assistantMessageID":"msg_a","ordinal":0,"delta":"Hel"}"""),
            ev("session.text.delta", """{"sessionID":"$sid","assistantMessageID":"msg_a","ordinal":0,"delta":"lo"}"""),
            ev("session.text.ended", """{"sessionID":"$sid","assistantMessageID":"msg_a","ordinal":0,"text":"Hello."}""", 6),
            ev("session.tool.input.started", """{"sessionID":"$sid","assistantMessageID":"msg_a","id":"call1","name":"read"}""", 7),
            ev("session.tool.called", """{"sessionID":"$sid","assistantMessageID":"msg_a","id":"call1","input":{"path":"src/a.kt"},"executed":true}""", 8),
            ev("session.tool.success", """{"sessionID":"$sid","assistantMessageID":"msg_a","id":"call1","content":[{"type":"text","text":"a\nb"}],"executed":true}""", 9),
            ev("session.step.ended", """{"sessionID":"$sid","assistantMessageID":"msg_a","finish":"stop","cost":0.42,"tokens":{"input":10,"output":5,"reasoning":0,"cache":{"read":0,"write":0}}}""", 10),
            ev("session.execution.succeeded", """{"sessionID":"$sid"}""", 11),
        )
        assertFalse(s.running)
        assertEquals(11L, s.lastSeq)
        val user = s.entries[0] as ChatEntry.User
        assertEquals("Fix it", user.text)
        assertFalse(user.pending)
        val a = s.entries[1] as ChatEntry.Assistant
        assertEquals("Hello.", (a.parts[0] as Part.Text).text)
        val tool = a.parts[1] as Part.Tool
        assertEquals(ToolStatus.Completed, tool.status)
        assertEquals("a\nb", tool.output)
        val feed = s.feed()
        assertTrue(feed[0] is FeedItem.UserBubble)
        assertTrue(feed[1] is FeedItem.Text)
        assertTrue(feed[2] is FeedItem.Work)
        assertTrue(feed.last() is FeedItem.Stats)
    }

    @Test fun missedDeltasHealOnEnded() {
        val s = run(ev("session.text.ended", """{"sessionID":"$sid","assistantMessageID":"msg_a","ordinal":0,"text":"Full text"}"""))
        assertEquals("Full text", ((s.entries.single() as ChatEntry.Assistant).parts.single() as Part.Text).text)
    }

    @Test fun ignoresOtherSessions() {
        val s = run(ev("session.execution.started", """{"sessionID":"other"}"""))
        assertFalse(s.running)
    }

    @Test fun optimisticBubbleAdoptsServerId() {
        val start = ChatReducer.optimisticUser(ChatState(sid), "local-1", "Hi", 1)
        val s = run(ev("session.inbox.enqueued", """{"sessionID":"$sid","inboxID":"msg_u","item":{"type":"user","payload":{"text":"Hi"},"delivery":"steer"}}"""), start = start)
        assertEquals(1, s.entries.size)
        assertEquals("msg_u", s.entries[0].id)
    }

    @Test fun queuedMessagesWaitWhileRunning() {
        val s = run(
            ev("session.execution.started", """{"sessionID":"$sid"}"""),
            ev("session.inbox.enqueued", """{"sessionID":"$sid","inboxID":"q1","item":{"type":"user","payload":{"text":"Then do this"},"delivery":"queue"}}"""),
        )
        assertEquals(listOf("Then do this"), s.queued.map { it.text })
        assertTrue(s.entries.isEmpty())
        val delivered = run(ev("session.inbox.delivered", """{"sessionID":"$sid","inboxID":"q1"}"""), start = s)
        assertTrue(delivered.queued.isEmpty())
        assertEquals("Then do this", (delivered.entries.single() as ChatEntry.User).text)
    }

    @Test fun permissionsAndFormsComeAndGo() {
        val s = run(
            ev("permission.asked", """{"id":"per_1","sessionID":"$sid","action":"shell","resources":["npm test"]}"""),
            ev("form.created", """{"form":{"id":"frm_1","sessionID":"$sid","title":"Which DB?","fields":[{"key":"db","type":"string","options":[{"value":"pg","label":"Postgres"}]}]}}"""),
        )
        assertEquals(2, s.needsYou)
        assertEquals("Postgres", s.forms.single().fields.single().options.single().label)
        val after = run(
            ev("permission.replied", """{"sessionID":"$sid","requestID":"per_1","reply":"once"}"""),
            ev("form.replied", """{"id":"frm_1","sessionID":"$sid","answer":{}}"""),
            start = s,
        )
        assertEquals(0, after.needsYou)
    }

    @Test fun failedTurnFromRealServerCapture() {
        val lines = javaClass.getResource("/failed-turn.sse")!!.readText().lineSequence()
            .filter { it.startsWith("data: ") }.mapNotNull { ServerEvent.parse(it.removePrefix("data: ")) }.toList()
        val id = lines.first { it.type == "session.execution.started" }.sessionID!!
        val s = lines.fold(ChatState(id), ChatReducer::reduce)
        assertFalse(s.running)
        assertNotNull(s.error)
        assertEquals("Renamed", s.title)
        val a = s.entries.filterIsInstance<ChatEntry.Assistant>().single()
        assertEquals(403, a.error?.status)
        assertTrue(s.feed().any { it is FeedItem.Error })
    }

    @Test fun parsesRestMessages() {
        val msgs = listOf(
            """{"id":"m2","type":"assistant","time":{"created":2,"completed":3},"agent":"build","model":{"id":"x","providerID":"y"},"finish":"stop",
               "content":[{"type":"reasoning","text":"hmm"},{"type":"text","text":"Done"},{"type":"tool","id":"t1","name":"shell","state":{"status":"error","input":{"command":"ls"},"error":{"type":"x","message":"boom"}},"time":{"created":2}}]}""",
            """{"id":"m1","type":"user","time":{"created":1},"text":"Go"}""",
            """{"id":"m0","type":"synthetic","time":{"created":0},"text":"hidden"}""",
        ).map { dev.flowpilot.core.api.OpenCodeJson.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject }
        val entries = ChatReducer.entriesFrom(msgs)
        assertEquals(listOf("m1", "m2"), entries.map { it.id })
        val tool = (entries[1] as ChatEntry.Assistant).parts[2] as Part.Tool
        assertEquals(ToolStatus.Error, tool.status)
        assertEquals("boom", tool.error?.message)
    }
}
