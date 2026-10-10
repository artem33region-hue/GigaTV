package com.artem.gigatv.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import com.artem.gigatv.BuildConfig
import com.artem.gigatv.data.SettingsRepository
import java.io.File
import java.security.MessageDigest
import java.security.DigestOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Формат файла версии, который лежит по указанному адресу. */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
    val notes: String = "",
    /**
     * SHA-256 файла APK в hex, необязательно. Подпись проверяется всегда, но
     * хеш ловит битую закачку раньше и дешевле.
     */
    val sha256: String = ""
)

/**
 * Проверка и установка обновления. Приложение распространяется сайдлоадом, так
 * что магазина у него нет — так же самообновляются Lampishe, LazyMedia, HDrezka
 * и Zona, все они держат REQUEST_INSTALL_PACKAGES.
 *
 * Раз магазина нет, нет и его проверок, поэтому весь контроль здесь свой:
 * только https, ограничение на размер, необязательный SHA-256 и — главное —
 * скачанный APK обязан быть тем же пакетом и с той же подписью, что и текущий.
 * Без последней проверки любой, кто подменит ответ сервера, ставит на телевизор
 * произвольное приложение.
 */
class UpdateRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {
    private companion object {
        /** Заведомо больше любой нашей сборки (сейчас ~10 МБ) и не даёт забить кэш. */
        const val MAX_APK_BYTES = 300L * 1024 * 1024
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val downloadMutex = Mutex()

    /**
     * Обновление приходит из сети и попадает прямо в установщик, поэтому канал
     * должен быть защищён от подмены на всём пути: и манифест, и сам APK.
     */
    private fun requireHttps(url: String, what: String): String {
        val parsed = url.toHttpUrlOrNull() ?: error("$what: адрес неразборчив")
        if (!parsed.isHttps) error("$what должен начинаться с https://")
        return parsed.toString()
    }

    /** null — обновления нет или адрес не задан. */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val url = settings.updateUrl.first().trim()
        if (url.isEmpty()) return@withContext Result.success(null)
        runCatching {
            val manifestUrl = requireHttps(url, "адрес обновления")
            val body = client.newCall(Request.Builder().url(manifestUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
            if (!body.trimStart().startsWith("{")) error("по адресу лежит не JSON")
            val info = json.decodeFromString(UpdateInfo.serializer(), body)
            if (info.versionCode <= BuildConfig.VERSION_CODE || info.url.isBlank()) return@runCatching null
            // Адрес APK может указывать куда угодно, в том числе на другой хост,
            // — проверяем его до того, как покажем пользователю кнопку.
            requireHttps(info.url, "адрес APK")
            info
        }
    }

    /** Скачивает APK, проверяет его и передаёт системному установщику. */
    suspend fun download(info: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        if (!downloadMutex.tryLock()) {
            return@withContext Result.failure(IllegalStateException("Обновление уже скачивается"))
        }
        var apk: File? = null
        try {
            val jobContext = currentCoroutineContext()
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File.createTempFile("gigatv-${info.versionCode}-", ".apk", dir)
                apk = target
                val apkUrl = requireHttps(info.url, "адрес APK")
                val digest = MessageDigest.getInstance("SHA-256")
                val size = client.newCall(Request.Builder().url(apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body ?: error("пустой ответ")
                    if (body.contentLength() > MAX_APK_BYTES) error("файл подозрительно велик")
                    target.outputStream().use { out ->
                        DigestOutputStream(out, digest).use { hashing ->
                            body.byteStream().copyLimitedTo(hashing, MAX_APK_BYTES) {
                                jobContext.ensureActive()
                            }
                        }
                    }
                }
                if (size < 1_000) error("файл слишком мал, похоже это не APK")
                verify(target, digest.digest().toHex(), info)
                withContext(Dispatchers.Main) { install(target) }
            }.onFailure {
                apk?.delete()
                if (it is CancellationException) throw it
            }
        } finally {
            downloadMutex.unlock()
        }
    }

    /**
     * Всё, что должно совпасть, прежде чем файл уйдёт установщику. Ключевая
     * проверка — подпись: APK, подписанный чужим ключом, установщик поставил бы
     * как отдельное приложение, если у него другое имя пакета.
     */
    private fun verify(apk: File, actualSha256: String, info: UpdateInfo) {
        val expected = info.sha256.trim()
        if (expected.isNotEmpty() && !expected.equals(actualSha256, ignoreCase = true)) {
            error("SHA-256 не совпал — файл повреждён или подменён")
        }

        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, signatureFlags())
            ?: error("не удалось разобрать APK")

        if (archive.packageName != context.packageName) {
            error("в APK другое приложение (${archive.packageName})")
        }
        val archiveVersion = archive.versionCodeCompat()
        if (archiveVersion <= BuildConfig.VERSION_CODE) {
            error("в APK версия $archiveVersion, не новее установленной")
        }

        val ours = signaturesOf(pm.getPackageInfo(context.packageName, signatureFlags()))
        val theirs = signaturesOf(archive)
        if (ours.isEmpty() || theirs.isEmpty()) error("не удалось прочитать подпись APK")
        if (ours.intersect(theirs).isEmpty()) error("APK подписан другим ключом")
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signaturesOf(pkg: PackageInfo?): Set<String> {
        pkg ?: return emptySet()
        val signatures: List<Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pkg.signingInfo ?: return emptySet()
                // Берём и текущих подписантов, и историю ротации: какой из двух
                // списков заполнен, зависит от того, одним ключом подписан пакет
                // или несколькими, а перепутать их — значит забраковать своё же
                // обновление.
                info.apkContentsSigners.orEmpty().toList() +
                    info.signingCertificateHistory.orEmpty().toList()
            } else {
                pkg.signatures.orEmpty().toList()
            }
        return signatures.filterNotNull().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
