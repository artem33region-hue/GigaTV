package com.artem.gigatv.data.catalog

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedRow(val title: String, val movies: List<CatalogMovie>)

/**
 * Дисковый кэш рядов главного экрана: при запуске карточки показываются сразу
 * из последнего успешного ответа, а свежие данные подменяют их в фоне.
 */
class CatalogCache(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.cacheDir, "catalog_rows.json")

    fun read(): List<Pair<String, List<CatalogMovie>>> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<CachedRow>>(file.readText()).map { it.title to it.movies }
    }.getOrDefault(emptyList())

    fun write(rows: List<Pair<String, List<CatalogMovie>>>) {
        runCatching {
            file.writeText(
                json.encodeToString(rows.map { CachedRow(it.first, it.second) })
            )
        }
    }
}
