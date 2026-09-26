package dev.flowpilot.core

import dev.flowpilot.core.api.CreateSessionBody
import dev.flowpilot.core.api.Location
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.ProjectOps
import dev.flowpilot.core.api.PromptBody
import dev.flowpilot.core.api.ServerEndpoint
import dev.flowpilot.core.api.StreamSignal
import dev.flowpilot.core.api.events
import dev.flowpilot.core.chat.ChatReducer
import dev.flowpilot.core.chat.ChatState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Files

/**
 * Runs against a real OpenCode 2 server when OPENCODE_URL and OPENCODE_PASSWORD are set; skipped otherwise.
 * `opencode service start`, then redeem a code from `opencode pair` for a token and run
 * `OPENCODE_URL=http://127.0.0.1:49374 OPENCODE_PASSWORD=<token> ./gradlew :core:test`.
 */
class LiveServerTest {
    private val url = System.getenv("OPENCODE_URL")
    private val pw = System.getenv("OPENCODE_PASSWORD")

    @Test fun endToEnd() = runBlocking {
        assumeTrue(url != null && pw != null)
        val client = OpenCodeClient(ServerEndpoint(url!!, pw!!))
        assertTrue(client.info().version.startsWith("2."))
        val ops = ProjectOps(client)
        val home = ops.homeDir()
        assertTrue(home.startsWith("/"))
        val base = Files.createTempDirectory("fp-live").toString()
        val dir = ops.createFolder(base, "My App", gitInit = true)
        assertTrue(client.listDir(dir).any { it.name == ".git" })

        val models = client.models(dir)
        assertTrue(models.isNotEmpty())
        val agents = client.agents(dir)
        assertTrue(agents.any { it.selectable })

        val session = client.createSession(CreateSessionBody(location = Location(dir)))
        val page = client.sessions()
        assertTrue(page.data.any { it.id == session.id })
        client.rename(session.id, "Live test")
        assertEquals("Live test", client.session(session.id).title)

        // Stream a turn. The model call may fail offline; the reducer must still settle.
        var state = ChatState(session.id)
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = launch {
            client.events().filterIsInstance<StreamSignal.Event>().collect { s ->
                state = ChatReducer.reduce(state, s.event)
                if (s.event.sessionID == session.id && s.event.type.startsWith("session.execution.") && s.event.type != "session.execution.started") done.complete(Unit)
            }
        }
        kotlinx.coroutines.delay(500)
        client.prompt(session.id, PromptBody(text = "Say hi"))
        withTimeout(60_000) { done.await() }
        job.cancel()
        assertFalse(state.running)
        assertTrue(state.entries.isNotEmpty())

        val (messages, _) = client.messages(session.id)
        assertTrue(ChatReducer.entriesFrom(messages).isNotEmpty())
        client.deleteSession(session.id)
    }

    /** Every other route the app calls, plus the pairing flow the QR code drives. */
    @Test fun homeInboxAndPairing() = runBlocking {
        assumeTrue(url != null && pw != null)
        val client = OpenCodeClient(ServerEndpoint(url!!, pw!!))
        val dir = Files.createTempDirectory("fp-live-home").toString()
        val session = client.createSession(CreateSessionBody(location = Location(dir)))
        try {
            assertTrue(client.projects().any { it.id == session.projectID })
            client.activeSessions()
            client.defaultModel(dir)
            assertTrue(client.sessions(search = "zzz-no-such-chat").data.isEmpty())
            client.permissions(session.id)
            client.forms(session.id)
            client.inbox(session.id)
            client.pendingPermissions(dir)
            client.pendingForms(dir)
            client.markViewed(session.id, System.currentTimeMillis())

            // What `opencode pair` does, then what the phone does with the scanned link.
            val raw = client.send(
                okhttp3.Request.Builder().url(client.url("api/pair")).post(ByteArray(0).toRequestBody(null)).build(),
            ).use { it.body.string() }
            val code = dev.flowpilot.core.api.OpenCodeJson.parseToJsonElement(raw).jsonObject["code"]!!.jsonPrimitive.content
            val link = "${url.trimEnd('/')}/auth/connect/$code"
            val paired = OpenCodeClient(OpenCodeClient.redeemPairingLink(link))
            assertTrue(paired.info().version.startsWith("2."))
            // Links are single-use.
            val reused = runCatching { OpenCodeClient.redeemPairingLink(link) }.exceptionOrNull()
            assertTrue(reused is dev.flowpilot.core.api.ApiException.Http && reused.code == 401)
        } finally {
            client.deleteSession(session.id)
        }
    }
}
