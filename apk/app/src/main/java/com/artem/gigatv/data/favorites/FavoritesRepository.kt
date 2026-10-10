package com.artem.gigatv.data.favorites

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.favoritesStore by preferencesDataStore(name = "favorites")

/**
 * Единица избранного. Для фильмов из каталога хранится источник и id,
 * для локальных элементов плейлистов — прямая ссылка (она же ключ).
 */
@Serializable
data class FavoriteItem(
    val source: String,
    val id: Int = 0,
    val title: String,
    val series: Boolean = false,
    val poster: String? = null,
    val year: String = "",
    val url: String? = null,
    val description: String? = null
) {
    // series входит в ключ: у TMDB id фильмов и сериалов пересекаются.
    val key: String
        get() = if (source == SOURCE_LOCAL) "local:$url"
        else "$source:${if (series) "series" else "movie"}:$id"

    companion object {
        const val SOURCE_LOCAL = "local"
    }
}

class FavoritesRepository(private val context: Context) {

    private val itemsKey = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }

    val favorites: Flow<List<FavoriteItem>> = context.favoritesStore.data.map { prefs ->
        prefs[itemsKey]?.let { raw ->
            runCatching { json.decodeFromString<List<FavoriteItem>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun toggle(item: FavoriteItem) {
        context.favoritesStore.edit { prefs ->
            val current = prefs[itemsKey]?.let { raw ->
                runCatching { json.decodeFromString<List<FavoriteItem>>(raw) }.getOrNull()
            } ?: emptyList()
            val updated =
                if (current.any { it.key == item.key }) current.filterNot { it.key == item.key }
                else current + item
            prefs[itemsKey] = json.encodeToString(updated)
        }
    }
}
