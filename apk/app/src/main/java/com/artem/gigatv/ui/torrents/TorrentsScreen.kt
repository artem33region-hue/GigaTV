package com.artem.gigatv.ui.torrents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.torrserve.TorrServeRepository
import com.artem.gigatv.data.torrserve.TorrTorrent
import com.artem.gigatv.data.torrserve.VIDEO_EXTENSIONS
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.AppTextField
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

private data class TorrentExample(val name: String, val link: String)

// Только легальные раздачи с открытой лицензией (Internet Archive, CC-BY).
private val LEGAL_EXAMPLES = listOf(
    TorrentExample(
        "Big Buck Bunny (2008, CC-BY)",
        "https://archive.org/download/BigBuckBunny_124/BigBuckBunny_124_archive.torrent"
    ),
    TorrentExample(
        "Sintel (2010, CC-BY)",
        "https://archive.org/download/Sintel/Sintel_archive.torrent"
    ),
    TorrentExample(
        "Elephants Dream (2006, CC-BY)",
        "https://archive.org/download/ElephantsDream/ElephantsDream_archive.torrent"
    )
)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return "%.1f ГБ".format(gb)
    val mb = bytes / 1_048_576.0
    return "%.0f МБ".format(mb)
}

@Composable
fun TorrentsScreen(onPlay: (url: String, title: String) -> Unit) {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()

    val savedUrl by container.settings.torrserveUrl.collectAsState(initial = "")
    var urlInput by remember { mutableStateOf("") }
    var version by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }

    var magnet by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var torrent by rememberSaveable(stateSaver = Saver<TorrTorrent?, String>(
        save = { value -> value?.let { Json.encodeToString(TorrTorrent.serializer(), it) } },
        restore = { Json.decodeFromString(TorrTorrent.serializer(), it) }
    )) { mutableStateOf<TorrTorrent?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var addJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(savedUrl) {
        addJob?.cancel()
        loading = false
        addError = null
        checking = true
        version = container.torrserve.echo()
        checking = false
    }

    fun addLink(link: String) {
        if (link.isBlank()) return
        addJob?.cancel()
        loading = true
        addError = null
        torrent = null
        addJob = scope.launch {
            try {
                when (val result = container.torrserve.add(link)) {
                    is TorrServeRepository.AddResult.Ready -> torrent = result.torrent
                    TorrServeRepository.AddResult.ServerUnavailable ->
                        addError = "TorrServe не ответил. Проверьте, что сервер запущен и ссылка верна."
                    TorrServeRepository.AddResult.NoMetadata ->
                        addError = "Раздача принята, но за минуту не пришёл список файлов — " +
                            "нет живых пиров либо недоступны трекеры."
                }
            } finally {
                if (isActive) loading = false
            }
        }
    }

    val videoFiles = remember(torrent) {
        torrent?.file_stats.orEmpty()
            .filter { it.ext in VIDEO_EXTENSIONS }
            .sortedByDescending { it.length }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title") {
            Text("Торренты", style = MaterialTheme.typography.headlineMedium)
        }

        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сервер TorrServe", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        checking -> "Проверяем $savedUrl…"
                        version != null -> "Подключено: $savedUrl · $version"
                        else -> "Сервер не найден по адресу $savedUrl. Запустите TorrServe " +
                            "на этом ТВ или в локальной сети и укажите его адрес ниже."
                    },
                    color = TextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = "http://127.0.0.1:8090",
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    AppButton(onClick = {
                        if (urlInput.isNotBlank()) {
                            scope.launch {
                                container.settings.setTorrserveUrl(urlInput)
                                urlInput = ""
                            }
                        }
                    }) { Text("Сохранить") }
                }
            }
        }

        item(key = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Добавить раздачу", style = MaterialTheme.typography.titleLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = magnet,
                        onValueChange = { magnet = it },
                        placeholder = "magnet:?… или ссылка на .torrent",
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                    AppButton(onClick = { addLink(magnet) }) { Text("Добавить") }
                }
            }
        }

        if (loading) {
            item(key = "loading") {
                Text("Получаем список файлов… (до минуты)", color = TextMuted)
            }
        }

        addError?.let {
            item(key = "add-error") { Text(it, color = TextMuted) }
        }

        torrent?.let { t ->
            item(key = "torrent-title") {
                Text(t.displayName, style = MaterialTheme.typography.titleLarge)
            }
            if (videoFiles.isEmpty()) {
                item(key = "no-video") {
                    Text("В раздаче не найдено видеофайлов.", color = TextMuted)
                }
            }
            item(key = "video-browser") {
                VideoFileBrowser(videoFiles, t.hash, onPlay = { file ->
                    scope.launch { onPlay(container.torrserve.streamUrl(t.hash, file), file.fileName) }
                }, modifier = Modifier.fillMaxWidth().height(480.dp))
            }
        }

        item(key = "examples-title") {
            Text("Легальные примеры (для проверки)", style = MaterialTheme.typography.titleLarge)
        }
        items(LEGAL_EXAMPLES, key = { it.link }) { ex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(ex.name, modifier = Modifier.fillMaxWidth(0.6f))
                AppButton(onClick = { addLink(ex.link) }) { Text("Загрузить") }
            }
        }

        item(key = "spacer") { Spacer(Modifier.width(1.dp)) }
    }
}
