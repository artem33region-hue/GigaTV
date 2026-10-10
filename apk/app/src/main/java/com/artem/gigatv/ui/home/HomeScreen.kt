package com.artem.gigatv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.artem.gigatv.data.favorites.FavoriteItem
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.MovieCard
import com.artem.gigatv.ui.components.RowTitle
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.launch

/** Горизонтальный отступ рядов; заодно запас, чтобы фокусные карточки не обрезались. */
private val EDGE = 48.dp

@Composable
fun HomeScreen(
    onOpenMovie: (CatalogMovie) -> Unit,
    onOpenLocal: (PlaylistItem) -> Unit,
    onResume: (url: String, title: String, poster: String?) -> Unit,
    onOpenFavorites: () -> Unit
) {
    val container = LocalContext.current.appContainer()
    val playlists by container.playlists.playlists.collectAsState()
    val errors by container.playlists.errors.collectAsState()
    val activeCatalog by container.catalog.active.collectAsState()
    val continueWatching by container.history.inProgress.collectAsState(initial = emptyList())
    val favorites by container.favorites.favorites.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var catalogRows by remember { mutableStateOf<List<Pair<String, List<CatalogMovie>>>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeCatalog, refreshTick) {
        // Ряды приходят по мере готовности: кэш прошлого запуска мгновенно,
        // затем каждый свежий ряд отдельной эмиссией.
        catalogLoading = catalogRows.isEmpty()
        container.catalog.homeRowsFlow(force = refreshTick > 0).collect { rows ->
            catalogRows = rows
            catalogLoading = false
        }
        catalogLoading = false
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        // Горизонтальный отступ не здесь, а внутри рядов: иначе LazyRow обрезает
        // крайние карточки, когда они увеличиваются в фокусе.
        contentPadding = PaddingValues(vertical = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = EDGE)
            ) {
                Text(
                    text = "GigaTV",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                AppButton(onClick = onOpenFavorites) { Text("★ Избранное (${favorites.size})") }
                AppButton(onClick = {
                    refreshTick++
                    scope.launch { container.playlists.refresh() }
                }) { Text("Обновить") }
            }
        }

        if (errors.isNotEmpty()) {
            item(key = "errors") {
                Column(Modifier.padding(horizontal = EDGE)) {
                    errors.forEach { err ->
                        Text("Не удалось загрузить: $err", color = TextMuted)
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Column {
                    RowTitle("Продолжить просмотр", Modifier.padding(start = EDGE))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = EDGE)
                    ) {
                        items(continueWatching, key = { it.url }) { w ->
                            MovieCard(
                                title = w.title,
                                posterUrl = w.poster,
                                subtitle = "${(w.progress * 100).toInt()}%",
                                progress = w.progress,
                                onLongClick = {
                                    scope.launch { container.history.remove(w.url) }
                                },
                                onClick = { onResume(w.url, w.title, w.poster) }
                            )
                        }
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favorites") {
                Column {
                    RowTitle("Избранное", Modifier.padding(start = EDGE))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = EDGE)
                    ) {
                        items(favorites, key = { it.key }) { favorite ->
                            MovieCard(
                                title = favorite.title,
                                posterUrl = favorite.poster,
                                subtitle = favorite.year.ifBlank { null },
                                onClick = {
                                    if (favorite.source == FavoriteItem.SOURCE_LOCAL) {
                                        favorite.url?.let { url ->
                                            onOpenLocal(PlaylistItem(title = favorite.title, url = url,
                                                poster = favorite.poster, year = favorite.year.toIntOrNull(),
                                                description = favorite.description, isSeries = favorite.series))
                                        }
                                    } else {
                                        onOpenMovie(CatalogMovie(id = favorite.id, source = favorite.source,
                                            title = favorite.title, isSeries = favorite.series))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        playlists.forEachIndexed { index, playlist ->
            item(key = "pl-$index-${playlist.name}") {
                Column {
                    RowTitle(playlist.name, Modifier.padding(start = EDGE))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = EDGE)
                    ) {
                        items(playlist.items) { entry ->
                            MovieCard(
                                title = entry.title,
                                posterUrl = entry.poster,
                                subtitle = entry.year?.toString(),
                                onClick = { onOpenLocal(entry) }
                            )
                        }
                    }
                }
            }
        }


        if (catalogLoading && catalogRows.isEmpty() && playlists.isEmpty()) {
            item(key = "catalog-loading") {
                Text("Загружаем каталог…", color = TextMuted, modifier = Modifier.padding(start = EDGE))
            }
        }

        catalogRows.forEach { (title, movies) ->
            item(key = "cat-$title") {
                Column {
                    RowTitle(title, Modifier.padding(start = EDGE))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = EDGE)
                    ) {
                        items(movies, key = { "${it.source}-${it.isSeries}-${it.id}" }) { movie ->
                            MovieCard(
                                title = movie.title,
                                posterUrl = movie.poster,
                                subtitle = movie.subtitle,
                                onClick = { onOpenMovie(movie) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "footer-spacer") { Spacer(Modifier.width(1.dp)) }
    }
}
