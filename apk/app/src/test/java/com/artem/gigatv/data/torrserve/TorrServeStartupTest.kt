package com.artem.gigatv.data.torrserve

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TorrServeStartupTest {
    private val stream = "http://127.0.0.1:8090/stream/episode.mkv?link=${"a".repeat(40)}&index=1&play"

    @Test fun onlyStartsForTheConfiguredLoopbackTorrentServer() {
        assertTrue(isLocalTorrentStream(stream, "http://localhost:8090"))
        assertFalse(isLocalTorrentStream(stream, "http://192.168.1.2:8090"))
        assertFalse(isLocalTorrentStream(stream, "http://127.0.0.1:8091"))
        assertFalse(isLocalTorrentStream("http://127.0.0.1:8090/movie.mp4", "http://127.0.0.1:8090"))
    }

    @Test fun restartsStoppedServerBeforeReportingReady() = runBlocking {
        val events = mutableListOf<String>()
        var running = false
        assertTrue(ensureTorrServe(true, probe = { running }, launch = {
            events += "launch"
            running = true
            true
        }, onServerStarted = { events += "return" }))
        assertEquals(listOf("launch", "return"), events)
    }

    @Test fun doesNotInterruptRemoteOrAlreadyRunningServer() = runBlocking {
        for (local in listOf(false, true)) {
            assertTrue(ensureTorrServe(local, probe = { true },
                launch = { error("Must not launch") }, onServerStarted = { error("Must not navigate") }))
        }
    }

    @Test fun timeoutOrMissingAppDoesNotPretendPlaybackIsReady() = runBlocking {
        assertFalse(ensureTorrServe(true, probe = { false }, launch = { false },
            onServerStarted = { error("Must not navigate") }))
        assertFalse(ensureTorrServe(true, probe = { false }, launch = { true },
            onServerStarted = { error("Must not navigate") }, timeoutMs = 20))
    }
}
