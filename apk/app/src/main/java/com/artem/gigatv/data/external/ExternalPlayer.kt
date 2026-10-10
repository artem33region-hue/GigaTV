package com.artem.gigatv.data.external

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.artem.gigatv.data.DeepLink

/**
 * Передача воспроизведения в сторонний плеер.
 *
 * Зачем: встроенный ExoPlayer на этом телевизоре не вытягивает Dolby Vision, а у
 * MX Player на нём же ломается HDR — владельцы модели сообщают, что корректно
 * работает DDD Video Player. Реализовывать DV самим бессмысленно, проще отдать
 * поток тому плееру, который с ним справляется.
 */
object ExternalPlayer {

    private fun intentFor(url: String, title: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            // Ключи, которые понимают MX Player, VLC, Vimu и другие.
            putExtra("title", title)
            putExtra("secure_uri", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Есть ли вообще куда отдавать: если плееров нет, кнопку показывать незачем. */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager
            .queryIntentActivities(intentFor("https://example.com/v.mp4", ""), 0)
            .any { it.activityInfo?.packageName != context.packageName }
    }.getOrDefault(false)

    /** Открывает выбор плеера. Возвращает false, если открыть не удалось. */
    fun open(context: Context, url: String, title: String): Boolean = runCatching {
        // Тот же список схем, что и у своего плеера: адрес мог прийти извне по
        // ссылке gigatv://play/…, и подсовывать чужому плееру file:// или
        // content:// от нашего имени незачем.
        if (!DeepLink.isPlayable(url)) error("схема адреса не поддерживается")
        val chooser = Intent.createChooser(intentFor(url, title), "Открыть в плеере")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    }.getOrElse {
        Toast.makeText(context, "Не нашлось приложения для видео", Toast.LENGTH_SHORT).show()
        false
    }
}
