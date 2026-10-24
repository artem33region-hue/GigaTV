package com.artem.gigatvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

const val TV_PORT = 8765
const val SERVICE_TYPE = "_gigatv._tcp."

/**
 * WebSocket-клиент к серверу пульта в GigaTV: держит соединение,
 * переподключается сам и отдаёт последнее состояние плеера во Flow.
 */
class RemoteClient(private val scope: CoroutineScope, private val token: String) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ok = OkHttpClient.Builder()
        .pingInterval(3, TimeUnit.SECONDS)
        .build()

    val state = MutableStateFlow<RemoteState?>(null)
    val status = MutableStateFlow("Поиск телевизора…")

    @Volatile
    private var target: String? = null
    private val lock = Any()
    @Volatile
    private var ws: WebSocket? = null
    @Volatile
    private var retryJob: Job? = null

    fun connect(host: String): Boolean {
        val address = try {
            remoteAddress(host)
        } catch (e: IllegalArgumentException) {
            status.value = e.message ?: "Некорректный адрес телевизора"
            return false
        }
        synchronized(lock) {
            target = address.host
            state.value = null
            open()
        }
        return true
    }

    private fun open() {
        val host = target ?: return
        status.value = "Подключение к $host…"
        val request = Request.Builder().url(remoteAddress(host)).header("X-GigaTV-Token", token).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (webSocket !== ws) return
                    status.value = "Подключено: $host"
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(lock) {
                    if (webSocket !== ws) return
                    runCatching { json.decodeFromString(RemoteState.serializer(), text) }
                        .onSuccess { state.value = it }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(lock) {
                    if (webSocket !== ws) return
                    state.value = null
                    status.value = if (response?.code == 401) "Подтвердите подключение на телевизоре"
                        else "Нет связи, переподключение…"
                    retry()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(lock) {
                    if (webSocket !== ws) return
                    state.value = null
                    retry()
                }
            }
        }
        // Отмена ретрая и подмена сокета атомарны, иначе гонка с onFailure
        // со старого сокета убивает свежее соединение.
        synchronized(lock) {
            retryJob?.cancel()
            val old = ws
            ws = ok.newWebSocket(request, listener)
            old?.cancel()
        }
    }

    private fun retry() {
        synchronized(lock) {
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(2000)
                if (target != null) open()
            }
        }
    }

    fun send(cmd: RemoteCommand) {
        ws?.send(json.encodeToString(RemoteCommand.serializer(), cmd))
    }

    fun close() {
        synchronized(lock) {
            target = null
            retryJob?.cancel()
            val old = ws
            ws = null
            state.value = null
            status.value = "Выберите телевизор"
            old?.cancel()
        }
    }
}

/**
 * Поиск телевизора по mDNS-анонсу GigaTV. [onFound] вызывается на главном
 * потоке с именем сервиса и адресом.
 */
class TvDiscovery(context: Context, private val onFound: (String, String) -> Unit) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private val lan = LanTvDiscovery(onFound)
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
        .createMulticastLock("gigatv-discovery").apply { setReferenceCounted(false) }
    private var running = false
    private var resolving = false
    private val pending = ArrayDeque<NsdServiceInfo>()

    private fun resolveNext() {
        if (!running || resolving || pending.isEmpty()) return
        resolving = true
        val info = pending.removeFirst()
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo?, errorCode: Int) {
                    main.post {
                        resolving = false
                        resolveNext()
                        main.postDelayed({ if (running) { pending.add(info); resolveNext() } }, 3000)
                    }
                }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    main.post {
                        resolving = false
                        val address = if (android.os.Build.VERSION.SDK_INT >= 34) {
                            i.hostAddresses.firstOrNull { it is java.net.Inet4Address }
                        } else i.host?.takeIf { it is java.net.Inet4Address }
                        if (running) address?.hostAddress?.let { onFound(i.serviceName ?: "GigaTV", it) }
                        resolveNext()
                    }
                }
            })
        } catch (e: Exception) {
            resolving = false
            android.util.Log.w("GigaTVDiscovery", "mDNS resolution failed", e)
            resolveNext()
        }
    }

    fun start() {
        if (running) return
        running = true
        runCatching { multicast.acquire() }
        lan.start()
        startNsd()
    }

    private fun startNsd() {
        if (!running || listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {
                main.post { listener = null; main.postDelayed({ startNsd() }, 3000) }
            }
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                main.post {
                    listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
                    listener = null
                    main.postDelayed({ startNsd() }, 3000)
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo?) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { if (running) { pending.add(info); resolveNext() } }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        pending.clear()
        lan.stop()
        runCatching { if (multicast.isHeld) multicast.release() }
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }
}
