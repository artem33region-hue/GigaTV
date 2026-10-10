package com.artem.gigatv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPage(
    val page: Int = 1,
    val results: List<TmdbMovie> = emptyList()
)

@Serializable
data class TmdbMovie(
    val id: Int,
    val title: String = "",
    // Поля сериалов (/tv): у них name и first_air_date вместо title и release_date
    @SerialName("name") val tvName: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList()
) {
    val anyTitle: String get() = title.ifBlank { tvName }.ifBlank { originalTitle }
    val year: String get() = releaseDate.ifBlank { firstAirDate }.take(4)
}

@Serializable
data class TmdbGenre(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
data class TmdbVideos(
    val results: List<TmdbVideo> = emptyList()
)

@Serializable
data class TmdbVideo(
    val key: String = "",
    val site: String = "",
    val type: String = ""
)
