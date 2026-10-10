package com.artem.gigatv.data.torrserve

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TorrServeRepositoryTest {
    @Test fun metadataDeadlineIncludesSlowResponseBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"hash":"abc"}"""))
            server.enqueue(MockResponse().setBody("""{"hash":"abc","file_stats":[]}""")
                .setBodyDelay(3, TimeUnit.SECONDS))
            server.start()
            val repository = TorrServeRepository(flowOf(server.url("/").toString()), metadataTimeoutMs = 1000)
            val start = System.nanoTime()
            assertEquals(TorrServeRepository.AddResult.NoMetadata, repository.add("magnet:?xt=test"))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 4000)
        }
    }

    @Test fun cancellationInterruptsBodyRead() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"hash":"abc"}"""))
            server.enqueue(MockResponse().setBody("""{"hash":"abc","file_stats":[]}""")
                .setBodyDelay(3, TimeUnit.SECONDS))
            server.start()
            val repository = TorrServeRepository(flowOf(server.url("/").toString()))
            val request = async(kotlinx.coroutines.Dispatchers.Default) { repository.add("magnet:?xt=test") }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            val start = System.nanoTime()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000)
        }
    }

    @Test fun readyTorrentKeepsItsHashAndFiles() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"hash":"abc","file_stats":[{"id":1,"path":"film.mkv","length":1024}]}"""))
            server.start()
            val repository = TorrServeRepository(flowOf(server.url("/").toString()))
            val result = repository.add("magnet:?xt=test") as TorrServeRepository.AddResult.Ready
            assertEquals("abc", result.torrent.hash)
            assertEquals("film.mkv", result.torrent.file_stats!!.single().fileName)
            assertEquals(1, server.requestCount)
        }
    }
}
