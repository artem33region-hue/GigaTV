package com.artem.gigatv.data.torrserve

import kotlinx.serialization.Serializable

/** Тело запроса к TorrServe: /torrents (action = add | get | rem). */
@Serializable
data class TorrRequest(
    val action: String,
    val link: String = "",
    val hash: String = "",
    val title: String = "",
    val poster: String = "",
    val save_to_db: Boolean = false
)

/** Ответ TorrServe с описанием раздачи и списком файлов. */
@Serializable
data class TorrTorrent(
    val hash: String = "",
    val title: String = "",
    val name: String = "",
    val poster: String = "",
    val file_stats: List<TorrFile>? = null
) {
    val displayName: String get() = title.ifBlank { name }.ifBlank { "Раздача" }
}

@Serializable
data class TorrFile(
    val id: Int = 0,
    val path: String = "",
    val length: Long = 0
) {
    val fileName: String get() = path.substringAfterLast('/').substringAfterLast('\\')
    val ext: String get() = fileName.substringAfterLast('.', "").lowercase()
}

val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "webm", "m4v", "mov", "ts", "flv", "mpg", "mpeg", "wmv", "3gp", "ogv"
)
