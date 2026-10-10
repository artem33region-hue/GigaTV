package com.artem.gigatv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackNamesTest {
    @Test fun retainsVoiceStudioInsteadOfOnlyLanguage() {
        assertEquals("LostFilm · Русский", readableTrackName("LostFilm", "ru", "Дорожка 1"))
        assertEquals("Amedia · Русский", readableTrackName(" Amedia ", "rus", "Дорожка 2"))
    }
    @Test fun distinguishesUnnamedTracksWithoutInventingStudio() {
        assertEquals("Русский · Дорожка 2", readableTrackName(null, "ru", "Дорожка 2"))
        assertEquals("Дорожка 3", readableTrackName(" ", "und", "Дорожка 3"))
    }
}
