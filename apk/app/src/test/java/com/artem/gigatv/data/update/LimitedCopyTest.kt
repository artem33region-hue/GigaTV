package com.artem.gigatv.data.update

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LimitedCopyTest {
    @Test fun exactLimitIsAccepted() {
        val input = ByteArray(8193) { (it % 127).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(8193L, ByteArrayInputStream(input).copyLimitedTo(output, 8193))
        assertArrayEquals(input, output.toByteArray())
    }

    @Test fun oversizedStreamNeverWritesBeyondLimit() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(20000)).copyLimitedTo(output, 10000)
        }
        assertTrue(output.size() <= 10000)
    }

    @Test fun cancellationStopsBeforeNextChunk() {
        val output = ByteArrayOutputStream()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            ByteArrayInputStream(ByteArray(20000)).copyLimitedTo(output, 30000) {
                if (++checks == 2) throw CancellationException("cancelled")
            }
        }
        assertEquals(DEFAULT_BUFFER_SIZE, output.size())
    }
}
