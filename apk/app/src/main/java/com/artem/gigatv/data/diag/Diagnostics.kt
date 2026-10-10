package com.artem.gigatv.data.diag

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Журнал редких, но важных событий: подвисания главного потока и ошибки плеера.
 *
 * Зачем свой файл, когда есть logcat: на этом телевизоре системный буфер живёт
 * около двух минут — его заливает видеодекодер, — поэтому разбирать по нему
 * вчерашнее зависание невозможно. Пишем рядом с приложением, во внешнюю папку:
 * она читается с adb без root, в отличие от filesDir у неотлаживаемой сборки.
 *
 * Сторож нужен, чтобы отличать два внешне одинаковых состояния: «встал главный
 * поток» и «ввод до приложения не доходит». Пульт в обоих случаях мёртв, но
 * лечится это по-разному.
 */
class Diagnostics(private val context: Context) {

    private companion object {
        const val TAG = "GigaTvDiag"
        const val FILE_NAME = "diag.log"

        /** Как часто дёргать главный поток. */
        const val PROBE_INTERVAL_MS = 2_000L

        /** Задержка, с которой запись уже считается подвисанием. */
        const val STALL_MS = 1_500L

        /** Сколько ждать ответа, прежде чем записать «не отвечает вовсе». */
        const val MAX_WAIT_MS = 20_000L

        /** Больше не растим: журнал переживает месяцы, место на ТВ не резиновое. */
        const val MAX_BYTES = 128 * 1024
    }

    private val main = Handler(Looper.getMainLooper())
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()

    private val file: File? by lazy {
        // Внешняя папка может быть недоступна — тогда журнала просто не будет,
        // ронять из-за диагностики приложение незачем.
        runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) } }.getOrNull()
    }

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "gigatv-watchdog").apply { isDaemon = true }
    }

    fun note(event: String) {
        Log.i(TAG, event)
        val target = file ?: return
        synchronized(lock) {
            runCatching {
                if (target.length() > MAX_BYTES) target.delete()
                target.appendText("${stamp.format(Date())}  $event\n")
            }
        }
    }

    /**
     * Периодически замеряет, за сколько главный поток успевает выполнить пустую
     * задачу. Пульт телевизора обслуживает тот же поток, поэтому его задержка и
     * есть задержка реакции на кнопки.
     */
    fun startWatchdog() {
        watchdog.scheduleWithFixedDelay(
            { runCatching { probe() } },
            PROBE_INTERVAL_MS, PROBE_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    private fun probe() {
        val latch = CountDownLatch(1)
        val start = SystemClock.uptimeMillis()
        main.post { latch.countDown() }
        val answered = latch.await(MAX_WAIT_MS, TimeUnit.MILLISECONDS)
        val waited = SystemClock.uptimeMillis() - start
        when {
            !answered -> note("главный поток молчит дольше ${MAX_WAIT_MS / 1000} с")
            waited >= STALL_MS -> note("главный поток стоял $waited мс")
        }
    }
}
