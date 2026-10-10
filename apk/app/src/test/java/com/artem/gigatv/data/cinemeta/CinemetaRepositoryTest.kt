package com.artem.gigatv.data.cinemeta

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class CinemetaRepositoryTest {
    @Test fun seriesResultsSurviveMovieEndpointFailure() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path!!.contains("/movie/")) MockResponse().setResponseCode(503)
                    else MockResponse().setBody("""{"metas":[{"id":"tt0944947","name":"Game of Thrones","type":"series"}]}""")
            }
            server.start()
            val results = CinemetaRepository(server.url("/").toString(), reportFailure = { _, _ -> }).search("игра престолов")
            assertEquals("Game of Thrones", results.single().title)
            assertTrue(results.single().isSeries)
            val path = server.takeRequest().path!!
            assertTrue(path.contains("%20"))
            assertFalse(path.contains("+"))
        }
    }

    @Test fun failuresAreNotReportedAsEmptyResults() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path!!.contains("/movie/")) MockResponse().setResponseCode(503)
                    else MockResponse().setBody("""{"metas":[]}""")
            }
            server.start()
            try {
                CinemetaRepository(server.url("/").toString(), reportFailure = { _, _ -> }).search("missing")
                fail("Expected a server error")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("503"))
            }
        }
    }

    @Test fun temporaryServerFailureIsRetriedOnce() = runBlocking {
        MockWebServer().use { server ->
            val seriesRequests = java.util.concurrent.atomic.AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path!!.contains("/movie/")) return MockResponse().setBody("""{"metas":[]}""")
                    if (seriesRequests.incrementAndGet() == 1) return MockResponse().setResponseCode(520)
                    return MockResponse().setBody("""{"metas":[{"id":"tt0944947","name":"Game of Thrones","type":"series"}]}""")
                }
            }
            server.start()
            val results = CinemetaRepository(server.url("/").toString(), reportFailure = { _, _ -> }).search("игра престолов")
            assertTrue(results.single().isSeries)
            assertEquals(2, seriesRequests.get())
        }
    }
}
