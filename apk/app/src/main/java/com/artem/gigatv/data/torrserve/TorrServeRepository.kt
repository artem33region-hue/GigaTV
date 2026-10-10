package com.artem.gigatv.data.torrserve

import com.artem.gigatv.data.SettingsRepository
import com.artem.gigatv.data.net.awaitParsed
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class TorrServeRepository(
    private val serverUrl: Flow<String>,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val metadataTimeoutMs: Long = 60_000
) {
    constructor(settings: SettingsRepository) : this(settings.torrserveUrl)

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private suspend fun baseUrl(): String = serverUrl.first().trim().trimEnd('/')

    suspend fun echo(): String? = try {
        client.newCall(Request.Builder().url("${baseUrl()}/echo").build()).awaitParsed { r ->
            if (r.isSuccessful) r.body?.string()?.trim() else null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun post(base: String, req: TorrRequest): TorrTorrent? = try {
        val body = json.encodeToString(TorrRequest.serializer(), req)
        client.newCall(
            Request.Builder().url("$base/torrents").post(body.toRequestBody(jsonMedia)).build()
        ).awaitParsed { r ->
            val text = r.body?.string()
            if (r.isSuccessful && !text.isNullOrBlank())
                json.decodeFromString(TorrTorrent.serializer(), text)
            else null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    sealed interface AddResult {
        data class Ready(val torrent: TorrTorrent) : AddResult
        data object ServerUnavailable : AddResult
        data object NoMetadata : AddResult
    }

    /** The entire operation, including reading HTTP bodies, has one deadline. */
    suspend fun add(link: String): AddResult {
        val base = baseUrl()
        var accepted = false
        return withTimeoutOrNull(metadataTimeoutMs) {
            val added = post(base, TorrRequest(action = "add", link = link.trim()))
                ?: return@withTimeoutOrNull AddResult.ServerUnavailable
            val hash = added.hash.ifBlank { return@withTimeoutOrNull AddResult.ServerUnavailable }
            accepted = true
            var torrent = added
            while (torrent.file_stats.isNullOrEmpty()) {
                // Poll immediately once, then leave time for metadata to arrive.
                val current = post(base, TorrRequest(action = "get", hash = hash))
                if (current != null) torrent = current
                if (torrent.file_stats.isNullOrEmpty()) delay(1000)
            }
            AddResult.Ready(torrent.copy(hash = hash))
        } ?: if (accepted) AddResult.NoMetadata else AddResult.ServerUnavailable
    }

    suspend fun streamUrl(hash: String, file: TorrFile): String {
        val name = URLEncoder.encode(file.fileName, "UTF-8").replace("+", "%20")
        return "${baseUrl()}/stream/$name?link=$hash&index=${file.id}&play"
    }

    suspend fun torrentForStream(url: String): TorrTorrent? {
        val base = baseUrl()
        val hash = torrentStreamHash(url, base) ?: return null
        return post(base, TorrRequest(action = "get", hash = hash))?.copy(hash = hash)
    }
}

internal fun torrentStreamHash(url: String, server: String? = null): String? {
    val stream = url.toHttpUrlOrNull() ?: return null
    if (!stream.encodedPath.startsWith("/stream/")) return null
    if (server != null) {
        val base = server.toHttpUrlOrNull() ?: return null
        if (stream.scheme != base.scheme || stream.host != base.host || stream.port != base.port) return null
    }
    return stream.queryParameter("link")?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
}
