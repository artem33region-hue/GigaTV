package com.artem.gigatv.data.torrserve

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun isLocalTorrentStream(url: String, server: String): Boolean {
    val stream = url.toHttpUrlOrNull() ?: return false
    val base = server.toHttpUrlOrNull() ?: return false
    val loopback = setOf("127.0.0.1", "localhost", "::1")
    return stream.host in loopback && base.host in loopback &&
        stream.port == base.port && stream.scheme == base.scheme && torrentStreamHash(url) != null
}

/** Start only a local dependency and notify the caller once it is listening. */
internal suspend fun ensureTorrServe(
    local: Boolean,
    probe: suspend () -> Boolean,
    launch: () -> Boolean,
    onServerStarted: () -> Unit,
    timeoutMs: Long = 15_000
): Boolean {
    if (!local || probe()) return true
    if (!launch()) return false
    val ready = withTimeoutOrNull(timeoutMs) {
        while (!probe()) delay(500)
        true
    } ?: false
    if (ready) onServerStarted()
    return ready
}
