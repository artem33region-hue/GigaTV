package com.artem.gigatv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.artem.gigatv.data.update.UpdateInfo
import kotlinx.coroutines.launch
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.artem.gigatv.data.DeepLink
import com.artem.gigatv.data.PlayRequest
import com.artem.gigatv.data.catalog.CatalogMovie
import com.artem.gigatv.data.playlist.PlaylistItem
import com.artem.gigatv.ui.categories.CategoriesScreen
import com.artem.gigatv.ui.details.CatalogDetailsScreen
import com.artem.gigatv.ui.details.LocalDetailsScreen
import com.artem.gigatv.ui.details.TorrentSearchSpec
import com.artem.gigatv.ui.favorites.FavoritesScreen
import com.artem.gigatv.ui.home.HomeScreen
import com.artem.gigatv.ui.player.PlayerScreen
import com.artem.gigatv.ui.releases.ReleasesScreen
import com.artem.gigatv.ui.search.SearchScreen
import com.artem.gigatv.ui.settings.SettingsScreen
import com.artem.gigatv.ui.sources.SourcesScreen
import com.artem.gigatv.ui.torrents.TorrentsScreen
import com.artem.gigatv.ui.torrents.ResumeTorrentScreen
import com.artem.gigatv.data.torrserve.torrentStreamHash
import com.artem.gigatv.ui.theme.KinoTheme

class MainActivity : ComponentActivity() {

    /** Полезная нагрузка ссылки gigatv://play/… — ждёт, пока навигация будет готова. */
    private val pendingPlay = mutableStateOf<String?>(null)

    /** Запрос из системного поиска телевизора. */
    private val pendingQuery = mutableStateOf<String?>(null)

    private fun readPlayLink(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "gigatv" || uri.host != "play") return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    private fun readQuery(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEARCH &&
            intent?.action != "com.google.android.gms.actions.SEARCH_ACTION"
        ) return null
        return intent.getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // launchMode=singleTask: повторный переход приходит сюда, а не в onCreate.
        readPlayLink(intent)?.let { pendingPlay.value = it }
        readQuery(intent)?.let { pendingQuery.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // singleTask хранит исходный intent в задаче: без проверки пересоздание
        // (смерть процесса, смена конфигурации) заново открывало бы плеер/поиск.
        if (savedInstanceState == null) {
            pendingPlay.value = readPlayLink(intent)
            pendingQuery.value = readQuery(intent)
        }
        setContent {
            KinoTheme {
                // tv-material берёт цвет текста из LocalContentColor, а тот по
                // умолчанию чёрный. Компоненты-Surface (Card, Button) задают его
                // сами, но заголовки вне них иначе рисуются чёрным по чёрному.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AppNav(pendingPlay, pendingQuery)
                    }
                }
            }
        }
    }
}

private fun NavHostController.openMovie(source: String, id: Int, series: Boolean) {
    navigate("movie/$source/$id?s=${if (series) 1 else 0}")
}

private fun NavHostController.openLocal(item: PlaylistItem) {
    navigate(
        "local/" + DeepLink.encode(
            DeepLink.json.encodeToString(PlaylistItem.serializer(), item)
        )
    )
}

private fun NavHostController.openPlayer(item: PlaylistItem) {
    openPlayer(item.url, item.title, item.poster)
}

private fun NavHostController.openPlayer(url: String, title: String, poster: String? = null) {
    navigate("player/" + DeepLink.encodePlay(PlayRequest(url, title, poster)))
}

private data class MenuEntry(val route: String, val label: String, val icon: ImageVector)

private val MENU = listOf(
    MenuEntry("home", "Главная", Icons.Default.Home),
    MenuEntry("search", "Поиск", Icons.Default.Search),
    MenuEntry("categories", "Категории", Icons.AutoMirrored.Filled.List),
    MenuEntry("favorites", "Избранное", Icons.Default.Favorite),
    MenuEntry("torrents", "Торренты", Icons.Default.Share),
    MenuEntry("sources", "Источники", Icons.Default.PlayArrow),
    MenuEntry("settings", "Настройки", Icons.Default.Settings)
)

/** Ширина свёрнутой полосы меню — на неё сдвигается контент, чтобы не уезжать под неё. */
private val RAIL_WIDTH = 64.dp

/**
 * Ширина пункта в развёрнутом меню.
 *
 * Свои строки вместо `NavigationDrawerItem`: тот жёстко берёт ширину из
 * `ExpandedDrawerItemWidth` (около 268dp), а попытка задать её модификатором
 * ломает разметку — панель остаётся широкой, а подписи пропадают.
 */
private val MENU_ITEM_WIDTH = 190.dp
private val MENU_ROW_HEIGHT = 48.dp

