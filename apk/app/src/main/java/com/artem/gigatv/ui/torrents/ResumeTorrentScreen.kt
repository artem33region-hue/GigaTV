package com.artem.gigatv.ui.torrents

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.PlayRequest
import com.artem.gigatv.data.torrserve.TorrTorrent
import com.artem.gigatv.data.torrserve.VIDEO_EXTENSIONS
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.details.TorrentSearchSpec
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun ResumeTorrentScreen(request: PlayRequest, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit,
                        onOpenReleases: (TorrentSearchSpec) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer()
    val scope = rememberCoroutineScope()
    var torrent by rememberSaveable(request.url, stateSaver = Saver<TorrTorrent?, String>(
        save = { t -> t?.let { Json.encodeToString(TorrTorrent.serializer(), it) } },
        restore = { Json.decodeFromString(TorrTorrent.serializer(), it) }
    )) { mutableStateOf<TorrTorrent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(request.url, retry) {
        loading = true
        if (torrent == null) torrent = container.torrserve.torrentForStream(request.url)
        loading = false
    }
    fun backToReleases() {
        val current = torrent
        if (current != null) onOpenReleases(context.torrentSearch(current, request.poster)) else onBack()
    }
    BackHandler { backToReleases() }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppButton(onClick = { backToReleases() }) {
            Text(if (torrent != null) "← Назад к раздачам" else "← Назад")
        }
        val current = torrent
        if (current != null) {
            Text(current.displayName)
            val videos = remember(current) { current.file_stats.orEmpty().filter { it.ext in VIDEO_EXTENSIONS } }
            if (videos.isEmpty()) Text("В раздаче нет видеофайлов.")
            else VideoFileBrowser(videos, current.hash, onPlay = { file ->
                scope.launch {
                    onPlay(PlayRequest(container.torrserve.streamUrl(current.hash, file),
                        playbackTitle(context.torrentSearch(current, request.poster).title, file), request.poster))
                }
            }, modifier = Modifier.weight(1f),
                initialFileId = Uri.parse(request.url).getQueryParameter("index")?.toIntOrNull())
        } else if (loading) Text("Загружаем файлы раздачи…")
        else {
            Text("Не удалось получить раздачу. Проверьте TorrServe: раздача могла быть удалена с сервера.")
            AppButton(onClick = { retry++ }) { Text("Повторить") }
        }
    }
}
