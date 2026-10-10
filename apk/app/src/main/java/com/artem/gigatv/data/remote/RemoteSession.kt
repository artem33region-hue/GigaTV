package com.artem.gigatv.data.remote

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale
import com.artem.gigatv.ui.player.readableTrackName
import kotlinx.serialization.json.Json

/**
 * Мост между сервером пульта и живущим на экране плеера ExoPlayer.
 * Команды приходят с потоков сервера и переносятся на главный поток;
 * [stateJson] вызывается только с главного (тикер в PlayerScreen).
 */
@OptIn(UnstableApi::class)
class RemoteSession(
    private val player: ExoPlayer,
    private val title: String,
    private val getResize: () -> Int,
    private val setResize: (Int) -> Unit,
    private val broadcast: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Ставится при разборе PlayerScreen. Команда, попавшая в main-очередь до
    // этого, но исполняющаяся после, не должна трогать освобождённый плеер и
    // перебивать финальный {"active":false} своим active:true.
    @Volatile
    private var closed = false

    fun close() {
        closed = true
    }

    fun handle(text: String) {
        val cmd = runCatching { json.decodeFromString(RemoteCommand.serializer(), text) }.getOrNull() ?: return
        main.post {
            if (closed) return@post
            runCatching {
                apply(cmd)
                // Мгновенный отклик на телефоне, не дожидаясь тикера.
                broadcast(stateJson())
            }
        }
    }

    private fun apply(c: RemoteCommand) {
        when (c.cmd) {
            "play" -> player.play()
            "pause" -> player.pause()
            "toggle" -> if (player.playWhenReady) player.pause() else player.play()
            "seek" -> if (c.to >= 0) player.seekTo(c.to)
            "seekBy" -> player.seekTo((player.currentPosition + c.by).coerceAtLeast(0))
            "speed" -> player.setPlaybackSpeed(c.value.coerceIn(0.25f, 3f))
            "resize" -> setResize(
                when (c.mode) {
                    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            )
            "audio" -> select(C.TRACK_TYPE_AUDIO, c.group, c.index)
            "subs" -> {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                select(C.TRACK_TYPE_TEXT, c.group, c.index)
            }
            "subsOff" -> player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            "quality" -> select(C.TRACK_TYPE_VIDEO, c.group, c.index)
            "qualityAuto" -> player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
        }
    }

    private fun select(type: Int, groupIndex: Int, trackIndex: Int) {
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return
        if (group.type != type || trackIndex !in 0 until group.length) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    fun stateJson(): String {
        val tracks = player.currentTracks
        val state = RemoteState(
            active = true,
            title = title,
            position = player.currentPosition.coerceAtLeast(0),
            duration = player.duration.coerceAtLeast(0),
            buffered = player.totalBufferedDuration.coerceAtLeast(0),
            playing = player.playWhenReady,
            speed = player.playbackParameters.speed,
            resize = when (getResize()) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "zoom"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "fill"
                else -> "fit"
            },
            video = player.videoFormat?.let { "${it.width}×${it.height}" },
            audio = trackDtos(tracks, C.TRACK_TYPE_AUDIO),
            subs = trackDtos(tracks, C.TRACK_TYPE_TEXT),
            subsOff = tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
            quality = trackDtos(tracks, C.TRACK_TYPE_VIDEO),
            qualityAuto = player.trackSelectionParameters.overrides.keys
                .none { it.type == C.TRACK_TYPE_VIDEO },
        )
        return json.encodeToString(RemoteState.serializer(), state)
    }

    private fun trackDtos(tracks: Tracks, type: Int): List<TrackDto> =
        tracks.groups.withIndex()
            .filter { it.value.type == type }
            .flatMap { (gi, g) ->
                (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                    val fmt = g.getTrackFormat(i)
                    TrackDto(
                        group = gi,
                        index = i,
                        label = label(type, fmt, i),
                        detail = detail(type, fmt),
                        selected = g.isTrackSelected(i),
                    )
                }
            }.mapIndexed { number, dto ->
                if (type == C.TRACK_TYPE_VIDEO) dto else {
                    val fmt = tracks.groups[dto.group].getTrackFormat(dto.index)
                    dto.copy(label = readableTrackName(fmt.label, fmt.language, "Дорожка ${number + 1}"))
                }
            }

    private fun label(type: Int, fmt: Format, index: Int): String {
        if (type == C.TRACK_TYPE_VIDEO) {
            return if (fmt.height > 0) "${fmt.height}p" else "Дорожка ${index + 1}"
        }
        return readableTrackName(fmt.label, fmt.language, "Дорожка ${index + 1}")
    }

    private fun detail(type: Int, fmt: Format): String? = when (type) {
        C.TRACK_TYPE_AUDIO -> listOfNotNull(
            when (fmt.channelCount) {
                1 -> "моно"; 2 -> "стерео"; 6 -> "5.1"; 8 -> "7.1"; else -> null
            },
            fmt.codecs ?: fmt.sampleMimeType?.removePrefix("audio/"),
            if (fmt.bitrate > 0) "${fmt.bitrate / 1000} кбит/с" else null
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

        C.TRACK_TYPE_VIDEO -> listOfNotNull(
            if (fmt.width > 0) "${fmt.width}×${fmt.height}" else null,
            if (fmt.bitrate > 0) "%.1f Мбит/с".format(fmt.bitrate / 1_000_000f) else null
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

        else -> fmt.label?.toString()
    }
}
