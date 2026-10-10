package com.artem.gigatv.data.jacred

import kotlinx.serialization.Serializable

/**
 * Раздача в ответе JacRed. Поля соответствуют реальному ответу
 * `/api/v1.0/torrents`: `voices`, `seasons` и `types` — массивы,
 * а год называется `relased` (опечатка на стороне API, не наша).
 */
@Serializable
data class JacredTorrent(
    val tracker: String = "",
    val url: String = "",
    val title: String = "",
    val size: Long = 0,
    val sizeName: String = "",
    val createTime: String = "",
    val sid: Int = 0,
    val pir: Int = 0,
    val magnet: String = "",
    val name: String = "",
    val originalname: String = "",
    val relased: Int = 0,
    val videotype: String = "",
    val quality: Int = 0,
    val voices: List<String> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val types: List<String> = emptyList()
) {
    // Companion обязан оставаться непубличным только внутри: сам объект должен
    // быть доступен снаружи, иначе decodeFromString<List<JacredTorrent>> падает
    // в рантайме с IllegalAccessError — плагин сериализации подставляет прямое
    // обращение к JacredTorrent.Companion в класс, где вызван разбор JSON.
    companion object {
        // "anime" в обоих наборах: аниме-фильмы и аниме-сериалы в JacRed
        // помечены одинаково, отличить их по types нельзя.
        private val SERIES_TYPES = setOf("serial", "multserial", "tvshow", "docuserial", "anime")
        private val MOVIE_TYPES = setOf("movie", "multfilm", "docmovie", "anime")
    }

    /**
     * Подходит ли раздача под искомую категорию. Пустые и незнакомые `types`
     * не отбрасываем — режем только явно противоположную категорию.
     */
    fun matchesCategory(series: Boolean): Boolean {
        if (types.isEmpty()) return true
        val wanted = if (series) SERIES_TYPES else MOVIE_TYPES
        val opposite = if (series) MOVIE_TYPES else SERIES_TYPES
        return types.any { it in wanted } || types.none { it in opposite }
    }

    /** Короткая строка под названием: качество, размер, сиды, озвучки. */
    val summary: String
        get() = buildList {
            if (quality > 0) add("${quality}p")
            if (videotype.isNotBlank() && videotype != "sdr") add(videotype.uppercase())
            if (seasons.isNotEmpty()) add("сезон " + seasons.joinToString(", "))
            if (sizeName.isNotBlank()) add(sizeName)
            add("↑$sid / ↓$pir")
            if (tracker.isNotBlank()) add(tracker)
            if (voices.isNotEmpty()) add(voices.take(3).joinToString(", "))
        }.joinToString(" · ")
}
