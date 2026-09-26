package dev.flowpilot.core

import dev.flowpilot.core.api.Location
import dev.flowpilot.core.api.OpenCodeJson
import dev.flowpilot.core.api.PairingLink
import dev.flowpilot.core.api.ProjectOps
import dev.flowpilot.core.api.ServerEvent
import dev.flowpilot.core.api.Session
import dev.flowpilot.core.chat.Format
import dev.flowpilot.core.chat.Part
import dev.flowpilot.core.chat.ToolDescriber
import dev.flowpilot.core.chat.ToolStatus
import dev.flowpilot.core.home.HomeState
import dev.flowpilot.core.home.RowStatus
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class MiscTest {
    @Test fun parsesPairingLinks() {
        val p = PairingLink.parse("http://100.64.1.2:49374/auth/connect/ABCD-1234")!!
        assertEquals("http://100.64.1.2:49374", p.baseUrl)
        assertEquals("ABCD-1234", p.code)
        val tail = PairingLink.parse("https://box.tail.ts.net/oc/auth/connect/xyz")!!
        assertEquals("https://box.tail.ts.net/oc", tail.baseUrl)
        assertNull(PairingLink.parse("https://example.com/other"))
        assertNull(PairingLink.parse("not a url"))
    }

    @Test fun describesTools() {
        fun tool(name: String, input: String, status: ToolStatus = ToolStatus.Completed, meta: String? = null) =
            Part.Tool("1", name, status, input = OpenCodeJson.parseToJsonElement(input) as JsonObject,
                metadata = meta?.let { OpenCodeJson.parseToJsonElement(it) as JsonObject })
        val read = ToolDescriber.describe(tool("read", """{"path":"src/a.kt"}"""))
        assertEquals("Read", read.verb); assertEquals("src/a.kt", read.target)
        assertEquals("Running", ToolDescriber.describe(tool("shell", """{"command":"npm test"}""", ToolStatus.Running)).verb)
        val edit = ToolDescriber.describe(tool("opencode.tool.edit", """{"path":"a.kt"}""", meta = """{"files":[{"file":"a.kt","additions":4,"deletions":2}]}"""))
        assertEquals(4, edit.additions); assertEquals(2, edit.deletions)
        assertEquals("7 matches", ToolDescriber.describe(tool("grep", """{"pattern":"x"}""", meta = """{"matches":7}""")).meta)
        val summary = ToolDescriber.summary(listOf(tool("read", "{}"), tool("glob", "{}"), tool("edit", "{}"), tool("shell", "{}"), tool("shell", "{}")))
        assertEquals("Explored 2 files, edited 1 file, ran 2 commands", summary)
    }

    @Test fun parsesPartialToolInput() {
        val o = ToolDescriber.parsePartial("""{"command":"npm te""")!!
        assertEquals("\"npm te\"", o["command"].toString())
    }

    @Test fun formatsNumbers() {
        assertEquals("12.4k", Format.tokens(12_400.0))
        assertEquals("812", Format.tokens(812.0))
        assertEquals("$0.42", Format.cost(0.4234))
        assertEquals("0:42", Format.duration(42_000))
        assertEquals("12m", Format.duration(12 * 60_000L))
        assertEquals("200k", Format.context(200_000))
        val now = 1_790_000_000_000L
        assertEquals("now", Format.relative(now - 5_000, now, ZoneOffset.UTC))
        assertEquals("2m", Format.relative(now - 120_000, now, ZoneOffset.UTC))
    }

    @Test fun repoUrls() {
        assertEquals("https://github.com/pingdotgg/t3code.git", ProjectOps.normalizeRepoUrl("pingdotgg/t3code"))
        assertEquals("t3code", ProjectOps.repoName("https://github.com/pingdotgg/t3code.git"))
        assertEquals("repo", ProjectOps.repoName("git@github.com:me/repo.git"))
        assertEquals(62, ProjectOps.percentOf("Receiving objects:  62% (620/1000)"))
    }

    @Test fun homeTracksLiveStatus() {
        val s = Session(id = "s1", projectID = "p1", title = "Fix", location = Location("/code/app"))
        var h = HomeState().withPage(listOf(s), null, replace = true)
        fun e(type: String, data: String) = ServerEvent.parse("""{"type":"$type","created":5,"data":$data}""")!!
        h = h.reduce(e("session.execution.started", """{"sessionID":"s1"}"""))
        assertEquals(RowStatus.Working, h.row(h.sessions["s1"]!!, emptySet(), null).status)
        h = h.reduce(e("session.tool.called", """{"sessionID":"s1","id":"t","name":"read","input":{"path":"a.kt"}}"""))
        assertEquals("Reading a.kt…", h.row(h.sessions["s1"]!!, emptySet(), null).supporting)
        h = h.reduce(e("permission.asked", """{"id":"per_1","sessionID":"s1","action":"shell","resources":[]}"""))
        assertEquals("Needs you", h.groups(emptySet(), null).first().label)
        h = h.reduce(e("permission.replied", """{"sessionID":"s1","requestID":"per_1","reply":"once"}"""))
        h = h.reduce(e("session.execution.succeeded", """{"sessionID":"s1"}"""))
        assertEquals(RowStatus.Unread, h.row(h.sessions["s1"]!!, emptySet(), null).status)
    }
}
