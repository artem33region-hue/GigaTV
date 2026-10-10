package com.artem.gigatv.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.catalog.CatalogMovie
import com.artem.gigatv.data.catalog.CatalogRepository
import com.artem.gigatv.data.catalog.Genres
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.MovieCard
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onOpenMovie: (CatalogMovie) -> Unit) {
    val container = LocalContext.current.appContainer()
    val activeCatalog by container.catalog.active.collectAsState()
    val scope = rememberCoroutineScope()

    var selected by rememberSaveable { mutableStateOf(Genres.ALL.first()) }
    var seriesMode by rememberSaveable { mutableStateOf(false) }
    var movies by remember { mutableStateOf<List<CatalogMovie>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var loadMoreJob by remember { mutableStateOf<Job?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }
    val availableGenres = if (seriesMode && activeCatalog == CatalogRepository.SRC_TMDB) {
        Genres.tmdbTvIds.keys.toList()
    } else Genres.ALL

    LaunchedEffect(selected, seriesMode, activeCatalog, retryTick) {
        loadMoreJob?.cancel()
        loadingMore = false
        loadError = null
        movies = emptyList()
        if (selected !in availableGenres) {
            selected = availableGenres.first()
            return@LaunchedEffect
        }
        loading = true
        page = 1
        endReached = false
        try {
            movies = container.catalog.byGenre(selected, 1, seriesMode)
                .distinctBy { Triple(it.source, it.isSeries, it.id) }
            endReached = movies.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "Не удалось загрузить категорию: ${e.message ?: "нет связи"}"
        } finally {
            if (isActive) loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Категории", style = MaterialTheme.typography.headlineMedium)
            AppButton(onClick = { seriesMode = false }) {
                Text(
                    "Фильмы",
                    color = if (!seriesMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            AppButton(onClick = { seriesMode = true }) {
                Text(
                    "Сериалы",
                    color = if (seriesMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(availableGenres) { genre ->
                AppButton(onClick = { selected = genre }) {
                    Text(
                        text = genre,
                        color = if (genre == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (loadError != null) {
            Text(loadError.orEmpty(), color = TextMuted)
            if (movies.isEmpty()) AppButton(onClick = { retryTick++ }) { Text("Повторить") }
        }
        when {
            loading -> Text("Загрузка…", color = TextMuted)
            movies.isEmpty() && loadError == null -> Text(
                "В категории «$selected» (${if (seriesMode) "сериалы" else "фильмы"}) ничего не нашлось.",
                color = TextMuted
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(movies, key = { "${it.source}-${it.isSeries}-${it.id}" }) { movie ->
                MovieCard(
                    title = movie.title,
                    posterUrl = movie.poster,
                    subtitle = movie.subtitle,
                    onClick = { onOpenMovie(movie) }
                )
            }
            if (movies.isNotEmpty() && !endReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppButton(
                        enabled = !loading && !loadingMore,
                        onClick = {
                            loadingMore = true
                            loadError = null
                            val genre = selected
                            val series = seriesMode
                            val catalog = activeCatalog
                            val nextPage = page + 1
                            loadMoreJob?.cancel()
                            loadMoreJob = scope.launch {
                                try {
                                    val fetched = container.catalog.byGenre(genre, nextPage, series)
                                    if (!isActive || genre != selected || series != seriesMode ||
                                        catalog != activeCatalog
                                    ) return@launch
                                    val known = movies.mapTo(HashSet()) { Triple(it.source, it.isSeries, it.id) }
                                    val fresh = fetched.filter { known.add(Triple(it.source, it.isSeries, it.id)) }
                                    endReached = fetched.isEmpty()
                                    movies = movies + fresh
                                    page = nextPage
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    loadError = "Не удалось загрузить следующую страницу. Повторите попытку."
                                } finally {
                                    if (isActive) loadingMore = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (loadingMore) "Загрузка…" else "Показать ещё") }
                }
            }
        }
    }
}
