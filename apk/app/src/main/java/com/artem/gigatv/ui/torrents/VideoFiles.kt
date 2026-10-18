package com.artem.gigatv.ui.torrents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.artem.gigatv.data.torrserve.TorrFile
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.theme.TextMuted

private val seasonEpisode = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})[ ._-]*e(\\d{1,3})")
private val crossEpisode = Regex("(?i)(?:^|[^0-9])(\\d{1,2})x(\\d{1,3})(?:[^0-9]|$)")
private val seasonFolder = Regex("(?i)(?:season[ ._-]*|сезон[ ._-]*|s)(\\d{1,2})(?:[^0-9]|$)|(?:^|[^0-9])(\\d{1,2})[ ._-]*сезон")
private val episodeNumber = Regex("(?i)(?:episode[ ._-]*|серия[ ._-]*|e)(\\d{1,3})(?:[^0-9]|$)|(?:^|[^0-9])(\\d{1,3})[ ._-]*серия")

internal fun episodeInfo(file: TorrFile): Pair<Int?, Int?> {
    val match = seasonEpisode.find(file.fileName) ?: crossEpisode.find(file.fileName)
    if (match != null) return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    val folder = file.path.replace('\\', '/').substringBeforeLast('/', "")
    val season = seasonFolder.find(file.fileName) ?: seasonFolder.findAll(folder).lastOrNull()
    val episode = episodeNumber.find(file.fileName)
    fun number(match: MatchResult?) = match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
    return number(season) to number(episode)
}

internal fun naturalFileKey(file: TorrFile): String = Regex("\\d+").replace(file.fileName.lowercase()) {
    it.value.trimStart('0').padStart(12, '0')
}

internal fun playbackTitle(name: String, file: TorrFile): String {
    val base = name.trim().ifBlank { file.fileName }
    val (season, episode) = episodeInfo(file)
    return when {
        season != null && episode != null -> "$base — Сезон $season Серия $episode"
        episode != null -> "$base — Серия $episode"
        else -> base
    }
}

@Composable
fun VideoFileBrowser(files: List<TorrFile>, identity: String, onPlay: (TorrFile) -> Unit,
                     onExternal: ((TorrFile) -> Unit)? = null, modifier: Modifier = Modifier,
                     initialFileId: Int? = null) {
    val groups = remember(files) {
        files.groupBy { episodeInfo(it).first ?: -1 }.toSortedMap().mapValues { (_, group) ->
            group.sortedWith(compareBy<TorrFile> { episodeInfo(it).second ?: Int.MAX_VALUE }
                .thenBy { naturalFileKey(it) }.thenBy { it.id })
        }
    }
    var selected by rememberSaveable(identity) {
        mutableStateOf(files.firstOrNull { it.id == initialFileId }?.let { episodeInfo(it).first ?: -1 }
            ?: groups.keys.firstOrNull() ?: -1)
    }
    val current = selected.takeIf { it in groups } ?: groups.keys.firstOrNull() ?: -1
    var selectedFileId by rememberSaveable(identity) { mutableStateOf(initialFileId) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (groups.size > 1 || groups.keys.any { it >= 0 }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups.keys.toList()) { season ->
                    AppButton(onClick = { selected = season }) {
                        Text((if (current == season) "✓ " else "") +
                            (if (season < 0) "Другие файлы" else "Сезон $season") + " (${groups[season]?.size})")
                    }
                }
            }
        }
        Text("Файлов: ${groups[current].orEmpty().size}", color = TextMuted)
        key(identity, current) {
            val seasonFiles = groups[current].orEmpty()
            val listState = rememberLazyListState()
            val episodeFocus = remember { FocusRequester() }
            // Navigation resumes only after its transition. Restore the row after it is laid out.
            LaunchedEffect(lifecycleState) {
                if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
                val index = seasonFiles.indexOfFirst { it.id == selectedFileId }
                if (index < 0) return@LaunchedEffect
                listState.scrollToItem(index)
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == selectedFileId } }
                    .first { it }
                withFrameNanos { }
                episodeFocus.requestFocus()
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(seasonFiles, key = { it.id }) { file ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        AppButton(onClick = { selectedFileId = file.id; onPlay(file) },
                            modifier = Modifier.weight(1f).then(
                                if (file.id == selectedFileId) Modifier.focusRequester(episodeFocus) else Modifier
                            )) {
                            Column {
                                episodeInfo(file).second?.let { Text("Серия $it") }
                                Text(file.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        onExternal?.let { external ->
                            AppButton(onClick = { selectedFileId = file.id; external(file) }) { Text("Внешний") }
                        }
                    }
                }
            }
        }
    }
}
