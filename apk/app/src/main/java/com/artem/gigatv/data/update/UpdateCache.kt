package com.artem.gigatv.data.update

import java.io.File

/** Keep one recent APK for an installer that may still be reading it. */
internal fun pruneUpdateCache(directory: File, now: Long = System.currentTimeMillis()) {
    val files = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("gigatv-") && it.extension == "apk" }
        .sortedByDescending { it.lastModified() }
    files.forEachIndexed { index, file ->
        if (index > 0 || now - file.lastModified() > 24 * 60 * 60 * 1000L) {
            check(file.delete()) { "Не удалось очистить кэш обновлений" }
        }
    }
}
