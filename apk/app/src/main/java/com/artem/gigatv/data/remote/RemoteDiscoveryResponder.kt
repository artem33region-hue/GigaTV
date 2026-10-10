package com.artem.gigatv.data.remote

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.Executors

/** LAN broadcast fallback for routers that do not pass mDNS reliably. */
class RemoteDiscoveryResponder {
    private val worker = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null

    fun start() {
        worker.execute {
            try {
                DatagramSocket(8766).use { server ->
                    socket = server
                    val bytes = ByteArray(256)
                    while (!server.isClosed) {
                        val packet = DatagramPacket(bytes, bytes.size)
                        server.receive(packet)
                        val request = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        if (request == "GIGATV_DISCOVER_V1") {
                            val reply = "GIGATV_HERE_V1:8765".toByteArray(Charsets.UTF_8)
                            server.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GigaTVDiscovery", "LAN discovery stopped", e)
            }
        }
    }
}
