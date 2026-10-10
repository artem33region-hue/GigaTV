package com.artem.gigatv.data.catalog

import com.artem.gigatv.data.SettingsRepository
import com.artem.gigatv.data.cinemeta.CinemetaRepository
import com.artem.gigatv.data.tmdb.TmdbConst
import com.artem.gigatv.data.tmdb.TmdbMovie
import com.artem.gigatv.data.tmdb.TmdbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Единая карточка фильма/сериала для UI, независимая от источника каталога. */
@kotlinx.serialization.Serializable
data class CatalogMovie(
    val id: Int,
    val source: String,
    val title: String,
    val isSeries: Boolean = false,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String = "",
    val overview: String = "",
    val rating: Double = 0.0,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val tmdbId: Int? = null,
    val trailer: String? = null
) {
    val subtitle: String
        get() = listOfNotNull(
            year.takeIf { it.isNotBlank() },
            if (isSeries) "сериал" else null
        ).joinToString(" · ").ifBlank { " " }
}

fun TmdbMovie.toCatalog() = CatalogMovie(
    id = id,
    source = CatalogRepository.SRC_TMDB,
    title = anyTitle,
    // у /tv-ответов заполнено name, а title пустой
    isSeries = title.isBlank() && tvName.isNotBlank(),
    poster = TmdbConst.poster(posterPath),
    backdrop = TmdbConst.backdrop(backdropPath),
    year = year,
    overview = overview,
    rating = voteAverage,
    runtime = runtime,
    genres = genres.map { it.name },
    tmdbId = id
)

/** Жанры для экрана «Категории»: русские названия и их id в TMDB. */
object Genres {
    val tmdbIds = linkedMapOf(
        "Комедия" to 35, "Боевик" to 28, "Драма" to 18, "Фантастика" to 878,
        "Ужасы" to 27, "Триллер" to 53, "Мелодрама" to 10749, "Приключения" to 12,
        "Мультфильм" to 16, "Детектив" to 9648, "Криминал" to 80, "Фэнтези" to 14,
        "Военный" to 10752, "Семейный" to 10751, "История" to 36, "Документальный" to 99
    )
    val ALL: List<String> = tmdbIds.keys.toList()

    val tmdbTvIds = linkedMapOf(
        "Комедия" to 35, "Боевик" to 10759, "Драма" to 18,
        "Фантастика" to 10765, "Приключения" to 10759,
        "Мультфильм" to 16, "Детектив" to 9648, "Криминал" to 80,
        "Фэнтези" to 10765, "Военный" to 10768,
        "Семейный" to 10751, "Документальный" to 99
    )

    /** Те же жанры для Cinemeta — у него они английские. */
    val cinemeta = mapOf(
        "Комедия" to "Comedy", "Боевик" to "Action", "Драма" to "Drama",
        "Фантастика" to "Sci-Fi", "Ужасы" to "Horror", "Триллер" to "Thriller",
        "Мелодрама" to "Romance", "Приключения" to "Adventure",
        "Мультфильм" to "Animation", "Детектив" to "Mystery", "Криминал" to "Crime",
        "Фэнтези" to "Fantasy", "Военный" to "War", "Семейный" to "Family",
        "История" to "History", "Документальный" to "Documentary"
    )
}

/**
 * Фасад каталога: выбирает активный источник (Кинопоиск или TMDB) по настройке
 * пользователя и наличию ключей, отдаёт ряды главного экрана, поиск и детали.
 */
