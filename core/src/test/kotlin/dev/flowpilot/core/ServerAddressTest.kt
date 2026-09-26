package dev.flowpilot.core

import dev.flowpilot.core.api.ServerAddress
import dev.flowpilot.core.api.ServerAddress.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerAddressTest {
    @Test fun addsSchemeAndServicePort() {
        assertEquals(Result.Ok("http://192.168.1.20:49374"), ServerAddress.normalize("192.168.1.20"))
        assertEquals(Result.Ok("http://192.168.1.20:49374"), ServerAddress.normalize(" http://192.168.1.20/ "))
        assertEquals(Result.Ok("http://my-pc.lan:49374"), ServerAddress.normalize("my-pc.lan"))
    }

    @Test fun keepsExplicitPortsAndHttps() {
        assertEquals(Result.Ok("http://192.168.1.20:4096"), ServerAddress.normalize("192.168.1.20:4096"))
        assertEquals(Result.Ok("http://192.168.1.20"), ServerAddress.normalize("http://192.168.1.20:80"))
        assertEquals(Result.Ok("https://box.tail.ts.net"), ServerAddress.normalize("https://box.tail.ts.net"))
        assertEquals(Result.Ok("https://box.tail.ts.net/oc"), ServerAddress.normalize("https://box.tail.ts.net/oc/"))
        assertEquals(Result.Ok("http://[fd00::1]:49374"), ServerAddress.normalize("http://[fd00::1]"))
        assertEquals(Result.Ok("http://[fd00::1]:5000"), ServerAddress.normalize("[fd00::1]:5000"))
    }

    @Test fun rejectsLoopbackAndJunk() {
        assertEquals(Result.Loopback, ServerAddress.normalize("localhost:49374"))
        assertEquals(Result.Loopback, ServerAddress.normalize("http://0.0.0.0:49374"))
        assertEquals(Result.Loopback, ServerAddress.normalize("127.0.0.1"))
        assertEquals(Result.Invalid, ServerAddress.normalize(""))
        assertEquals(Result.Invalid, ServerAddress.normalize("http://"))
    }
}
