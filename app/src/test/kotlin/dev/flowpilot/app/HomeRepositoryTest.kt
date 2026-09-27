package dev.flowpilot.app

import dev.flowpilot.app.data.*
import dev.flowpilot.core.api.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HomeRepositoryTest {
    private class Memory : HomeStorage {
        var saved: HomeSnapshot? = null
        override suspend fun readHome(server: String) = saved
        override suspend fun writeHome(server: String, snapshot: HomeSnapshot) { saved = snapshot }
    }
    @Test fun liveRenameWinsOverDelayedHomeSnapshot() = runBlocking {
        MockWebServer().use { server ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path!!.startsWith("/api/session?")) {
                        entered.countDown(); release.await(5, TimeUnit.SECONDS)
                        return MockResponse().setBody("""{"data":[{"id":"s","projectID":"p","title":"old","location":{"directory":"/p"}}],"cursor":{}}""")
                    }
                    if (request.path == "/api/project") return MockResponse().setBody("[]")
                    return MockResponse().setBody("""{"data":{}}""")
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val client = OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test"))
                val repo = HomeRepository("server", client, Catalog(client), Memory(), scope)
                repo.setEnabled(true)
                withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
                repo.accept(ServerEvent.parse("""{"type":"session.renamed","data":{"sessionID":"s","title":"new"}}""")!!)
                release.countDown()
                withTimeout(5_000) { while (repo.loading.value || repo.refreshing.value) delay(10) }
                assertEquals("new", repo.state.value.sessions["s"]?.title)
            } finally { release.countDown(); scope.cancel() }
        }
    }
}
