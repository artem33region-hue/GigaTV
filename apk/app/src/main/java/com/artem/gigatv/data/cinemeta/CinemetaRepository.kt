package com.artem.gigatv.data.cinemeta

import com.artem.gigatv.data.catalog.CatalogMovie
import com.artem.gigatv.data.catalog.CatalogRepository
import com.artem.gigatv.data.net.awaitParsed
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class CinemetaItem(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val year: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("moviedb_id") val moviedbId: Int? = null
) {
    /** id вида tt0133093 → 133093, чтобы уложиться в числовой id каталога. */
    val numericId: Int get() = id.removePrefix("tt").toIntOrNull() ?: 0

    fun toCatalog(): CatalogMovie = CatalogMovie(
        id = numericId,
        source = CatalogRepository.SRC_CINEMETA,
        title = name,
        isSeries = type == "series",
        poster = poster,
        backdrop = background,
        year = (year ?: releaseInfo).orEmpty().take(4),
        overview = description.orEmpty(),
        rating = imdbRating?.toDoubleOrNull() ?: 0.0,
        runtime = runtime?.filter { it.isDigit() }?.toIntOrNull(),
        genres = genres,
        tmdbId = moviedbId
    )
}

@Serializable
private data class CatalogResponse(val metas: List<CinemetaItem> = emptyList())

@Serializable
private data class MetaResponse(val meta: CinemetaItem? = null)

/**
 * Cinemeta — каталог Stremio. Работает **без ключа и без лимитов**, поэтому
 * служит запасным источником, когда ключи Кинопоиска или TMDB не заданы либо
 * исчерпали суточную квоту.
 *
 * Расплата — только английский язык: названия и описания не переведены.
 */
class CinemetaRepository(
    private val baseUrl: String = "https://v3-cinemeta.strem.io",
    private val suppliedClient: OkHttpClient? = null,
    private val reportFailure: (String, Exception) -> Unit = { type, error ->
        android.util.Log.w("GigaTvSearch", "Cinemeta $type search failed", error)
    }
) {

    // На этом ТВ пинг до Cloudflare 5 мс, но HTTPS висел ~8 с: соединение
    // стопорилось на IPv6 (happy-eyeballs). Резолвим только в IPv4.
    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            return v4.ifEmpty { all }
        }
    }

    private val client = suppliedClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .dns(ipv4Dns)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Одна попытка: повтор с таймаутом растягивал холодную загрузку — если один
     * ряд отвечает медленно, awaitAll ждёт именно его. Единичные сбои каталога
     * лечит кнопка «Обновить», а деталей — «Повторить».
     */
    private class HttpFailure(val status: Int) : IOException("Cinemeta: HTTP $status")

    private suspend fun get(path: String): String {
        repeat(2) { attempt ->
            try {
                val request = Request.Builder().url("${baseUrl.trimEnd('/')}$path")
                if (attempt > 0) request.header("Cache-Control", "no-cache")
                return client.newCall(request.build()).awaitParsed { r ->
                    if (!r.isSuccessful) throw HttpFailure(r.code)
                    r.body?.string() ?: throw IOException("Cinemeta: пустой ответ")
                }
            } catch (e: HttpFailure) {
                if (attempt > 0 || e.status !in 500..599) throw e
                delay(350)
            }
        }
        error("Unreachable")
    }

    private suspend fun catalog(path: String, strict: Boolean = false): List<CinemetaItem> {
        return runCatching {
            val body = get(path)
            json.decodeFromString(CatalogResponse.serializer(), body).metas
        }.getOrElse {
            if (it is CancellationException) throw it
            if (strict) throw it
            emptyList()
        }
    }

    /** Ряды главного экрана; порядок задаёт порядок на экране. */
    val homeRowSpecs = listOf(
        "Популярные фильмы" to "/catalog/movie/top.json",
        "Популярные сериалы" to "/catalog/series/top.json",
        "Высокий рейтинг IMDb" to "/catalog/movie/imdbRating.json"
    )

    /** Один ряд каталога. */
    suspend fun loadRow(spec: Pair<String, String>): Pair<String, List<CatalogMovie>> =
        withContext(Dispatchers.IO) {
            spec.first to catalog(spec.second).map { it.toCatalog() }
        }

    suspend fun homeRows(): List<Pair<String, List<CatalogMovie>>> = withContext(Dispatchers.IO) {
        homeRowSpecs.map { spec -> async { loadRow(spec) } }
            .awaitAll()
            .filter { it.second.isNotEmpty() }
    }

    suspend fun search(query: String): List<CatalogMovie> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val results = listOf("movie", "series").map { type ->
            async {
                try {
                    Result.success(catalog("/catalog/$type/top/search=$q.json", strict = true))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportFailure(type, e)
                    Result.failure<List<CinemetaItem>>(e)
                }
            }
        }.awaitAll()
        val items = results.flatMap { it.getOrDefault(emptyList()) }
        if (items.isEmpty()) results.firstOrNull { it.isFailure }?.getOrThrow()
        items.map { it.toCatalog() }
    }

    suspend fun byGenre(genre: String, page: Int, series: Boolean): List<CatalogMovie> =
        withContext(Dispatchers.IO) {
            val type = if (series) "series" else "movie"
            val g = URLEncoder.encode(genre, "UTF-8")
            // Extras Stremio-аддонов склеиваются через '&' внутри сегмента пути.
            val skip = if (page > 1) "&skip=${(page - 1) * 100}" else ""
            catalog("/catalog/$type/top/genre=$g$skip.json", strict = true).map { it.toCatalog() }
        }

    suspend fun details(numericId: Int, series: Boolean): CatalogMovie? =
        withContext(Dispatchers.IO) {
            val type = if (series) "series" else "movie"
            val imdb = "tt" + numericId.toString().padStart(7, '0')
            runCatching {
                val body = get("/meta/$type/$imdb.json")
                json.decodeFromString(MetaResponse.serializer(), body).meta?.toCatalog()
            }.getOrElse {
                if (it is CancellationException) throw it
                null
            }
        }
}
