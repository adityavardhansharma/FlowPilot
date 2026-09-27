package dev.flowpilot.app

import dev.flowpilot.app.data.Pending
import dev.flowpilot.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PendingTest {
    private val ask = """{"id":"p","sessionID":"s","action":"shell","resources":[]}"""
    private fun asked() = ServerEvent.parse("""{"type":"permission.asked","data":$ask}""")!!
    private suspend fun waitFor(test: () -> Boolean) = withTimeout(5_000) { while (!test()) delay(10) }

    @Test fun failedFolderRefreshRetainsPendingPermission() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when {
                    request.path!!.startsWith("/api/permission") -> MockResponse().setResponseCode(500)
                    request.path!!.startsWith("/api/session/s") -> MockResponse().setBody("""{"data":{"id":"s","projectID":"p","location":{"directory":"/p"}}}""")
                    else -> MockResponse().setBody("""{"data":[]}""")
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val events = MutableSharedFlow<ServerEvent>()
                val pending = Pending(OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test")), events, MutableStateFlow(0), scope, { listOf(Project("p", "/p")) })
                waitFor { events.subscriptionCount.value > 0 }
                events.emit(asked()); waitFor { pending.asks.value.size == 1 }
                pending.refresh()
                assertEquals("p", pending.asks.value.single().id)
                assertNotNull(pending.error.value)
            } finally { scope.cancel() }
        }
    }

    @Test fun replyDuringSnapshotCannotResurrectApproval() = runBlocking {
        MockWebServer().use { server ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path!!.startsWith("/api/permission")) {
                        entered.countDown(); release.await(5, TimeUnit.SECONDS)
                        return MockResponse().setBody("""{"data":[$ask]}""")
                    }
                    if (request.path!!.startsWith("/api/session/s")) return MockResponse().setBody("""{"data":{"id":"s","projectID":"p","location":{"directory":"/p"}}}""")
                    return MockResponse().setBody("""{"data":[]}""")
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val events = MutableSharedFlow<ServerEvent>()
                val pending = Pending(OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test")), events, MutableStateFlow(0), scope, { listOf(Project("p", "/p")) })
                waitFor { events.subscriptionCount.value > 0 }
                events.emit(asked()); waitFor { pending.asks.value.size == 1 }
                val refresh = async { pending.refresh() }
                withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
                events.emit(ServerEvent.parse("""{"type":"permission.replied","data":{"sessionID":"s","requestID":"p"}}""")!!)
                waitFor { pending.asks.value.isEmpty() }
                release.countDown(); refresh.await()
                assertTrue(pending.asks.value.isEmpty())
            } finally { release.countDown(); scope.cancel() }
        }
    }
}
