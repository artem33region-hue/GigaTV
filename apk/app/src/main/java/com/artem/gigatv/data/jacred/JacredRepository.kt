package com.artem.gigatv.data.jacred

import com.artem.gigatv.data.SettingsRepository
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Клиент JacRed — поиск торрент-раздач по заранее собранной базе русских
 * трекеров. В отличие от Jackett ничего не обходит вживую, поэтому отвечает
 * быстро, но и знает только то, что успел проиндексировать.
 *
 * Привязки к id Кинопоиска или TMDB в ответе нет — API отдаёт только
 * `name`, `originalname` и год, так что фильм с раздачей сопоставляем сами.
 */
class JacredRepository(private val settings: SettingsRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun baseUrl(): String =
        settings.jacredUrl.first().trim().trimEnd('/')

    /**
     * Ищет раздачи по названию и отсеивает заведомо чужие: сериалы для фильма
     * и наоборот, а для фильмов — ещё и другой год. Сортировка по числу сидов.
     */
    suspend fun search(
        title: String,
        year: String,
        series: Boolean
    ): Result<List<JacredTorrent>> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext Result.success(emptyList())
        runCatching {
            val query = URLEncoder.encode(title.trim(), "UTF-8")
            val url = "${baseUrl()}/api/v1.0/torrents?search=$query&exact=true&apikey="
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                when {
                    r.code == 429 -> error("сервис ограничивает частоту запросов, попробуйте позже")
                    !r.isSuccessful -> error("HTTP ${r.code}")
                    else -> r.body?.string().orEmpty()
                }
            }
            // Публичные зеркала иногда отдают HTML-заглушку вместо JSON.
            if (!body.trimStart().startsWith("[")) error("сервис вернул не JSON — проверьте адрес")

            val all = json.decodeFromString<List<JacredTorrent>>(body)
            val wantYear = year.take(4).toIntOrNull()
            all.asSequence()
                .filter { it.magnet.isNotBlank() }
                .filter { it.matchesCategory(series) }
                // У сериала год раздачи — это год сезона, он законно не совпадает.
                .filter { series || wantYear == null || it.relased == 0 || it.relased == wantYear }
                // JacRed отдаёт дубли одной раздачи; одинаковый magnet — это ещё и
                // повторяющийся ключ в LazyColumn, что роняет отрисовку.
                .distinctBy { it.magnet }
                .sortedByDescending { it.sid }
                .toList()
        }
    }
}
