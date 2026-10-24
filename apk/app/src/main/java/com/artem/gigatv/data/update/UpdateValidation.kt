package com.artem.gigatv.data.update

import com.artem.gigatv.data.net.awaitParsed
import java.io.ByteArrayOutputStream
import okhttp3.Call

/** Bound both Content-Length and streamed/chunked responses before JSON parsing. */
internal suspend fun Call.readUpdateManifest(): String = awaitParsed { response ->
    check(response.isSuccessful) { "HTTP ${response.code}" }
    val body = response.body ?: error("Пустой манифест обновления")
    val limit = 64 * 1024L
    check(body.contentLength() <= limit) { "Манифест обновления слишком большой" }
    ByteArrayOutputStream().use { output ->
        body.byteStream().copyLimitedTo(output, limit)
        output.toString(Charsets.UTF_8.name())
    }
}

internal fun validateUpdateVersion(actual: Long, installed: Long, announced: Long) {
    check(actual > installed) { "В APK версия $actual, не новее установленной" }
    check(actual == announced) { "Версия APK не совпадает с манифестом обновления" }
}
