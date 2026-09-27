package dev.flowpilot.core

import dev.flowpilot.core.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TransportTest {
    @Test fun unauthorizedStreamStopsRetrying() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            val client = OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test"))
            val signals = withTimeout(5_000) { client.events().toList() }
            assertTrue((signals.last() as StreamSignal.Disconnected).error is ApiException.Unauthorized)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun streamOverflowIsExplicitAndReaderDoesNotBlock() = runBlocking {
        MockWebServer().use { server ->
            val burst = buildString {
                repeat(5000) { append("data: {\"type\":\"test\",\"data\":{}}\n\n") }
            }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(burst))
            val client = OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test"))
            val failure = withTimeout(10_000) {
                client.events().onEach {
                    if (it == StreamSignal.Connected) {
                        // Hold the consumer until the reader exits. Filling the queue must cancel it,
                        // not wait for this collector; no assumption about machine speed is needed.
                        while (client.streamingHttp.dispatcher.runningCallsCount() > 0) delay(10)
                    }
                }.filterIsInstance<StreamSignal.Disconnected>().first()
            }
            assertTrue(failure.error is StreamOverflowException)
        }
    }

    @Test fun cancellationAfterHeadersCancelsBodyRead() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1000)).throttleBody(1, 1, TimeUnit.SECONDS))
            val client = OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test"))
            val read = launch { client.getRaw("slow") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            delay(50)
            withTimeout(1_000) { read.cancelAndJoin() }
            withTimeout(2_000) { while (client.http.dispatcher.runningCallsCount() != 0) delay(10) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun malformedAndUnknownFramesDoNotBreakStream() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: bad-json\n\ndata: {\"type\":\"future.event\",\"data\":{}}\n\n"))
            val client = OpenCodeClient(ServerEndpoint(server.url("/").toString(), "test"))
            val event = withTimeout(5_000) { client.events().filterIsInstance<StreamSignal.Event>().first() }
            assertEquals("future.event", event.event.type)
        }
    }
}
