package com.artem.gigatv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.artem.gigatv.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val keyTmdb = stringPreferencesKey("tmdb_api_key")
    private val keyCatalog = stringPreferencesKey("catalog_source")
    private val keyPlaylists = stringSetPreferencesKey("playlist_urls")
    private val keyTorrserve = stringPreferencesKey("torrserve_url")
    private val keyJacred = stringPreferencesKey("jacred_url")
    private val keyHomeChannel = longPreferencesKey("home_channel_id")
    private val keyUpdate = stringPreferencesKey("update_url")

    val tmdbApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyTmdb]?.takeIf { it.isNotBlank() } ?: BuildConfig.TMDB_API_KEY
    }

    /** "" = авто (TMDB, если есть ключ, иначе Cinemeta), либо явный выбор "tmdb"/"cinemeta". */
    val catalogSource: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyCatalog] ?: ""
    }

    val playlistUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[keyPlaylists]?.toList()?.sorted() ?: emptyList()
    }

    /** Адрес сервера TorrServe. По умолчанию локальный (TorrServe на самом ТВ). */
    val torrserveUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyTorrserve]?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8090"
    }

    /**
     * Адрес JacRed. По умолчанию jac.red: зеркало jacred.xyz на проверке
     * отдавало HTML-заглушку вместо JSON.
     */
    val jacredUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyJacred]?.takeIf { it.isNotBlank() } ?: "https://jac.red"
    }

    suspend fun setTmdbApiKey(value: String) {
        context.dataStore.edit { it[keyTmdb] = value.trim() }
    }

    suspend fun setCatalogSource(value: String) {
        context.dataStore.edit { it[keyCatalog] = value }
    }

    suspend fun addPlaylistUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { it[keyPlaylists] = (it[keyPlaylists] ?: emptySet()) + trimmed }
    }

    suspend fun removePlaylistUrl(url: String) {
        context.dataStore.edit { it[keyPlaylists] = (it[keyPlaylists] ?: emptySet()) - url }
    }

    suspend fun setTorrserveUrl(value: String) {
        context.dataStore.edit { it[keyTorrserve] = value.trim() }
    }

    suspend fun setJacredUrl(value: String) {
        context.dataStore.edit { it[keyJacred] = value.trim() }
    }

    /** id созданного нами канала на домашнем экране; 0 — ещё не создан. */
    val homeChannelId: Flow<Long> = context.dataStore.data.map { it[keyHomeChannel] ?: 0L }

    suspend fun setHomeChannelId(value: Long) {
        context.dataStore.edit { it[keyHomeChannel] = value }
    }

    /** Адрес JSON с описанием свежей версии; пусто — проверка обновлений выключена. */
    val updateUrl: Flow<String> = context.dataStore.data.map {
        it[keyUpdate]?.takeIf { url -> url.isNotBlank() } ?: BuildConfig.DEFAULT_UPDATE_URL
    }

    suspend fun setUpdateUrl(value: String) {
        context.dataStore.edit { it[keyUpdate] = value.trim() }
    }
}
