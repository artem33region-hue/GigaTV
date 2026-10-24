package com.artem.gigatv.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class RemoteServerAccessTest {
    @Test fun protectsHttpAndWebSocketHandshakeUntilTvApproval() {
        val access = RemoteAccess()
        val server = RemoteControlServer(access, port = 0)
        val client = OkHttpClient()
        val token = "c".repeat(64)
        server.start()
        try {
            val url = "http://127.0.0.1:${server.listeningPort}/"
            fun get(builder: Request.Builder) = client.newCall(builder.url(url).build()).execute()
            get(Request.Builder()).use { response ->
                assertEquals(401, response.code)
                assertFalse(response.body!!.string().contains("active"))
            }
            get(Request.Builder().header("Upgrade", "websocket").header("X-GigaTV-Token", token)).use {
                assertEquals(401, it.code)
            }
            assertNotNull(access.pending.value)
            access.resolve(access.pending.value!!, true)
            get(Request.Builder().header("X-GigaTV-Token", token)).use {
                assertEquals(200, it.code)
                assertEquals("""{"active":false}""", it.body!!.string())
            }
            get(Request.Builder().header("X-GigaTV-Token", token).header("Origin", "https://example.test")).use {
                assertEquals(403, it.code)
            }
            server.revokeAll()
            get(Request.Builder().header("X-GigaTV-Token", token)).use { assertEquals(401, it.code) }
        } finally {
            server.stop()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }
}