@Composable
private fun MenuRow(
    entry: MenuEntry,
    expanded: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.onSurface
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.surface
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .width(if (expanded) MENU_ITEM_WIDTH else MENU_ROW_HEIGHT)
            .height(MENU_ROW_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(MENU_ROW_HEIGHT / 2))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(entry.icon, contentDescription = entry.label, tint = fg)
        if (expanded) {
            Text(
                entry.label,
                color = fg,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
fun AppNav(
    pendingPlay: MutableState<String?> = remember { mutableStateOf(null) },
    pendingQuery: MutableState<String?> = remember { mutableStateOf(null) }
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        context.appContainer().updates.check().onSuccess { availableUpdate = it }
    }
    availableUpdate?.let { info ->
        DisposableEffect(info) {
            val dialog = android.app.AlertDialog.Builder(context)
                .setTitle("Доступно обновление GigaTV ${info.versionName}")
                .setMessage(info.notes.ifBlank { "Скачать и установить новую версию?" })
                .setNegativeButton("Позже") { _, _ -> availableUpdate = null }
                .setPositiveButton("Обновить") { _, _ ->
                    availableUpdate = null
                    updateScope.launch {
                        context.appContainer().updates.download(info).onFailure {
                            android.widget.Toast.makeText(context,
                                "Не удалось обновить: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setOnCancelListener { availableUpdate = null }
                .create()
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route?.substringBefore('?') ?: "home"
    val topLevel = MENU.any { it.route == route }

    // Пришли из карточки на домашнем экране — сразу открываем плеер.
    LaunchedEffect(pendingPlay.value) {
        pendingPlay.value?.let { payload ->
            pendingPlay.value = null
            if (DeepLink.decodePlay(payload) != null) nav.navigate("player/$payload")
        }
    }

    // Пришли из системного поиска — открываем экран поиска с готовым запросом.
    LaunchedEffect(pendingQuery.value) {
        pendingQuery.value?.let { q ->
            pendingQuery.value = null
            nav.navigate("search?q=" + Uri.encode(q))
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val currentEntryFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }
    var focusTransfer by remember { mutableStateOf(false) }
    var menuSelection by remember { mutableStateOf(0) }

    // При возврате на верхний уровень (например, из плеера) фокус по умолчанию
    // падает на первый фокусируемый элемент — полосу меню, и она раскрывается
    // сама. Один requestFocus не спасает: в момент смены маршрута старый экран
    // ещё держит фокус, и система переназначает его позже. Поэтому короткая
    // серия попыток — но меню принудительно закрываем лишь один раз и выходим,
    // как только фокус дошёл до контента или пользователь сам открыл меню:
    // иначе цикл ~600 мс отбирал ввод и захлопывал меню до четырёх раз.
    LaunchedEffect(route, menuSelection) {
        if (topLevel) {
            drawerState.setValue(DrawerValue.Closed)
            repeat(5) {
                if (contentHasFocus && it > 0 || !focusTransfer && drawerState.currentValue == DrawerValue.Open) {
                    focusTransfer = false
                    return@LaunchedEffect
                }
                runCatching { contentFocus.requestFocus() }
                kotlinx.coroutines.delay(120)
            }
            focusTransfer = false
        }
    }

    fun goTo(dest: String) {
        focusTransfer = true
        menuSelection++
        drawerState.setValue(DrawerValue.Closed)
        if (route == dest) return
        nav.navigate(dest) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Меню раскрывается фокусом: стрелка влево на крайнем левом элементе экрана
    // уводит фокус в полосу меню, и она разворачивается. Отдельная кнопка не нужна.
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            // На деталях и в плеере фокусируемых элементов в меню нет, поэтому
            // стрелка влево там работает как обычно и ничего не открывает.
            if (topLevel) {
                // Состояние меню читаем здесь, а не в теле AppNav: иначе открытие
                // перекомпонует весь NavHost вместе с рядами постеров, и анимация
                // проседает до ~117 мс на кадр вместо 8 мс.
                LaunchedEffect(drawerValue, route) {
                    if (drawerValue == DrawerValue.Open && !focusTransfer) {
                        runCatching { currentEntryFocus.requestFocus() }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        // Без непрозрачного фона контент просвечивает сквозь меню.
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 24.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                ) {
                    MENU.forEach { entry ->
                        val isCurrent = route == entry.route
                        MenuRow(
                            entry = entry,
                            expanded = drawerValue == DrawerValue.Open,
                            selected = isCurrent,
                            modifier = if (isCurrent) {
                                Modifier.focusRequester(currentEntryFocus)
                            } else {
                                Modifier
                            },
                            onClick = { goTo(entry.route) }
                        )
                    }
                }
            }
        }
    ) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (topLevel) RAIL_WIDTH else 0.dp)
                // focusGroup: requestFocus на контейнере передаёт фокус первому
                // элементу контента.
                .focusRequester(contentFocus)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .focusGroup()
        ) {
            composable("home") {
                HomeScreen(
                    onOpenMovie = { m: CatalogMovie -> nav.openMovie(m.source, m.id, m.isSeries) },
                    onOpenLocal = { item -> nav.openLocal(item) },
                    onResume = { url, title, poster -> nav.openPlayer(url, title, poster) },
                    onOpenFavorites = { goTo("favorites") }
                )
            }
            composable("search?q={q}") { entry ->
                SearchScreen(
                    onOpenMovie = { m: CatalogMovie -> nav.openMovie(m.source, m.id, m.isSeries) },
                    onOpenLocal = { item -> nav.openLocal(item) },
                    initialQuery = entry.arguments?.getString("q").orEmpty()
                )
            }
            composable("categories") {
                CategoriesScreen(
                    onOpenMovie = { m: CatalogMovie -> nav.openMovie(m.source, m.id, m.isSeries) }
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    onOpenMovie = { source, id, series -> nav.openMovie(source, id, series) },
                    onOpenLocal = { item -> nav.openLocal(item) }
                )
            }
            composable("torrents") {
                TorrentsScreen(onPlay = { url, title -> nav.openPlayer(url, title) })
            }
            composable("sources") { SourcesScreen() }
            composable("settings") { SettingsScreen() }

            composable("movie/{source}/{id}?s={s}") { entry ->
                val source = entry.arguments?.getString("source") ?: return@composable
                val id = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                val series = entry.arguments?.getString("s") == "1"
                CatalogDetailsScreen(
                    source = source,
                    movieId = id,
                    series = series,
                    onPlay = { item -> nav.openPlayer(item) },
                    onOpenReleases = { spec ->
                        nav.navigate(
                            "releases/" + DeepLink.encode(
                                DeepLink.json.encodeToString(TorrentSearchSpec.serializer(), spec)
                            )
                        )
                    }
                )
            }
            composable("releases/{data}") { entry ->
                val data = entry.arguments?.getString("data") ?: return@composable
                val spec = runCatching {
                    DeepLink.json.decodeFromString(TorrentSearchSpec.serializer(), DeepLink.decode(data))
                }.getOrNull() ?: return@composable
                ReleasesScreen(spec = spec, onPlay = { nav.openPlayer(it) })
            }
            composable("local/{data}") { entry ->
                val data = entry.arguments?.getString("data") ?: return@composable
                val item = runCatching {
                    DeepLink.json.decodeFromString(PlaylistItem.serializer(), DeepLink.decode(data))
                }.getOrNull() ?: return@composable
                LocalDetailsScreen(item = item, onPlay = { nav.openPlayer(it) })
            }
            composable("torrent-files/{data}") { entry ->
                val data = entry.arguments?.getString("data") ?: return@composable
                val request = DeepLink.decodePlay(data) ?: return@composable
                ResumeTorrentScreen(request,
                    onBack = { nav.popBackStack() },
                    onPlay = { nav.openPlayer(it.url, it.title, it.poster) },
                    onOpenReleases = { spec ->
                        if (nav.currentBackStackEntry == entry) {
                            nav.navigate("releases/" + DeepLink.encode(
                                DeepLink.json.encodeToString(TorrentSearchSpec.serializer(), spec)
                            )) {
                                popUpTo(entry.destination.id) { inclusive = true }
                            }
                        }
                    })
            }
            composable("player/{data}") { entry ->
                val data = entry.arguments?.getString("data") ?: return@composable
                val request = DeepLink.decodePlay(data) ?: return@composable
                PlayerScreen(url = request.url, title = request.title, poster = request.poster,
                    onBack = {
                        if (nav.currentBackStackEntry == entry) {
                            val previous = nav.previousBackStackEntry?.destination?.route
                            val hasFiles = previous == "releases/{data}" || previous == "torrents" ||
                                previous == "torrent-files/{data}"
                            if (!hasFiles && torrentStreamHash(request.url) != null) {
                                nav.navigate("torrent-files/$data") {
                                    popUpTo(entry.destination.id) { inclusive = true }
                                }
                            } else if (!nav.popBackStack()) nav.navigate("home")
                        }
                    })
            }
        }

        // Back при открытом меню закрывает меню, а не всё приложение.
        // Строго после NavHost: OnBackPressedDispatcher отдаёт приоритет
        // последнему зарегистрированному колбэку, иначе на любом экране,
        // кроме главного, Back уводил бы на «Главную» вместо закрытия меню.
        BackHandler(enabled = topLevel && drawerState.currentValue == DrawerValue.Open) {
            drawerState.setValue(DrawerValue.Closed)
            runCatching { contentFocus.requestFocus() }
        }
    }
}
