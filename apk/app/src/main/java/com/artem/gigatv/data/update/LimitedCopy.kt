package com.artem.gigatv.data.update

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Enforce the limit before writing a chunk, including responses without Content-Length. */
internal fun InputStream.copyLimitedTo(
    out: OutputStream,
    limit: Long,
    checkCancelled: () -> Unit = {}
): Long {
    require(limit >= 0)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        checkCancelled()
        val count = read(buffer)
        if (count < 0) return total
        if (count.toLong() > limit - total) throw IOException("Файл превышает допустимый размер")
        out.write(buffer, 0, count)
        total += count
    }
}
