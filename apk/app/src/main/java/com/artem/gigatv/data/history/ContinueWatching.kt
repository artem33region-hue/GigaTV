package com.artem.gigatv.data.history

internal data class ContinueWatchingCard(
    val item: WatchItem,
    val key: String,
    val title: String,
    val episode: String?
)

private val episodeMarker = Regex(
    "(?iu)(?:^|[\\s._/\\\\-])(?:s(\\d{1,2})[ ._-]*e[ ._-]*(\\d{1,3})|(\\d{1,2})x(\\d{1,3})|(?:сезон|season)[ ._-]*(\\d{1,2})[ ._/-]*(?:серия|episode)[ ._-]*(\\d{1,3}))"
)

internal fun WatchItem.continueWatchingCard(): ContinueWatchingCard {
    val match = episodeMarker.find(title)
        ?: return ContinueWatchingCard(this, "url:$url", title, null)
    val numbers = match.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toInt() }
    val name = title.substring(0, match.range.first).replace(Regex("[._]+"), " ").trim().trimEnd('—', '-').trimEnd()
    // An episode number alone does not identify a show; do not merge unrelated files.
    if (name.isBlank()) return ContinueWatchingCard(this, "url:$url", title,
        "Сезон ${numbers[0]} · Серия ${numbers[1]}")
    return ContinueWatchingCard(this, "series:${name.lowercase()}", name,
        "Сезон ${numbers[0]} · Серия ${numbers[1]}")
}

/** Group before filtering: finishing the latest episode must not revive older episodes. */
internal fun continueWatchingCards(items: List<WatchItem>): List<ContinueWatchingCard> =
    items.sortedByDescending { it.updatedAt }
        .filter { it.finished || it.position >= 5_000 }
        .map { it.continueWatchingCard() }
        .distinctBy { it.key }
        .filter { !it.item.finished && it.item.position >= 5_000 && it.item.progress < 0.95f }
