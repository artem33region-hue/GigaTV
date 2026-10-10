package com.artem.gigatv.data.tmdb

import com.artem.gigatv.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object TmdbConst {
    // При недоступности TMDB в вашей сети замените на адрес зеркала/прокси.
    const val API_BASE = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

    fun poster(path: String?): String? = path?.let { "${IMAGE_BASE}w342$it" }
    fun backdrop(path: String?): String? = path?.let { "${IMAGE_BASE}w1280$it" }
}

class TmdbRepository(settings: SettingsRepository, scope: CoroutineScope) {

    @Volatile
    private var apiKey: String = ""

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    init {
        scope.launch {
            settings.tmdbApiKey.collect {
                apiKey = it
                _hasKey.value = it.isNotBlank()
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        // Короткие таймауты: в части сетей api.themoviedb.org недоступен, и без
        // них главная висела на «Загружаем каталог…» до фолбэка на Cinemeta.
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val url = chain.request().url.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", "ru-RU")
                .build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        .build()

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbConst.API_BASE)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbApi::class.java)

    private val rowCache = LinkedHashMap<String, List<TmdbMovie>>()

    suspend fun homeRows(force: Boolean = false): List<Pair<String, List<TmdbMovie>>> {
        if (!_hasKey.value) return emptyList()
        if (force) rowCache.clear()

        suspend fun row(title: String, load: suspend () -> TmdbPage): Pair<String, List<TmdbMovie>>? {
            rowCache[title]?.let { return title to it }
            return try {
                val list = load().results
                rowCache[title] = list
                title to list
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        // Параллельно: при недоступном TMDB это одна задержка таймаута, а не сумма.
        return coroutineScope {
            listOf(
                async { row("Популярные") { api.popular() } },
                async { row("Популярные сериалы") { api.tvPopular() } },
                async { row("Топ рейтинга") { api.topRated() } },
                async { row("Сейчас в кино") { api.nowPlaying() } }
            ).awaitAll().filterNotNull()
        }
    }

    suspend fun discover(genreId: Int, page: Int = 1, series: Boolean = false): List<TmdbMovie> =
        if (series) api.discoverTv(genreId.toString(), page = page).results
        else api.discover(genreId.toString(), page = page).results

    suspend fun search(query: String): List<TmdbMovie> = coroutineScope {
        val movies = async { api.search(query).results }
        val series = async { api.searchTv(query).results }
        movies.await() + series.await()
    }

    suspend fun details(id: Int, series: Boolean = false): TmdbMovie? =
        try {
            if (series) api.tvDetails(id) else api.details(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    suspend fun trailerUrl(id: Int, series: Boolean = false): String? =
        try {
            val videos = if (series) api.tvVideos(id) else api.videos(id)
            videos.results
                .firstOrNull { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
