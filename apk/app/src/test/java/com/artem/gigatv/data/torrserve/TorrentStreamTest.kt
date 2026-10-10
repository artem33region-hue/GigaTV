package com.artem.gigatv.data.torrserve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentStreamTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun findsTorrentInSavedPlaybackUrl() {
        assertEquals(hash, torrentStreamHash("http://127.0.0.1:8090/stream/S01E02.mkv?link=$hash&index=2&play",
            "http://127.0.0.1:8090"))
    }

    @Test fun rejectsOtherVideosAndOtherServers() {
        assertNull(torrentStreamHash("https://example.com/movie.mkv"))
        assertNull(torrentStreamHash("http://127.0.0.1:8090/stream/movie.mkv?link=invalid"))
        assertNull(torrentStreamHash("http://example.com:8090/stream/movie.mkv?link=$hash",
            "http://127.0.0.1:8090"))
    }
}
