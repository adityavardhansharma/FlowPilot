package dev.flowpilot.core

import com.sun.net.httpserver.HttpServer
import dev.flowpilot.core.api.OpenCodeClient
import dev.flowpilot.core.api.ServerEndpoint
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import javax.net.SocketFactory

/**
 * Android throws NetworkOnMainThreadException when a socket is read on the main thread, and view models call the
 * client from there. This stands in for that check: reading the socket on the "main" thread fails the test.
 */
class MainThreadIoTest {
    private lateinit var server: HttpServer
    private val main = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }

    // Big enough that OkHttp can't have it buffered after the headers, like the real model list.
    private val models = buildString {
        append("""{"data":[""")
        repeat(3000) { i ->
            if (i > 0) append(',')
            append("""{"id":"model-$i","providerID":"provider","name":"Model $i with a fairly long display name"}""")
        }
        append("]}")
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/api/model") { ex ->
            val bytes = models.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { out -> bytes.toList().chunked(8192).forEach { out.write(it.toByteArray()); out.flush() } }
        }
        server.start()
    }

    @After fun stop() {
        server.stop(0)
        main.shutdown()
    }

    @Test fun neverReadsTheSocketOnTheCallersThread() = runBlocking {
        val guarded = OkHttpClient.Builder().socketFactory(MainGuardSocketFactory()).build()
        val client = OpenCodeClient(ServerEndpoint("http://127.0.0.1:${server.address.port}", "pw"), guarded)
        val list = withContext(main.asCoroutineDispatcher()) { client.models() }
        assertEquals(3000, list.size)
    }

    private class MainGuardSocketFactory : SocketFactory() {
        override fun createSocket(): Socket = GuardedSocket()
        override fun createSocket(host: String?, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: InetAddress?, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = throw UnsupportedOperationException()
    }

    private class GuardedSocket : Socket() {
        override fun getInputStream(): InputStream = object : FilterInputStream(super.getInputStream()) {
            // Coroutine debug mode appends " @coroutine#1" to the thread name.
            private fun check() = check(!Thread.currentThread().name.startsWith("fake-main")) { "socket read on the main thread" }
            override fun read(): Int { check(); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { check(); return super.read(b, off, len) }
        }
    }
}
