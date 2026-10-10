package com.artem.gigatv.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.artem.gigatv.ui.theme.SurfaceDark
import com.artem.gigatv.ui.theme.TextMuted
import java.util.Locale

private enum class Page { Root, Audio, Subs, Speed, Zoom, Info }

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(UnstableApi::class)
private val ZOOM_MODES = listOf(
    Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Вписать", "виден весь кадр"),
    Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Заполнить", "кадр обрезается по краям"),
    Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Растянуть", "пропорции игнорируются"),
)

/**
 * Панель настроек воспроизведения: дорожки, субтитры, скорость, масштаб, инфо.
 * Открывается кнопкой «меню» пульта из [PlayerScreen].
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSettingsSheet(
    player: ExoPlayer,
    tracks: Tracks,
    resizeMode: Int,
    onResizeMode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.Root) }
    val firstFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Back из подраздела возвращает в корень, а не закрывает панель.
        BackHandler(enabled = page != Page.Root) { page = Page.Root }
        // Окно диалога не всегда само отдаёт фокус Compose-списку на ТВ.
        LaunchedEffect(page) { runCatching { firstFocus.requestFocus() } }

        Box(Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(430.dp)
                    .fillMaxHeight()
                    .background(SurfaceDark)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(
                    text = when (page) {
                        Page.Root -> "Настройки плеера"
                        Page.Audio -> "Аудиодорожка"
                        Page.Subs -> "Субтитры"
                        Page.Speed -> "Скорость"
                        Page.Zoom -> "Масштаб"
                        Page.Info -> "Информация"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                when (page) {
                    Page.Root -> RootPage(player, tracks, resizeMode, firstFocus) { page = it }
                    Page.Audio -> AudioPage(player, tracks, firstFocus) { page = Page.Root }
                    Page.Subs -> SubsPage(player, tracks, firstFocus)
                    Page.Speed -> SpeedPage(player, firstFocus)
                    Page.Zoom -> ZoomPage(resizeMode, onResizeMode, firstFocus)
                    Page.Info -> InfoPage(player, firstFocus) { page = Page.Root }
                }
            }
        }
    }
}

@Composable
private fun RootPage(
    player: ExoPlayer,
    tracks: Tracks,
    resizeMode: Int,
    firstFocus: FocusRequester,
    onOpen: (Page) -> Unit,
) {
    val audioName = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)
        ?.let { trackName(it, "Дорожка 1") } ?: "—"
    val subsName = selectedFormat(tracks, C.TRACK_TYPE_TEXT)
        ?.let { trackName(it, "Дорожка 1") } ?: "Выключены"
    val zoomName = ZOOM_MODES.firstOrNull { it.first == resizeMode }?.second ?: "Вписать"

    MenuItem("Аудиодорожка", audioName, Modifier.focusRequester(firstFocus)) { onOpen(Page.Audio) }
    MenuItem("Субтитры", subsName) { onOpen(Page.Subs) }
    MenuItem("Скорость", speedLabel(player.playbackParameters.speed)) { onOpen(Page.Speed) }
    MenuItem("Масштаб", zoomName) { onOpen(Page.Zoom) }
    MenuItem("Информация", null) { onOpen(Page.Info) }
}

@Composable
private fun AudioPage(
    player: ExoPlayer,
    tracks: Tracks,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
) {
    val options = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
    if (options.isEmpty()) {
        Text("Дорожки ещё не загружены", color = TextMuted)
        // Без фокусируемого элемента страница запирает пульт: стрелкам и OK
        // некуда идти, и панель кажется зависшей.
        BackItem(firstFocus, onBack)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            val (group, index) = options[i]
            val fmt = group.getTrackFormat(index)
            OptionItem(
                title = trackName(fmt, "Дорожка ${i + 1}"),
                subtitle = audioDetails(fmt),
                selected = group.isTrackSelected(index),
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
            ) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            }
        }
    }
}

@Composable
private fun SubsPage(player: ExoPlayer, tracks: Tracks, firstFocus: FocusRequester) {
    val options = trackOptions(tracks, C.TRACK_TYPE_TEXT)
    val noneSelected = tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size + 1) { i ->
            if (i == 0) {
                OptionItem(
                    title = "Выключены",
                    subtitle = null,
                    selected = noneSelected,
                    modifier = Modifier.focusRequester(firstFocus)
                ) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                }
            } else {
                val (group, index) = options[i - 1]
                val fmt = group.getTrackFormat(index)
                val forced = fmt.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                OptionItem(
                    title = trackName(fmt, "Дорожка $i") + if (forced) " (форсированные)" else "",
                    subtitle = fmt.label?.toString(),
                    selected = group.isTrackSelected(index)
                ) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                        .build()
                }
            }
        }
    }
}

@Composable
private fun SpeedPage(player: ExoPlayer, firstFocus: FocusRequester) {
    // Галочка идёт за фактической скоростью плеера, а не за нажатием: на
    // passthrough-дорожках (Dolby) ExoPlayer молча откатывает скорость к 1×.
    var current by remember { mutableStateOf(player.playbackParameters.speed) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                current = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    SPEEDS.forEachIndexed { i, s ->
        OptionItem(
            title = speedLabel(s),
            subtitle = null,
            selected = current == s,
            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
        ) { player.setPlaybackSpeed(s) }
    }
    Text(
        "Если звук выводится в Dolby без декодирования, скорость изменить нельзя — " +
            "переключите аудиодорожку на AAC/стерео.",
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ZoomPage(resizeMode: Int, onResizeMode: (Int) -> Unit, firstFocus: FocusRequester) {
    ZOOM_MODES.forEachIndexed { i, (mode, name, desc) ->
        OptionItem(
            title = name,
            subtitle = desc,
            selected = resizeMode == mode,
            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
        ) { onResizeMode(mode) }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InfoPage(player: ExoPlayer, firstFocus: FocusRequester, onBack: () -> Unit) {
    val v = player.videoFormat
    val a = player.audioFormat
    val lines = buildList {
        add("Видео" to (v?.let {
            "${it.width}×${it.height}" +
                (if (it.frameRate > 0) ", ${"%.3f".format(it.frameRate).trimEnd('0').trimEnd(',', '.')} к/с" else "")
        } ?: "—"))
        add("Кодек видео" to (v?.let { it.codecs ?: it.sampleMimeType } ?: "—"))
        if (v != null && v.bitrate > 0) {
            add("Битрейт видео" to "%.1f Мбит/с".format(v.bitrate / 1_000_000f))
        }
        add("Аудио" to (a?.let {
            listOfNotNull(
                it.codecs ?: it.sampleMimeType,
                channelsLabel(it.channelCount),
                if (it.sampleRate > 0) "${it.sampleRate} Гц" else null
            ).joinToString(" · ")
        } ?: "—"))
        add("Буфер" to "${player.totalBufferedDuration / 1000} с")
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { (label, value) ->
            Column {
                Text(label, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        // Страница из одних надписей не имеет куда поставить фокус, и пульт на
        // ней выглядит сломанным: реагирует только Back.
        BackItem(firstFocus, onBack)
    }
}

/** Фокусируемый выход со страниц, где больше нечего выбирать. */
@Composable
private fun BackItem(firstFocus: FocusRequester, onBack: () -> Unit) {
    MenuItem("Назад", null, Modifier.focusRequester(firstFocus).padding(top = 8.dp), onBack)
}

