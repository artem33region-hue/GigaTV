package com.artem.gigatv.data.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class UpdateValidationTest {
    @Test fun acceptsSmallManifestAndRejectsOversizedChunkedBody() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient()
            try {
                server.enqueue(MockResponse().setBody("""{"versionCode":18}"""))
                fun call() = client.newCall(Request.Builder().url(server.url("/")).build())
                assertEquals("""{"versionCode":18}""", call().readUpdateManifest())
                server.enqueue(MockResponse().setChunkedBody("x".repeat(65 * 1024), 1024))
                assertTrue(runCatching { call().readUpdateManifest() }.isFailure)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }

    @Test fun cancellationClosesAnInFlightManifestCall() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(2, TimeUnit.SECONDS))
            val client = OkHttpClient()
            val call = client.newCall(Request.Builder().url(server.url("/")).build())
            try {
                val job = async(Dispatchers.Default) { call.readUpdateManifest() }
                assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue(call.isCanceled())
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }

    @Test fun rejectsApkThatDoesNotMatchAnnouncedVersion() {
        validateUpdateVersion(18, 17, 18)
        assertThrows(IllegalStateException::class.java) { validateUpdateVersion(18, 17, 19) }
        assertThrows(IllegalStateException::class.java) { validateUpdateVersion(17, 17, 17) }
    }
}
