package com.artem.gigatv.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.favorites.FavoriteItem
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.ui.components.MovieCard
import com.artem.gigatv.ui.theme.TextMuted

@Composable
fun FavoritesScreen(
    onOpenMovie: (source: String, id: Int, series: Boolean) -> Unit,
    onOpenLocal: (PlaylistItem) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val favorites by container.favorites.favorites.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Избранное", style = MaterialTheme.typography.headlineMedium)
        if (favorites.isNotEmpty()) {
            Text("ОК — открыть · Удерживайте ОК — удалить из избранного", color = TextMuted)
        }

        if (favorites.isEmpty()) {
            Text(
                "Пока пусто. Откройте фильм и нажмите «В избранное» — он появится здесь.",
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
            items(favorites, key = { it.key }) { fav ->
                MovieCard(
                    title = fav.title,
                    posterUrl = fav.poster,
                    onLongClick = { scope.launch { container.favorites.remove(fav.key) } },
                    subtitle = listOfNotNull(
                        fav.year.takeIf { it.isNotBlank() },
                        if (fav.series) "сериал" else null
                    ).joinToString(" · ").ifBlank { null },
                    onClick = {
                        if (fav.source == FavoriteItem.SOURCE_LOCAL) {
                            val url = fav.url ?: return@MovieCard
                            onOpenLocal(
                                PlaylistItem(
                                    title = fav.title,
                                    url = url,
                                    poster = fav.poster,
                                    year = fav.year.toIntOrNull(),
                                    description = fav.description,
                                    isSeries = fav.series
                                )
                            )
                        } else {
                            onOpenMovie(fav.source, fav.id, fav.series)
                        }
                    }
                )
            }
        }
    }
}
