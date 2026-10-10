package com.artem.gigatv.ui.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.catalog.CatalogMovie
import com.artem.gigatv.data.external.ExternalPlayer
import com.artem.gigatv.data.favorites.FavoriteItem
import com.artem.gigatv.data.jacred.JacredTorrent
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.data.torrserve.TorrFile
import com.artem.gigatv.data.torrserve.TorrServeRepository
import com.artem.gigatv.data.torrserve.TorrTorrent
import com.artem.gigatv.data.torrserve.VIDEO_EXTENSIONS
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.theme.BackgroundDark
import com.artem.gigatv.ui.theme.CardDark
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun CatalogDetailsScreen(
    source: String,
    movieId: Int,
    series: Boolean,
    onPlay: (PlaylistItem) -> Unit,
    onOpenReleases: (TorrentSearchSpec) -> Unit
) {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()
    var movie by remember { mutableStateOf<CatalogMovie?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(source, movieId, series, retry) {
        loading = true
        movie = container.catalog.details(source, movieId, series)
        loading = false
    }

    val current = movie
    if (current == null) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (loading) "Загрузка…" else "Не удалось загрузить данные фильма",
                color = TextMuted
            )
            if (!loading) {
                AppButton(onClick = { retry++ }) { Text("Повторить") }
            }
        }
        return
    }

    val allPlaylists by container.playlists.playlists.collectAsState()
    val localItem = remember(allPlaylists, current) {
        container.playlists.findFor(allPlaylists, current.tmdbId, current.title, current.isSeries)
            ?.let { it.copy(poster = it.poster ?: current.poster) }
    }
    val favorites by container.favorites.favorites.collectAsState(initial = emptyList())
    val favItem = FavoriteItem(
        source = source,
        id = current.id,
        title = current.title,
        series = current.isSeries,
        poster = current.poster,
        year = current.year
    )
    val isFav = favorites.any { it.key == favItem.key }

    DetailsContent(
        title = current.title,
        subtitle = buildString {
            if (current.year.isNotBlank()) append(current.year)
            if (current.isSeries) {
                if (isNotEmpty()) append(" · ")
                append("сериал")
            }
            current.runtime?.let { if (isNotEmpty()) append(" · "); append("$it мин") }
            if (current.rating > 0) {
                if (isNotEmpty()) append(" · ")
                append("★ %.1f".format(current.rating))
            }
            val genres = current.genres.joinToString(", ")
            if (genres.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(genres)
            }
        },
        description = current.overview.ifBlank { "Описание отсутствует." },
        posterUrl = current.poster,
        backdropUrl = current.backdrop,
        playItem = localItem,
        trailerUrl = current.trailer,
        onPlay = onPlay,
        favoriteLabel = if (isFav) "★ В избранном" else "☆ В избранное",
        onToggleFavorite = { scope.launch { container.favorites.toggle(favItem) } },
        noSourceHint = null,
        torrentSearch = TorrentSearchSpec(
            title = current.title,
            year = current.year,
            series = current.isSeries,
            poster = current.poster
        ),
        onOpenReleases = onOpenReleases
    )
}

/** Что искать в JacRed для этой карточки. */
@kotlinx.serialization.Serializable
data class TorrentSearchSpec(
    val title: String,
    val year: String,
    val series: Boolean,
    val poster: String?
)

@Composable
fun LocalDetailsScreen(item: PlaylistItem, onPlay: (PlaylistItem) -> Unit) {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()
    val favorites by container.favorites.favorites.collectAsState(initial = emptyList())
    val favItem = FavoriteItem(
        source = FavoriteItem.SOURCE_LOCAL,
        series = item.isSeries,
        title = item.title,
        poster = item.poster,
        year = item.year?.toString() ?: "",
        url = item.url,
        description = item.description
    )
    val isFav = favorites.any { it.key == favItem.key }

    DetailsContent(
        title = item.title,
        subtitle = item.year?.toString() ?: "",
        description = item.description ?: "Описание отсутствует.",
        posterUrl = item.poster,
        backdropUrl = null,
        playItem = item,
        trailerUrl = null,
        onPlay = onPlay,
        favoriteLabel = if (isFav) "★ В избранном" else "☆ В избранное",
        onToggleFavorite = { scope.launch { container.favorites.toggle(favItem) } },
        noSourceHint = null,
        torrentSearch = null,
        onOpenReleases = {}
    )
}

@Composable
private fun DetailsContent(
    title: String,
    subtitle: String,
    description: String,
    posterUrl: String?,
    backdropUrl: String?,
    playItem: PlaylistItem?,
    trailerUrl: String?,
    onPlay: (PlaylistItem) -> Unit,
    favoriteLabel: String,
    onToggleFavorite: () -> Unit,
    @Suppress("UNUSED_PARAMETER") noSourceHint: String?,
    torrentSearch: TorrentSearchSpec?,
    onOpenReleases: (TorrentSearchSpec) -> Unit
) {
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(BackgroundDark, BackgroundDark.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BackgroundDark)
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(220.dp)
                        .height(330.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                )
                Spacer(Modifier.width(36.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.titleMedium, color = TextMuted)
                }

                // Все действия одним рядом: навигация влево-вправо, без перехода
                // вниз между Row и отдельной кнопкой, который срабатывал не всегда.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    if (playItem != null) {
                        AppButton(onClick = { onPlay(playItem) }) { Text("▶  Смотреть") }
                        if (ExternalPlayer.isAvailable(context)) {
                            AppButton(onClick = {
                                ExternalPlayer.open(context, playItem.url, playItem.title)
                            }) { Text("Внешний плеер") }
                        }
                    }
                    // Раздачи — отдельным экраном с фильтрами и сортировкой.
                    if (torrentSearch != null) {
                        AppButton(onClick = { onOpenReleases(torrentSearch) }) {
                            Text("Раздачи (JacRed)")
                        }
                    }
                    AppButton(onClick = onToggleFavorite) { Text(favoriteLabel) }
                    if (trailerUrl != null) {
                        AppButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    "Нет приложения для открытия трейлера",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Text("Трейлер") }
                    }
                }

                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }
    }
}
