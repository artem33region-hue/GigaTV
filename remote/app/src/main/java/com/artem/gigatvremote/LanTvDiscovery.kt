package com.artem.gigatvremote

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.*
import java.util.Collections
import java.util.concurrent.Executors

/** Repeat broadcasts so waking TVs and Wi-Fi changes are discovered too. */
class LanTvDiscovery(private val onFound: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    @Volatile private var socket: DatagramSocket? = null

    fun start() {
        running = true
        worker.execute {
            try {
                DatagramSocket().use { client ->
                    socket = client
                    client.broadcast = true
                    client.soTimeout = 1000
                    while (running) {
                        val targets = mutableSetOf(InetAddress.getByName("255.255.255.255"))
                        Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
                            .flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast }.forEach { targets.add(it) }
                        val query = "GIGATV_DISCOVER_V1".toByteArray(Charsets.UTF_8)
                        targets.forEach { address ->
                            runCatching { client.send(DatagramPacket(query, query.size, address, 8766)) }
                        }
                        val until = android.os.SystemClock.elapsedRealtime() + 3000
                        while (running && android.os.SystemClock.elapsedRealtime() < until) {
                            val packet = DatagramPacket(ByteArray(256), 256)
                            try {
                                client.receive(packet)
                                if (String(packet.data, packet.offset, packet.length, Charsets.UTF_8) == "GIGATV_HERE_V1:8765") {
                                    val host = packet.address.hostAddress ?: continue
                                    main.post { if (running) onFound("GigaTV", host) }
                                }
                            } catch (_: SocketTimeoutException) { }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w("GigaTVDiscovery", "LAN discovery failed", e)
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
        worker.shutdownNow()
    }
}
