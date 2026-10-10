package com.artem.gigatv.data.channels

import com.artem.gigatv.data.history.WatchItem

internal data class WatchNextRow(val id: Long, val url: String, val hidden: Boolean)
internal data class WatchNextPlan(
    val hiddenUrls: Set<String>,
    val deleteIds: List<Long>,
    val upserts: List<Pair<Long?, WatchItem>>
)

/** Retain stable row IDs and remember dismissals even after an item leaves history. */
internal fun planWatchNext(
    rows: List<WatchNextRow>,
    items: List<WatchItem>,
    hiddenUrls: Set<String>,
    maxItems: Int = 12
): WatchNextPlan {
    val hidden = hiddenUrls + rows.filter { it.hidden }.map { it.url }
    val existing = rows.groupBy { it.url }
    val upserts = items.distinctBy { it.url }.filterNot { it.url in hidden }.take(maxItems)
        .map { item -> existing[item.url]?.firstOrNull()?.id to item }
    val retained = upserts.mapNotNull { it.first }.toSet()
    return WatchNextPlan(hidden, rows.filterNot { it.id in retained }.map { it.id }, upserts)
}
