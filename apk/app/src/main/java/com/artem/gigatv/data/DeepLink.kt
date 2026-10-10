package com.artem.gigatv.data

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Что играть: одинаково для навигации внутри приложения и для ссылок с домашнего экрана. */
@Serializable
data class PlayRequest(
    val url: String,
    val title: String,
    val poster: String? = null
)

object DeepLink {
    val json = Json { ignoreUnknownKeys = true }

    /**
     * Что мы вообще готовы отдать плееру.
     *
     * Ссылка gigatv://play/… приходит извне — её умеет открыть и любое другое
     * приложение, и обычная ссылка в браузере, — а ExoPlayer через
     * DefaultDataSource берёт не только http(s), но и file://, content:// и
     * asset://. Ничего, кроме сети, играть мы не собираемся: и торрент через
     * TorrServe, и плейлисты, и трейлеры — это http(s).
     */
    private val PLAYABLE_SCHEMES = setOf("http", "https")

    fun isPlayable(url: String): Boolean =
        url.substringBefore(':', "").lowercase() in PLAYABLE_SCHEMES

    fun encode(value: String): String =
        Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decode(value: String): String =
        String(
            Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8
        )

    fun encodePlay(request: PlayRequest): String =
        encode(json.encodeToString(PlayRequest.serializer(), request))

    fun decodePlay(payload: String): PlayRequest? =
        runCatching { json.decodeFromString(PlayRequest.serializer(), decode(payload)) }
            .getOrNull()
            ?.takeIf { isPlayable(it.url) }
}
