package com.artem.gigatvremote

import org.junit.Assert.*
import org.junit.Test

class RemoteAddressTest {
    @Test fun blankAddressIsRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException::class.java) { remoteAddress("  ") }
    }

    @Test fun ipv4HasTheExpectedPort() {
        val url = remoteAddress(" 192.168.1.20 ")
        assertEquals("192.168.1.20", url.host)
        assertEquals(TV_PORT, url.port)
    }

    @Test fun ipv6FormsProduceTheSameUrl() {
        assertEquals(remoteAddress("::1"), remoteAddress("[::1]"))
        assertEquals("http://[::1]:8765/", remoteAddress("::1").toString())
    }

    @Test fun unsupportedScopedAddressIsRejectedClearly() {
        val error = assertThrows(IllegalArgumentException::class.java) { remoteAddress("fe80::1234%wlan0") }
        assertTrue(error.message.orEmpty().contains("IPv4"))
    }
}
