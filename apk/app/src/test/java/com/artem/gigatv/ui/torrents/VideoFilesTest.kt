package com.artem.gigatv.ui.torrents

import com.artem.gigatv.data.torrserve.TorrFile
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFilesTest {
    @Test fun detectsEpisodeNamesAndSeasonFolders() {
        assertEquals(2 to 10, episodeInfo(TorrFile(path = "Show.S02E10.mkv")))
        assertEquals(3 to 7, episodeInfo(TorrFile(path = "Show.3x07.avi")))
        assertEquals(4 to 2, episodeInfo(TorrFile(path = "Show/Season 04/E02.mkv")))
        assertEquals(2 to 5, episodeInfo(TorrFile(path = "Show\\2 сезон\\5 серия.mkv")))
        assertEquals(null to null, episodeInfo(TorrFile(path = "Movie.mkv")))
    }

    @Test fun sortsNumbersNaturallyInsteadOfBySize() {
        val files = listOf("Episode 10.mkv", "Episode 2.mkv", "Episode 1.mkv").map { TorrFile(path = it) }
        assertEquals(listOf("Episode 1.mkv", "Episode 2.mkv", "Episode 10.mkv"),
            files.sortedBy(::naturalFileKey).map { it.fileName })
    }
}
