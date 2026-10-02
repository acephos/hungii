package com.hungii.prototype

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SimulatorTransportTest {
    private val transport = SimulatorTransport(OkHttpClient.Builder()
        .callTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())

    @Test fun unreachableSimulatorExplainsHowToReconnect() {
        val server = MockWebServer()
        server.start()
        val request = Request.Builder().url(server.url("/api")).build()
        server.shutdown()
        val failure = assertThrows(ApiFailure::class.java) { transport.execute(request) }
        assertEquals("HUNGII_DEMO_OFFLINE", failure.code)
        assertTrue(failure.message.contains("Tailscale"))
        assertTrue(failure.message.contains("Refresh connection"))
    }

    @Test fun successfulSimulatorResponseRemainsUsable() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"connected\":true,\"demo\":true}"))
            val data = transport.execute(Request.Builder().url(server.url("/api")).build())
            assertTrue(data.getBoolean("connected"))
            assertTrue(data.getBoolean("demo"))
        }
    }

    @Test fun gatewayErrorsKeepTheirSpecificMessage() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409).setBody(
                "{\"error\":{\"code\":\"HUNGII_CART_CHANGED\",\"message\":\"Review the updated cart.\"}}"))
            val failure = assertThrows(ApiFailure::class.java) {
                transport.execute(Request.Builder().url(server.url("/api")).build())
            }
            assertEquals("HUNGII_CART_CHANGED", failure.code)
            assertEquals("Review the updated cart.", failure.message)
        }
    }
}
