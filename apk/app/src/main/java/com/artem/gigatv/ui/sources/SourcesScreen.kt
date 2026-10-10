package com.artem.gigatv.ui.sources

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
import androidx.compose.runtime.Composable
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
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.AppTextField
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SourcesScreen() {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()

    val urls by container.settings.playlistUrls.collectAsState(initial = emptyList())
    val refreshing by container.playlists.refreshing.collectAsState()
    val errors by container.playlists.errors.collectAsState()

    var newUrl by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title") {
            Text("Источники", style = MaterialTheme.typography.headlineMedium)
        }

        if (urls.isEmpty()) {
            item(key = "empty") {
                Text("Пока не добавлено ни одного плейлиста.", color = TextMuted)
            }
        }

        items(urls.size, key = { "url-$it-${urls[it]}" }) { index ->
            val url = urls[index]
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
                AppButton(onClick = {
                    scope.launch {
                        container.settings.removePlaylistUrl(url)
                        container.playlists.refresh()
                    }
                }) { Text("Удалить") }
            }
        }

        item(key = "add-url") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    placeholder = "https://…/playlist.json",
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
                AppButton(
                    onClick = {
                        if (newUrl.isNotBlank()) {
                            scope.launch {
                                container.settings.addPlaylistUrl(newUrl)
                                newUrl = ""
                                container.playlists.refresh()
                            }
                        }
                    }
                ) { Text("Добавить") }
            }
        }

        if (refreshing) {
            item(key = "refreshing") { Text("Обновляем плейлисты…", color = TextMuted) }
        }

        if (errors.isNotEmpty()) {
            item(key = "errors") {
                Column {
                    errors.forEach { Text("Ошибка: $it", color = TextMuted) }
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.width(1.dp)) }
    }
}
