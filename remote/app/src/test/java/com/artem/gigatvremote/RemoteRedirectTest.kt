package com.artem.gigatvremote

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RemoteRedirectTest {
    @Test fun doesNotForwardPairingTokenToRedirectTarget() {
        MockWebServer().use { tv ->
            MockWebServer().use { target ->
                tv.start()
                target.start()
                tv.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.url("/")))
                target.enqueue(MockResponse().setResponseCode(503))
                val client = remoteHttpClient()
                val failed = CountDownLatch(1)
                val socket = client.newWebSocket(Request.Builder().url(tv.url("/"))
                    .header("X-GigaTV-Token", "a".repeat(64)).build(), object : WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        failed.countDown()
                    }
                })
                try {
                    assertTrue(failed.await(3, TimeUnit.SECONDS))
                    assertEquals("a".repeat(64), tv.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("X-GigaTV-Token"))
                    assertNull(target.takeRequest(300, TimeUnit.MILLISECONDS))
                } finally {
                    socket.cancel()
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdownNow()
                }
            }
        }
    }
}
