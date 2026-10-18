package com.artem.gigatv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.catalog.CatalogMovie
import com.artem.gigatv.data.catalog.CatalogRepository
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.ui.components.AppButton
import com.artem.gigatv.ui.components.AppTextField
import com.artem.gigatv.ui.components.MovieCard
import com.artem.gigatv.ui.components.RowTitle
import com.artem.gigatv.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Composable
fun SearchScreen(
    onOpenMovie: (CatalogMovie) -> Unit,
    onOpenLocal: (PlaylistItem) -> Unit,
    /** Запрос из системного поиска телевизора; пусто — обычный вход в экран. */
    initialQuery: String = ""
) {
    val container = LocalContext.current.appContainer()
    val activeCatalog by container.catalog.active.collectAsState()
    val playlists by container.playlists.playlists.collectAsState()
    val history by container.searchHistory.queries.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var catalogResults by remember { mutableStateOf<List<CatalogMovie>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    /**
     * Показывать ли блок истории. Отдельное состояние, а не «поле пустое»:
     * выбранный из истории запрос не должен убирать её из-под фокуса — иначе
     * фокусу некуда деться и поверх результатов распахивается боковое меню.
     */
    var historyVisible by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.isBlank()) }
    val fieldFocus = remember { FocusRequester() }
    val clearHistoryFocus = remember { FocusRequester() }
    val historyFocus = remember(history) { history.associateWith { FocusRequester() } }
    var consumeLongPressRelease by remember { mutableStateOf(false) }

    val localResults = remember(query, playlists) { container.playlists.search(query) }

    /**
     * Запрос запоминаем не по каждому нажатию клавиши, а только когда он себя
     * оправдал: пользователь открыл найденное или явно завершил ввод. Иначе в
     * историю попадали бы огрызки вроде «Бэк», набранные по пути к «Бэкрумс».
     */
    fun keepQuery(value: String = query) {
        scope.launch { container.searchHistory.add(value) }
    }

    // Запрос из системного поиска телевизора — тоже осознанный, сохраняем сразу.
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) container.searchHistory.add(initialQuery)
    }

    LaunchedEffect(query, activeCatalog, retry) {
        searchError = null
        if (query.trim().length < 2 || activeCatalog == CatalogRepository.NONE) {
            catalogResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        catalogResults = emptyList()
        try {
            delay(500)
            catalogResults = container.catalog.search(query.trim())
                .distinctBy { Triple(it.source, it.isSeries, it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("GigaTvSearch", "Catalog search failed", e)
            searchError = "Поиск не удался: ${e.message ?: "нет связи"}"
        } finally {
            if (isActive) searching = false
        }
    }

    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (confirm && consumeLongPressRelease) {
            if (event.type == KeyEventType.KeyUp) consumeLongPressRelease = false
            true
        } else false
    }) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 76.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "input") {
                Column {
                    Text(
                        "Поиск",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    AppTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            // Набирают вручную — история уступает место результатам,
                            // а на очищенном поле возвращается.
                            historyVisible = it.isBlank()
                        },
                        placeholder = "Название фильма…",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .focusRequester(fieldFocus)
                            .onPreviewKeyEvent { event ->
                                if (historyVisible && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    (history.firstOrNull()?.let { historyFocus[it] } ?: clearHistoryFocus).requestFocus()
                                    true
                                } else false
                            },
                        onDone = { keepQuery() }
                    )
                }
            }

            // Пока ничего не ищем — на пустом месте показываем прошлые запросы.
            if (historyVisible) {
                item(key = "history") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RowTitle("Недавние запросы")
                        if (history.isEmpty()) Text("История поиска пуста", color = TextMuted)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(history, key = { it }) { past ->
                                AppButton(
                                    modifier = Modifier.focusRequester(historyFocus.getValue(past)),
                                    onClick = { query = past },
                                    onLongClick = {
                                        consumeLongPressRelease = true
                                        val index = history.indexOf(past)
                                        val next = history.getOrNull(index + 1) ?: history.getOrNull(index - 1)
                                        // Keep focus among history actions, never in the search field.
                                        runCatching {
                                            (next?.let { historyFocus[it] } ?: clearHistoryFocus).requestFocus()
                                        }
                                        scope.launch { container.searchHistory.remove(past) }
                                    }
                                ) { Text(past) }
                            }
                        }
                        AppButton(modifier = Modifier.focusRequester(clearHistoryFocus), onClick = {
                            scope.launch { container.searchHistory.clear() }
                        }) { Text("Очистить историю") }
                    }
                }
            }

            if (localResults.isNotEmpty()) {
                item(key = "local") {
                    Column {
                        RowTitle("В моей медиатеке")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(localResults) { entry ->
                                MovieCard(
                                    title = entry.title,
                                    posterUrl = entry.poster,
                                    subtitle = entry.year?.toString(),
                                    onClick = {
                                        keepQuery()
                                        onOpenLocal(entry)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (catalogResults.isNotEmpty()) {
                item(key = "catalog") {
                    Column {
                        RowTitle(
                            if (activeCatalog == CatalogRepository.SRC_CINEMETA) "В каталоге Cinemeta"
                            else "В каталоге TMDB"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(catalogResults, key = { "${it.source}-${it.isSeries}-${it.id}" }) { movie ->
                                MovieCard(
                                    title = movie.title,
                                    posterUrl = movie.poster,
                                    subtitle = movie.subtitle,
                                    onClick = {
                                        keepQuery()
                                        onOpenMovie(movie)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "status") {
                when {
                    searching -> Text("Ищем…", color = TextMuted)
                    searchError != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(searchError.orEmpty(), color = TextMuted)
                        AppButton(onClick = { retry++ }) { Text("Повторить") }
                    }
                    query.trim().length >= 2 && localResults.isEmpty() && catalogResults.isEmpty() ->
                        Text("Ничего не найдено.", color = TextMuted)
                    activeCatalog == CatalogRepository.NONE ->
                        Text("Поиск по вашей медиатеке работает всегда.", color = TextMuted)
                }
            }
        }
        if (historyVisible) {
            Text("Долгое нажатие на запросе — удалить его из истории · Последние 10 запросов",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 48.dp, vertical = 20.dp))
        }
    }
}
