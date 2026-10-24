package com.artem.gigatv.data.playlist

import com.artem.gigatv.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class PlaylistRepository(
    private val urls: Flow<List<String>>,
    private val client: OkHttpClient = OkHttpClient()
) {
    constructor(settings: SettingsRepository) : this(settings.playlistUrls)
    private val lastSuccessful = mutableMapOf<String, Playlist>()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Работа идёт в собственном scope: отмена вызывающего (уход с экрана)
    // не должна оборвать загрузку на полпути и записать неполный список.
    suspend fun refresh() {
        scope.launch { doRefresh() }.join()
    }

    private suspend fun doRefresh() = refreshMutex.withLock {
        _refreshing.value = true
        try {
            val result = mutableListOf<Playlist>()
            val problems = mutableListOf<String>()
            val sources = urls.first().distinct()
            lastSuccessful.keys.retainAll(sources.toSet())
            for (url in sources) {
                try {
                    val body = withContext(Dispatchers.IO) {
                        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            resp.body?.string() ?: error("пустой ответ")
                        }
                    }
                    val playlist = json.decodeFromString<Playlist>(body)
                    lastSuccessful[url] = playlist
                    result += playlist
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastSuccessful[url]?.let { result += it }
                    problems += "$url — ${e.message ?: "ошибка загрузки"}"
                }
            }
            _playlists.value = result
            _errors.value = problems
        } finally {
            _refreshing.value = false
        }
    }

    fun findFor(tmdbId: Int?, title: String?, series: Boolean = false): PlaylistItem? =
        findFor(_playlists.value, tmdbId, title, series)

    fun findFor(lists: List<Playlist>, tmdbId: Int?, title: String?, series: Boolean = false): PlaylistItem? =
        findPlaylistItem(lists, tmdbId, title, series)

    fun search(query: String): List<PlaylistItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _playlists.value.flatMap { it.items }.filter { q in it.title.lowercase() }
    }
}

internal fun findPlaylistItem(lists: List<Playlist>, tmdbId: Int?, title: String?, series: Boolean): PlaylistItem? {
    val all = lists.asSequence().flatMap { it.items }.filter { it.isSeries == series }
    return tmdbId?.let { id -> all.firstOrNull { it.tmdbId == id } }
        ?: title?.let { t ->
            all.firstOrNull { it.title.trim().equals(t.trim(), ignoreCase = true) }
        }
}

