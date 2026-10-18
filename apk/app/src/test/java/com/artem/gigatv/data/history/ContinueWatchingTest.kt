package com.artem.gigatv.data.history

import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingTest {
    @Test fun readablePlayerTitlesKeepSeriesGroupedWithExistingHistory() {
        val cards = continueWatchingCards(listOf(
            episode("Game.of.Thrones.S02E08.mkv", 1),
            episode("Game of Thrones — Сезон 2 Серия 9", 2)))
        assertEquals(1, cards.size)
        assertEquals("Game of Thrones", cards.single().title)
        assertEquals("Сезон 2 · Серия 9", cards.single().episode)
    }
    private fun episode(title: String, time: Long, finished: Boolean = false) =
        WatchItem("https://tv/$time", title, position = 20_000, duration = 100_000,
            updatedAt = time, finished = finished)

    @Test fun collapsesSeasonsAndResumesMostRecentlyWatchedEpisode() {
        val cards = continueWatchingCards(listOf(
            episode("Game.of.Thrones.S01E.03.UHD.mkv", 2),
            episode("Game.of.Thrones.S02E01.mkv", 1)))
        assertEquals(1, cards.size)
        assertEquals("Game of Thrones", cards.single().title)
        assertEquals("Сезон 1 · Серия 3", cards.single().episode)
        assertEquals("https://tv/2", cards.single().item.url)
    }

    @Test fun completedLatestEpisodeDoesNotReviveAnOlderOne() {
        assertTrue(continueWatchingCards(listOf(
            episode("Show.S01E01.mkv", 1), episode("Show.S01E02.mkv", 2, true))).isEmpty())
    }

    @Test fun keepsFilmsAndUnidentifiedShowsSeparate() {
        assertEquals(4, continueWatchingCards(listOf(
            episode("Movie A", 1), episode("Movie B", 2),
            episode("S01E01.mkv", 3), episode("S01E02.mkv", 4))).size)
    }

    @Test fun recognizesCrossAndRussianEpisodeNames() {
        assertEquals("Сезон 2 · Серия 7", episode("Show.2x07.mkv", 1).continueWatchingCard().episode)
        assertEquals("Сезон 1 · Серия 3",
            episode("Сериал Сезон 1 Серия 3.mkv", 1).continueWatchingCard().episode)
    }
}
