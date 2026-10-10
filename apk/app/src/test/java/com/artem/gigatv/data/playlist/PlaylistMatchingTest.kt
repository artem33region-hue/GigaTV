package com.artem.gigatv.data.playlist

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaylistMatchingTest {
    @Test fun movieAndSeriesWithSameIdDoNotCollide() {
        val movie = PlaylistItem("Movie", "https://example.com/movie", tmdbId = 7)
        val series = PlaylistItem("Series", "https://example.com/series", tmdbId = 7, isSeries = true)
        val lists = listOf(Playlist(items = listOf(movie, series)))
        assertEquals(series, findPlaylistItem(lists, 7, "Series", true))
        assertEquals(movie, findPlaylistItem(lists, 7, "Movie", false))
    }

    @Test fun titleCannotMatchAnItemOfTheWrongType() {
        val lists = listOf(Playlist(items = listOf(PlaylistItem("Same", "https://example.com/movie", tmdbId = 7))))
        assertNull(findPlaylistItem(lists, 7, "Same", true))
        assertNull(findPlaylistItem(lists, null, "Same", true))
    }

    @Test fun oldPlaylistsRemainMoviesByDefault() {
        val item = Json.decodeFromString(PlaylistItem.serializer(), """{"title":"Old","url":"https://example.com/v"}""")
        assertFalse(item.isSeries)
    }
}
