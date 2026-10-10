package com.artem.gigatv.ui.torrents

import com.artem.gigatv.data.torrserve.TorrFile
import com.artem.gigatv.data.torrserve.TorrTorrent
import org.junit.Assert.*
import org.junit.Test

class TorrentSearchContextTest {
    @Test fun restoresSearchForOlderSeriesHistory() {
        val spec = inferTorrentSearch(TorrTorrent(
            title = "Игра Престолов (Game Of Thrones) (2011-2023 Blu-Ray) UHD HDR",
            file_stats = listOf(TorrFile(path = "Game.of.Thrones.S01E02.mkv"))
        ), "poster")
        assertEquals("Игра Престолов", spec.title)
        assertEquals("2011", spec.year)
        assertTrue(spec.series)
        assertEquals("poster", spec.poster)
    }

    @Test fun retainsMovieCategory() {
        val spec = inferTorrentSearch(TorrTorrent(title = "Sintel (2010) [1080p]",
            file_stats = listOf(TorrFile(path = "Sintel.mkv"))), null)
        assertEquals("Sintel", spec.title)
        assertFalse(spec.series)
    }
}
