package com.artem.gigatv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.artem.gigatv.data.DeepLink
import com.artem.gigatv.data.PlayRequest
import com.artem.gigatv.data.SettingsRepository
import com.artem.gigatv.data.catalog.CatalogCache
import com.artem.gigatv.data.catalog.CatalogRepository
import com.artem.gigatv.data.channels.ChannelsRepository
import com.artem.gigatv.data.cinemeta.CinemetaRepository
import com.artem.gigatv.data.diag.Diagnostics
import com.artem.gigatv.data.favorites.FavoritesRepository
import com.artem.gigatv.data.history.WatchHistoryRepository
import com.artem.gigatv.data.jacred.JacredRepository
import com.artem.gigatv.data.playlist.PlaylistRepository
import com.artem.gigatv.data.remote.RemoteControlServer
import com.artem.gigatv.data.remote.registerRemoteService
import com.artem.gigatv.data.remote.RemoteDiscoveryResponder
import com.artem.gigatv.data.search.SearchHistoryRepository
import com.artem.gigatv.data.tmdb.TmdbRepository
import com.artem.gigatv.data.update.UpdateRepository
import com.artem.gigatv.data.torrserve.TorrServeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Осмысленный User-Agent обязателен: Wikimedia и ряд других CDN отвечают
 * 429 на запросы с дефолтными UA http-библиотек.
 */
val APP_USER_AGENT = "GigaTV/${BuildConfig.VERSION_NAME} (personal Android TV media player)"

class KinoApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", APP_USER_AGENT)
                                .build()
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
}

class AppContainer(context: Context) {

    private companion object {
        /**
         * Как часто пересобирать ряд «Смотреть далее». Чаще не нужно: в ряду
         * видна полоса прогресса, а не секунды.
         */
        const val WATCH_NEXT_SYNC_INTERVAL_MS = 60_000L
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsRepository(context)
    val tmdb = TmdbRepository(settings, appScope)
    val cinemeta = CinemetaRepository()
    val catalog = CatalogRepository(settings, tmdb, cinemeta, CatalogCache(context), appScope)
    val playlists = PlaylistRepository(settings)
    val favorites = FavoritesRepository(context)
    val torrserve = TorrServeRepository(settings)
    val jacred = JacredRepository(settings)
    val history = WatchHistoryRepository(context)
    val searchHistory = SearchHistoryRepository(context)
    val channels = ChannelsRepository(context)
    val updates = UpdateRepository(context, settings)
    val diag = Diagnostics(context)

    /** Сервер для приложения-пульта на телефоне. */
    val remote = RemoteControlServer()
    private val remoteDiscovery = RemoteDiscoveryResponder()

    init {
        diag.note("запуск ${BuildConfig.VERSION_NAME}")
        diag.startWatchdog()
        // Пульт не критичен: занятый порт или странная прошивка не должны
        // ронять приложение. Конечный read-таймаут в паре с ping'ами сервера
        // хоронит соединения пропавших телефонов (см. RemoteControlServer).
        runCatching { remote.start(RemoteControlServer.READ_TIMEOUT_MS, true) }
        registerRemoteService(context)
        remoteDiscovery.start()
        appScope.launch { playlists.refresh() }
        // Ряд «Продолжить» на домашнем экране держим в актуальном состоянии:
        // история и так живёт во Flow, отдельного триггера не нужно.
        //
        // Но не на каждой эмиссии: плеер пишет позицию каждые 5 секунд, а один
        // проход sync — это обход всей системной таблицы WatchNext плюс вставка
        // каждой своей карточки заново. Выходило 1440 полных пересборок ряда за
        // двухчасовой фильм; приложение задыхалось на мусоре и перестало
        // отвечать на пульт. Дедупликация здесь не помогает: position и
        // updatedAt меняются на каждом тике, так что каждая эмиссия честно новая.
        //
        // conflate + пауза после прохода: накопившееся сливается, а ряд
        // обновляется раз в минуту и всегда самым свежим состоянием.
        appScope.launch {
            history.inProgress.conflate().collect { items ->
                channels.sync(items) { w ->
                    DeepLink.encodePlay(PlayRequest(w.url, w.title, w.poster))
                }
                delay(WATCH_NEXT_SYNC_INTERVAL_MS)
            }
        }
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as KinoApp).container
