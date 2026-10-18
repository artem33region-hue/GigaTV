package com.artem.gigatv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import android.widget.Button
import android.widget.FrameLayout
import android.view.Gravity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.artem.gigatv.APP_USER_AGENT
import com.artem.gigatv.appContainer
import com.artem.gigatv.data.remote.RemoteSession
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(url: String, title: String, poster: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer()
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    BackHandler {
        if (showSettings) showSettings = false else onBack()
    }

    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(APP_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        // CDN (например, Wikimedia) может отвечать 429 на всплески range-запросов —
        // вместо ошибки ждём и повторяем с нарастающей задержкой.
        val retryPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val cause = loadErrorInfo.exception
                if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 429) {
                    return min(1500L * loadErrorInfo.errorCount, 8000L)
                }
                return super.getRetryDelayMsFor(loadErrorInfo)
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 6
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
                    .setLoadErrorHandlingPolicy(retryPolicy)
            )
            // Аудиофокус: при звонке или чужом звуке паузим, потом продолжаем.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Шаг перемотки кнопками пульта: 30 с вперёд оказалось слишком
            // размашисто, 10 с в обе стороны удобнее.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                // При нескольких аудиодорожках по умолчанию берём русскую.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("ru")
                    .build()
                // Держим процессор и Wi-Fi живыми, пока идёт сетевой поток, иначе
                // буферизация рвётся при засыпании. try-catch на случай, если
                // разрешение WAKE_LOCK почему-то не выдано — плеер важнее.
                runCatching { setWakeMode(C.WAKE_MODE_NETWORK) }
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                        .build()
                )
                addListener(object : Player.Listener {
                    override fun onPlayerError(e: PlaybackException) {
                        error = "Ошибка воспроизведения: ${e.errorCodeName}"
                        // Текст на экране живёт до выхода из плеера и никуда не
                        // попадает; в журнале он остаётся с временем и позицией.
                        container.diag.note(
                            "ошибка плеера: ${e.errorCodeName} на ${currentPosition / 1000} с"
                        )
                    }

                    override fun onTracksChanged(newTracks: Tracks) {
                        tracks = newTracks
                    }
                })
                prepare()
                playWhenReady = true
            }
    }

    // HOME/переход ТВ в ожидание останавливают Activity, но композиция жива:
    // без паузы звук продолжал играть поверх лончера, а WAKE_MODE_NETWORK
    // качал поток даже в standby. Обратно не возобновляем — предсказуемее.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Медиасессия живёт ровно столько же, сколько плеер: система показывает
    // название, отдаёт кнопки пульта и корректно ведёт себя с аудиофокусом.
    DisposableEffect(exoPlayer) {
        val session = MediaSession.Builder(context, exoPlayer)
            .setId("gigatv-" + System.identityHashCode(exoPlayer))
            .build()
        onDispose { session.release() }
    }

    // Пульт на телефоне: регистрируем мост и рассылаем состояние, пока экран жив.
    val remoteSession = remember {
        RemoteSession(
            player = exoPlayer,
            title = title,
            getResize = { resizeMode },
            setResize = { resizeMode = it },
            broadcast = { container.remote.broadcast(it) }
        )
    }
    DisposableEffect(Unit) {
        container.remote.playerSession = remoteSession
        onDispose {
            // close() до active:false: команда, уже отправленная в main-очередь,
            // иначе исполнится после разбора экрана и заново разошлёт active:true
            // от освобождённого плеера — телефоны зависнут на мёртвом пульте.
            remoteSession.close()
            if (container.remote.playerSession === remoteSession) {
                container.remote.playerSession = null
                container.remote.broadcast("""{"active":false}""")
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            // stateJson() собирает состояние и дорожки в главном потоке — том
            // же, что обслуживает пульт телевизора. Пока пульт-телефон не
            // подключён, частый такт не нужен: реже, но не выключаем совсем,
            // иначе GET-эндпоинт сервера отдавал бы застывшее состояние, а он
            // единственный способ посмотреть на плеер снаружи.
            container.remote.broadcast(remoteSession.stateJson())
            delay(if (container.remote.hasClients) 500 else 2_000)
        }
    }

    // Продолжить с сохранённой позиции.
    LaunchedEffect(url) {
        val saved = container.history.positionFor(url)
        if (saved > 0) exoPlayer.seekTo(saved)
    }

    // Периодически сохраняем прогресс во время воспроизведения.
    LaunchedEffect(url) {
        while (true) {
            delay(5000)
            val d = exoPlayer.duration
            val p = exoPlayer.currentPosition
            if (exoPlayer.isPlaying && d > 0 && p > 0) {
                container.history.save(url, title, poster, p, d, System.currentTimeMillis())
            }
        }
    }

    DisposableEffect(Unit) {
        container.diag.note("плеер открыт: $title")
        onDispose {
            val d = exoPlayer.duration
            val p = exoPlayer.currentPosition
            container.diag.note("плеер закрыт на ${p / 1000} с из ${d / 1000} с")
            if (d > 0 && p > 0) {
                // сохраняем финальную позицию в живущем скоупе приложения
                container.appScope.launch {
                    container.history.save(url, title, poster, p, d, System.currentTimeMillis())
                }
            }
            exoPlayer.release()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Дублирует OnKeyListener: ловит «меню», когда фокус ушёл на кнопки
            // контроллера и до PlayerView событие не доходит.
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) showSettings = true
                    true
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 3500
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)
                        ?.setOnClickListener { showSettings = true }
                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_audio_track)
                        ?.setOnClickListener { showSettings = true }
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        if (visibility != android.view.View.VISIBLE && !showSettings) {
                            post { if (!showSettings) requestFocus() }
                        }
                    })
                    // Keep the exit action in the native controller so D-pad focus
                    // and controller visibility work together.
                    val backButton = Button(ctx).apply {
                        text = "← Назад"
                        setOnClickListener { onBack() }
                    }
                    val header = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        val inset = (16 * resources.displayMetrics.density).toInt()
                        setPadding(inset, inset / 2, inset, inset / 2)
                        setBackgroundColor(android.graphics.Color.argb(190, 0, 0, 0))
                        addView(backButton)
                        addView(android.widget.TextView(ctx).apply {
                            text = title
                            textSize = 20f
                            setTextColor(android.graphics.Color.WHITE)
                            maxLines = 3
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setPadding(inset, 0, 0, 0)
                            isFocusable = false
                        }, android.widget.LinearLayout.LayoutParams(0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                        ?.addView(header, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.START
                        ))
                    // Стрелки на полосе прогресса по умолчанию мотают на 1/20
                    // длительности (~7 минут для двухчасового фильма) — ставим
                    // фиксированные 10 секунд.
                    findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
                        ?.setKeyTimeIncrement(10_000)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    // Кнопка «меню» пульта открывает панель настроек плеера.
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_MENU) {
                            if (event.action == KeyEvent.ACTION_UP) showSettings = true
                            true
                        } else false
                    }
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
                if (!showSettings) view.requestFocus()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showSettings) {
            PlayerSettingsSheet(
                player = exoPlayer,
                tracks = tracks,
                resizeMode = resizeMode,
                onResizeMode = { resizeMode = it },
                onDismiss = { showSettings = false }
            )
        }

        error?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000))
                    .padding(24.dp)
            )
        }
    }
}
