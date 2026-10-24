package com.artem.gigatv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.BuildConfig
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.catalog.CatalogRepository
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.AppTextField
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val container = LocalContext.current.appContainer()
    val scope = rememberCoroutineScope()

    val savedTmdbKey by container.settings.tmdbApiKey.collectAsState(initial = "")
    val activeCatalog by container.catalog.active.collectAsState()

    val savedJacred by container.settings.jacredUrl.collectAsState(initial = "")

    var tmdbKeyInput by remember { mutableStateOf("") }
    var jacredInput by remember { mutableStateOf("") }

    val savedUpdateUrl by container.settings.updateUrl.collectAsState(initial = "")
    var updateInput by remember { mutableStateOf("") }
    var updateReport by remember { mutableStateOf<String?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var historyReport by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title") {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }

        item(key = "catalog-switch") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Каталог фильмов", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (activeCatalog) {
                        CatalogRepository.SRC_TMDB -> "Активный каталог: TMDB"
                        CatalogRepository.SRC_CINEMETA -> "Активный каталог: Cinemeta"
                        else -> "Каталог не активен"
                    },
                    color = TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(onClick = {
                        scope.launch { container.settings.setCatalogSource(CatalogRepository.SRC_TMDB) }
                    }) { Text("TMDB") }
                    AppButton(onClick = {
                        scope.launch {
                            container.settings.setCatalogSource(CatalogRepository.SRC_CINEMETA)
                        }
                    }) { Text("Cinemeta") }
                }
            }
        }

        item(key = "tmdb-key") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ключ TMDB API", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (savedTmdbKey.isBlank())
                        "Ключ не задан."
                    else
                        "Ключ задан: ${savedTmdbKey.take(4)}…${savedTmdbKey.takeLast(4)}",
                    color = TextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = tmdbKeyInput,
                        onValueChange = { tmdbKeyInput = it },
                        placeholder = "Вставьте ключ TMDB…",
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    AppButton(
                        onClick = {
                            if (tmdbKeyInput.isNotBlank()) {
                                scope.launch {
                                    container.settings.setTmdbApiKey(tmdbKeyInput)
                                    tmdbKeyInput = ""
                                }
                            }
                        }
                    ) { Text("Сохранить") }
                }
            }
        }

        item(key = "jacred") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Поиск раздач (JacRed)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Адрес: $savedJacred",
                    color = TextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = jacredInput,
                        onValueChange = { jacredInput = it },
                        placeholder = "https://jac.red",
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    AppButton(onClick = {
                        if (jacredInput.isNotBlank()) {
                            scope.launch {
                                container.settings.setJacredUrl(jacredInput)
                                jacredInput = ""
                            }
                        }
                    }) { Text("Сохранить") }
                }
            }
        }

        item(key = "history") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("История просмотра", style = MaterialTheme.typography.titleLarge)
                Text(
                    historyReport ?: "",
                    color = TextMuted
                )
                AppButton(onClick = {
                    scope.launch {
                        container.history.clear()
                        historyReport = "История очищена."
                    }
                }) { Text("Очистить историю") }
            }
        }

        item(key = "update") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Обновление приложения", style = MaterialTheme.typography.titleLarge)
                Text(
                    updateReport ?: if (savedUpdateUrl.isBlank()) "Адрес не задан." else "Адрес: $savedUpdateUrl",
                    color = TextMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextField(
                        value = updateInput,
                        onValueChange = { updateInput = it },
                        placeholder = "https://…/gigatv.json",
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    AppButton(onClick = {
                        if (updateInput.isNotBlank()) {
                            scope.launch {
                                container.settings.setUpdateUrl(updateInput)
                                updateInput = ""
                            }
                        }
                    }) { Text("Сохранить") }
                }
                AppButton(enabled = !updateBusy, onClick = update@ {
                    if (updateBusy) return@update
                    updateBusy = true
                    scope.launch {
                        try {
                            updateReport = "Проверяем…"
                            container.updates.check()
                                .onSuccess { info ->
                                    when {
                                        savedUpdateUrl.isBlank() -> updateReport = "Сначала укажите адрес."
                                        info == null -> updateReport = "Установлена последняя версия " +
                                            BuildConfig.VERSION_NAME + "."
                                        else -> {
                                            val v = info.versionName
                                            updateReport = "Найдена версия $v, скачиваем…"
                                            container.updates.download(info).fold(
                                                onSuccess = {
                                                    updateReport = "Найдена версия $v, запускаем установку…"
                                                },
                                                onFailure = { e ->
                                                    updateReport = "Не удалось скачать: ${e.message}"
                                                }
                                            )
                                        }
                                    }
                                }
                                .onFailure { updateReport = "Проверка не удалась: ${it.message}" }
                        } finally {
                            updateBusy = false
                        }
                    }
                }) { Text(if (updateBusy) "Обновляем…" else "Проверить обновление") }
            }
        }

        item(key = "remote-access") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Телефонный пульт", style = MaterialTheme.typography.titleLarge)
                Text("Новый телефон подключается после подтверждения на телевизоре.", color = TextMuted)
                AppButton(onClick = { container.remote.revokeAll() }) {
                    Text("Отключить все телефонные пульты")
                }
            }
        }
        item(key = "about") {
            Text(
                "GigaTV ${BuildConfig.VERSION_NAME}",
                color = TextMuted
            )
        }
    }
}
