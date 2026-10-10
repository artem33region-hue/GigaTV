package com.artem.gigatv.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyStore by preferencesDataStore(name = "watch_history")

@Serializable
data class WatchItem(
    val url: String,
    val title: String,
    val poster: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val updatedAt: Long = 0,
    val finished: Boolean = false
) {
    val progress: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

class WatchHistoryRepository(private val context: Context) {

    private val itemsKey = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val MAX_ITEMS = 30
        const val MIN_POSITION_MS = 5_000L    // короче 5 сек — не считаем «начатым»
        const val DONE_THRESHOLD = 0.95f      // 95%+ — фильм досмотрен
    }

    private fun decode(raw: String?): List<WatchItem> =
        raw?.let { runCatching { json.decodeFromString<List<WatchItem>>(it) }.getOrNull() } ?: emptyList()

    /** Незавершённые записи, недавние сверху — для ряда «Продолжить». */
    val inProgress: Flow<List<WatchItem>> = context.historyStore.data.map { prefs ->
        decode(prefs[itemsKey])
            .filter { !it.finished && it.position >= MIN_POSITION_MS && it.progress < DONE_THRESHOLD }
            .sortedByDescending { it.updatedAt }
    }

    internal val continueWatching: Flow<List<ContinueWatchingCard>> = context.historyStore.data.map { prefs ->
        continueWatchingCards(decode(prefs[itemsKey]))
    }

    internal suspend fun removeFromContinueWatching(key: String) {
        context.historyStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(decode(prefs[itemsKey]).filterNot {
                it.continueWatchingCard().key == key
            })
        }
    }

    suspend fun positionFor(url: String): Long =
        inProgress.first().firstOrNull { it.url == url }?.position ?: 0L

    suspend fun save(
        url: String,
        title: String,
        poster: String?,
        position: Long,
        duration: Long,
        now: Long
    ) {
        if (url.isBlank() || duration <= 0) return
        val finished = position >= duration - MIN_POSITION_MS ||
            (position.toFloat() / duration) >= DONE_THRESHOLD
        context.historyStore.edit { prefs ->
            val current = decode(prefs[itemsKey]).filterNot { it.url == url }
            val updated = (listOf(
                WatchItem(url, title, poster, position, duration, now, finished)
            ) + current).take(MAX_ITEMS)
            prefs[itemsKey] = json.encodeToString(updated)
        }
    }

    /** Полностью очищает историю просмотра. */
    suspend fun clear() {
        context.historyStore.edit { prefs -> prefs[itemsKey] = "[]" }
    }

    suspend fun remove(url: String) {
        context.historyStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(decode(prefs[itemsKey]).filterNot { it.url == url })
        }
    }
}
