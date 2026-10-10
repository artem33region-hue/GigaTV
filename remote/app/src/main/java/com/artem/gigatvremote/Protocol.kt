package com.artem.gigatvremote

import kotlinx.serialization.Serializable

/**
 * Протокол обмена с GigaTV. Оригинал живёт в проекте телевизора
 * (`apk/.../data/remote/RemoteProtocol.kt`) — менять синхронно.
 */

@Serializable
data class TrackDto(
    val group: Int,
    val index: Int,
    val label: String,
    val detail: String? = null,
    val selected: Boolean = false,
)

@Serializable
data class RemoteState(
    val active: Boolean = false,
    val title: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val buffered: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val resize: String = "fit",
    val video: String? = null,
    val audio: List<TrackDto> = emptyList(),
    val subs: List<TrackDto> = emptyList(),
    val subsOff: Boolean = true,
    val quality: List<TrackDto> = emptyList(),
    val qualityAuto: Boolean = true,
)

@Serializable
data class RemoteCommand(
    val cmd: String,
    val group: Int = -1,
    val index: Int = -1,
    val to: Long = -1,
    val by: Long = 0,
    val value: Float = 1f,
    val mode: String? = null,
)