@Composable
private fun MenuItem(
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, color = TextMuted) } },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun OptionItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, color = TextMuted) } },
        trailingContent = if (selected) ({ Text("✓") }) else null,
        modifier = modifier.fillMaxWidth()
    )
}

/** Все поддерживаемые дорожки указанного типа: пары (группа, индекс внутри группы). */
private fun trackOptions(tracks: Tracks, type: Int): List<Pair<Tracks.Group, Int>> =
    tracks.groups.filter { it.type == type }
        .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }

private fun selectedFormat(tracks: Tracks, type: Int): Format? =
    tracks.groups.firstOrNull { it.type == type && it.isSelected }?.let { g ->
        (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
    }

private fun trackName(fmt: Format, fallback: String): String {
    return readableTrackName(fmt.label, fmt.language, fallback)
}

@OptIn(UnstableApi::class)
private fun audioDetails(fmt: Format): String? = listOfNotNull(
    channelsLabel(fmt.channelCount),
    fmt.codecs ?: fmt.sampleMimeType?.removePrefix("audio/"),
    if (fmt.bitrate > 0) "${fmt.bitrate / 1000} кбит/с" else null
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun channelsLabel(channels: Int): String? = when (channels) {
    1 -> "моно"
    2 -> "стерео"
    6 -> "5.1"
    8 -> "7.1"
    else -> null
}

private fun speedLabel(s: Float): String =
    (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "×"
