package com.artem.gigatv.ui.releases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.json.Json
import com.artem.gigatv.ui.torrents.VideoFileBrowser
import com.artem.gigatv.ui.torrents.saveTorrentSearch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.external.ExternalPlayer
import com.artem.gigatv.data.jacred.JacredTorrent
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.data.torrserve.TorrFile
import com.artem.gigatv.data.torrserve.TorrServeRepository
import com.artem.gigatv.data.torrserve.TorrTorrent
import com.artem.gigatv.data.torrserve.VIDEO_EXTENSIONS
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.details.TorrentSearchSpec
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Sort(val label: String) {
    SEEDS("По сидам"), SIZE("По размеру"), DATE("По дате")
}

/**
 * Отдельный экран раздач: поиск в JacRed, фильтры по качеству и озвучке,
 * сортировка. Выбор раздачи добавляет её в TorrServe и открывает плеер.
 */
@Composable
fun ReleasesScreen(spec: TorrentSearchSpec, onPlay: (PlaylistItem) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer()
    val scope = rememberCoroutineScope()
    val hasExternal = remember { ExternalPlayer.isAvailable(context) }

    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf<List<JacredTorrent>>(emptyList()) }
    var files by rememberSaveable(stateSaver = Saver<Pair<TorrTorrent, List<TorrFile>>?, String>(
        save = { value -> value?.let { Json.encodeToString(TorrTorrent.serializer(), it.first) } },
        restore = { Json.decodeFromString(TorrTorrent.serializer(), it).let { t ->
            t to t.file_stats.orEmpty().filter { it.ext in VIDEO_EXTENSIONS }
        } }
    )) { mutableStateOf<Pair<TorrTorrent, List<TorrFile>>?>(null) }

    var sort by remember { mutableStateOf(Sort.SEEDS) }
    var quality by remember { mutableStateOf<Int?>(null) }   // null — все
    var voice by remember { mutableStateOf<String?>(null) }  // null — все
    var openJob by remember { mutableStateOf<Job?>(null) }

    fun search() {
        scope.launch {
            loading = true
            status = null
            files = null
            container.jacred.search(spec.title, spec.year, spec.series)
                .onSuccess { all = it }
                .onFailure {
                    all = emptyList()
                    status = "Поиск не удался: ${it.message ?: "нет связи"}"
                }
            loading = false
        }
    }

    LaunchedEffect(spec.title) { if (files == null) search() }

    fun play(torrent: TorrTorrent, file: TorrFile) {
        scope.launch {
            val url = container.torrserve.streamUrl(torrent.hash, file)
            onPlay(
                PlaylistItem(
                    title = file.fileName.ifBlank { spec.title },
                    url = url,
                    poster = spec.poster
                )
            )
        }
    }

    fun open(release: JacredTorrent) {
        // Клик по другой раздаче отменяет прежнюю: иначе обе добавляются в
        // TorrServe и первая успевшая уводит в плеер не тем фильмом.
        openJob?.cancel()
        openJob = scope.launch {
            status = "Открываем раздачу в TorrServe…"
            files = null
            val torrent = when (val r = container.torrserve.add(release.magnet)) {
                is TorrServeRepository.AddResult.Ready -> r.torrent
                TorrServeRepository.AddResult.ServerUnavailable -> {
                    status = "TorrServe не ответил. Проверьте сервер на экране «Торренты»."
                    return@launch
                }
                TorrServeRepository.AddResult.NoMetadata -> {
                    status = "За минуту не пришёл список файлов — нет живых пиров " +
                        "либо недоступны трекеры. Возьмите раздачу с бо́льшим числом сидов."
                    return@launch
                }
            }
            status = null
            context.saveTorrentSearch(torrent.hash, spec)
            val videos = torrent.file_stats.orEmpty()
                .filter { it.ext in VIDEO_EXTENSIONS }
                .sortedByDescending { it.length }
            when {
                videos.isEmpty() -> status = "В раздаче нет видеофайлов."
                else -> files = torrent to videos
            }
        }
    }

    // Набор качеств и озвучек для фильтров — из того, что реально нашлось.
    val qualities = remember(all) { all.map { it.quality }.filter { it > 0 }.distinct().sortedDescending() }
    val voices = remember(all) { all.flatMap { it.voices }.distinct().sorted() }

    val shown = remember(all, sort, quality, voice) {
        all.asSequence()
            .filter { quality == null || it.quality == quality }
            .filter { voice == null || it.voices.contains(voice) }
            .sortedWith(
                when (sort) {
                    Sort.SEEDS -> compareByDescending { it.sid }
                    Sort.SIZE -> compareByDescending { it.size }
                    Sort.DATE -> compareByDescending { it.createTime }
                }
            )
            .toList()
    }

    BackHandler(enabled = files != null) {
        files = null
        search()
    }

    val chosen = files
    if (chosen != null) {
        // Выбор файла из многосерийной раздачи.
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(48.dp)
        ) {
            Text(chosen.first.displayName, style = MaterialTheme.typography.headlineSmall)
            AppButton(onClick = { files = null; search() }) { Text("Назад к раздачам") }
            VideoFileBrowser(chosen.second, chosen.first.hash,
                onPlay = { play(chosen.first, it) },
                onExternal = if (hasExternal) { file ->
                    scope.launch {
                        ExternalPlayer.open(context, container.torrserve.streamUrl(chosen.first.hash, file), file.fileName)
                    }
                    Unit
                } else null,
                modifier = Modifier.weight(1f))
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title") {
            Text("Раздачи: ${spec.title}", style = MaterialTheme.typography.headlineMedium)
        }

        item(key = "sort") {
            ChipRow("Сортировка", Sort.entries.map { it.label }, sort.label) { label ->
                sort = Sort.entries.first { it.label == label }
            }
        }

        if (qualities.isNotEmpty()) {
            item(key = "quality") {
                ChipRow(
                    "Качество",
                    listOf("Все") + qualities.map { "${it}p" },
                    quality?.let { "${it}p" } ?: "Все"
                ) { label ->
                    quality = if (label == "Все") null else label.removeSuffix("p").toIntOrNull()
                }
            }
        }

        if (voices.isNotEmpty()) {
            item(key = "voice") {
                ChipRow("Озвучка", listOf("Все") + voices, voice ?: "Все") { label ->
                    voice = if (label == "Все") null else label
                }
            }
        }

        status?.let { item(key = "status") { Text(it, color = TextMuted) } }

        if (loading) {
            item(key = "loading") { Text("Ищем в JacRed…", color = TextMuted) }
        } else {
            item(key = "count") {
                Text("Показано ${shown.size} из ${all.size}", color = TextMuted)
            }
            items(shown, key = { it.magnet }) { release ->
                AppButton(onClick = { open(release) }) {
                    Column {
                        Text(release.title, maxLines = 2)
                        Text(release.summary, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

/** Горизонтальный ряд чипов-переключателей с подписью слева. */
@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it }) { option ->
                Chip(text = option, selected = option == selected) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.onSurface
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.surface
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
