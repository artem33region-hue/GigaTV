package com.artem.gigatv.ui.torrents

import android.content.Context
import com.artem.gigatv.data.torrserve.TorrTorrent
import com.artem.gigatv.ui.details.TorrentSearchSpec
import kotlinx.serialization.json.Json

internal fun Context.saveTorrentSearch(hash: String, spec: TorrentSearchSpec) {
    getSharedPreferences("torrent_search_context", Context.MODE_PRIVATE).edit()
        .putString(hash, Json.encodeToString(TorrentSearchSpec.serializer(), spec)).apply()
}

internal fun Context.torrentSearch(torrent: TorrTorrent, poster: String?): TorrentSearchSpec {
    val saved = getSharedPreferences("torrent_search_context", Context.MODE_PRIVATE).getString(torrent.hash, null)
    return saved?.let {
        runCatching { Json.decodeFromString(TorrentSearchSpec.serializer(), it) }.getOrNull()
    } ?: inferTorrentSearch(torrent, poster)
}

/** Older history entries have no catalog context; use the torrent's readable title. */
internal fun inferTorrentSearch(torrent: TorrTorrent, poster: String?): TorrentSearchSpec {
    val name = torrent.displayName
    val title = name.substringBefore('(').substringBefore('[').substringBefore(" / ").trim()
        .ifBlank { name }
    val year = Regex("(?:19|20)\\d{2}").find(name)?.value.orEmpty()
    val series = torrent.file_stats.orEmpty().any { episodeInfo(it).first != null || episodeInfo(it).second != null } ||
        Regex("(?i)сезон|season|серии|episodes").containsMatchIn(name)
    return TorrentSearchSpec(title, year, series, poster)
}
