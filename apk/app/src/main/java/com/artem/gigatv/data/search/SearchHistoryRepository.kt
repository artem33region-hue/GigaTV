package com.artem.gigatv.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

/**
 * Недавние поисковые запросы, свежие сверху. Хранятся списком, а не набором:
 * порядок и есть смысл истории.
 */
class SearchHistoryRepository(private val context: Context) {

    private val queriesKey = stringPreferencesKey("queries")
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val MAX_ITEMS = 20
        // Столько же требует поиск, чтобы вообще начать искать.
        const val MIN_LENGTH = 2
    }

    private fun decode(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    val queries: Flow<List<String>> = context.searchHistoryStore.data.map { decode(it[queriesKey]) }

    /** Повторный запрос не плодит дубль, а всплывает наверх. */
    suspend fun add(query: String) {
        val fresh = query.trim()
        if (fresh.length < MIN_LENGTH) return
        context.searchHistoryStore.edit { prefs ->
            val rest = decode(prefs[queriesKey]).filterNot { it.equals(fresh, ignoreCase = true) }
            prefs[queriesKey] = json.encodeToString((listOf(fresh) + rest).take(MAX_ITEMS))
        }
    }

    suspend fun remove(query: String) {
        context.searchHistoryStore.edit { prefs ->
            prefs[queriesKey] = json.encodeToString(
                decode(prefs[queriesKey]).filterNot { it.equals(query, ignoreCase = true) }
            )
        }
    }

    suspend fun clear() {
        context.searchHistoryStore.edit { prefs -> prefs[queriesKey] = "[]" }
    }
}
