package com.artem.gigatv.data.playlist

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val name: String = "Плейлист",
    val items: List<PlaylistItem> = emptyList()
)

@Serializable
data class PlaylistItem(
    val title: String,
    val url: String,
    val poster: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val tmdbId: Int? = null,
    val isSeries: Boolean = false
)