class CatalogRepository(
    settings: SettingsRepository,
    private val tmdb: TmdbRepository,
    private val cinemeta: CinemetaRepository,
    private val cache: CatalogCache,
    scope: CoroutineScope
) {
    companion object {
        const val SRC_TMDB = "tmdb"
        const val SRC_CINEMETA = "cinemeta"
        const val NONE = "none"
    }

    /**
     * Почему показан не тот каталог, который ожидался. Раньше при отказе
     * источника главная просто молча пустела — теперь причина видна.
     */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * Cinemeta не требует ключа, поэтому доступен всегда и служит последним
     * запасным вариантом вместо пустого экрана.
     */
    /**
     * По умолчанию Cinemeta: он без ключа, без лимитов и доступен там, где
     * api.themoviedb.org заблокирован. TMDB (русские названия) — по явному выбору
     * и при наличии ключа.
     */
    val active: StateFlow<String> =
        combine(settings.catalogSource, tmdb.hasKey) { pref, hasTmdb ->
            when {
                pref == SRC_TMDB && hasTmdb -> SRC_TMDB
                else -> SRC_CINEMETA
            }
        }.stateIn(scope, SharingStarted.Eagerly, SRC_CINEMETA)

    /**
     * Ряды главного экрана потоком, без ожидания «всех сразу»:
     *  1) сначала мгновенно отдаётся дисковый кэш прошлого запуска;
     *  2) затем каждый свежий ряд — как только пришёл его ответ, поэтому один
     *     медленный эндпоинт больше не держит весь экран.
     */
    fun homeRowsFlow(force: Boolean = false): Flow<List<Pair<String, List<CatalogMovie>>>> =
        channelFlow {
            if (!force) {
                val cached = withContext(Dispatchers.IO) { cache.read() }
                if (cached.isNotEmpty()) send(cached)
            }
            val fresh = when (active.value) {
                SRC_TMDB -> {
                    val rows = tmdb.homeRows(force)
                        .map { (t, l) -> t to l.map { it.toCatalog() } }
                        .filter { it.second.isNotEmpty() }
                    if (rows.isNotEmpty()) {
                        send(rows)
                        rows
                    } else {
                        // TMDB молчит (сеть/лимит) — тихо падаем на Cinemeta.
                        streamCinemeta()
                    }
                }
                else -> streamCinemeta()
            }
            if (fresh.isNotEmpty()) withContext(Dispatchers.IO) { cache.write(fresh) }
        }

    /** Грузит ряды Cinemeta параллельно и шлёт накопленный список после каждого. */
    private suspend fun ProducerScope<List<Pair<String, List<CatalogMovie>>>>.streamCinemeta():
        List<Pair<String, List<CatalogMovie>>> = coroutineScope {
        val specs = cinemeta.homeRowSpecs
        // Слоты по порядку спеков, чтобы ряды не скакали местами между эмиссиями.
        val slots = arrayOfNulls<Pair<String, List<CatalogMovie>>>(specs.size)
        specs.mapIndexed { i, spec ->
            async {
                val row = cinemeta.loadRow(spec)
                if (row.second.isNotEmpty()) {
                    // asyncs наследуют один диспетчер вызывающего — слоты не гоняются.
                    slots[i] = row
                    send(slots.filterNotNull())
                }
            }
        }.awaitAll()
        slots.filterNotNull()
    }

    suspend fun byGenre(genre: String, page: Int = 1, series: Boolean = false): List<CatalogMovie> =
        when (active.value) {
            SRC_TMDB -> (if (series) Genres.tmdbTvIds else Genres.tmdbIds)[genre]
                ?.let { id -> tmdb.discover(id, page, series).map { it.toCatalog().copy(isSeries = series) } }
                ?: emptyList()
            SRC_CINEMETA -> Genres.cinemeta[genre]
                ?.let { cinemeta.byGenre(it, page, series) }
                ?: emptyList()
            else -> emptyList()
        }

    suspend fun search(query: String): List<CatalogMovie> =
        when (active.value) {
            SRC_TMDB -> tmdb.search(query).map { it.toCatalog() }
            SRC_CINEMETA -> cinemeta.search(query)
            else -> emptyList()
        }

    suspend fun details(source: String, id: Int, series: Boolean = false): CatalogMovie? =
        when (source) {
            SRC_TMDB -> tmdb.details(id, series)?.toCatalog()
                ?.copy(trailer = tmdb.trailerUrl(id, series), isSeries = series)
            SRC_CINEMETA -> cinemeta.details(id, series)
            else -> null
        }
}
