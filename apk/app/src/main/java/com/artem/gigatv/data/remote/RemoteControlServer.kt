package com.artem.gigatv.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Patterns
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * WebSocket-сервер для приложения-пульта на телефоне.
 *
 * Слушает [PORT] на всех интерфейсах: телевизор и телефон в одной домашней
 * сети, пароля нет сознательно. Состояние плеера рассылается всем подключённым
 * пультам; команды пересылаются в активный [RemoteSession]. Обычный GET отдаёт
 * последнее состояние — удобно для отладки из браузера.
 *
 * «Без пароля» означает «для всех в этой сети», но не «для всех в интернете»:
 * запросы, пришедшие из чужой веб-страницы, отсекаются в [serve]. Иначе
 * открытая в браузере страница управляла бы плеером и читала, что смотрят.
 */
class RemoteControlServer : NanoWSD(PORT) {

    companion object {
        const val PORT = 8765
        const val SERVICE_TYPE = "_gigatv._tcp."

        /**
         * Таймаут чтения и предел длительности отправки. Отдельный watchdog
         * закрывает транспорт, даже если обработчик ping ждёт блокировку записи.
         */
        const val READ_TIMEOUT_MS = 15_000

        private const val PING_INTERVAL_MS = 5_000L

        /** Столько сообщений может накопиться на медленного клиента. */
        private const val SEND_QUEUE_LIMIT = 16
    }

    /** Мост к плееру; есть только пока открыт экран воспроизведения. */
    @Volatile
    var playerSession: RemoteSession? = null

    @Volatile
    private var lastState: String = """{"active":false}"""

    private val clients = CopyOnWriteArrayList<Client>()

    /**
     * Есть ли кому слать. Позволяет не собирать состояние плеера, когда пульт
     * не подключён: [RemoteSession.stateJson] считается в главном потоке.
     */
    val hasClients: Boolean get() = clients.isNotEmpty()

    // Запись в мёртвый сокет (телефон пропал из Wi-Fi без FIN) блокируется на
    // минуты TCP-ретрансмита, поэтому у каждого клиента свои поток и очередь
    // отправки: зависший клиент не стопорит рассылку остальным. Периодический
    // ping проверяет связь. Этот же таймер закрывает транспорт при зависшей
    // записи: одного read-таймаута недостаточно, если входящий ping ждёт sendFrame.
    private val pinger = Executors.newSingleThreadScheduledExecutor().apply {
        scheduleWithFixedDelay(
            { clients.forEach { it.enqueuePing() } },
            PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    fun broadcast(state: String) {
        lastState = state
        // Сеть уходит на потоки клиентов — с главного потока вызывать можно.
        clients.forEach { it.enqueue(state) }
    }

    /**
     * Единственная проверка «свой — чужой» на входе, до рукопожатия.
     *
     * WebSocket не подчиняется Same-Origin Policy: без неё любая страница,
     * открытая в браузере на телефоне или ноутбуке в этой же сети, могла бы
     * подключиться к пульту (CSWSH) и управлять воспроизведением.
     */
    override fun serve(session: IHTTPSession): Response = when {
        // Наш клиент на OkHttp Origin не шлёт; браузер шлёт его всегда, когда
        // запрос инициирован страницей.
        !session.headers["origin"].isNullOrBlank() ->
            forbidden("origin not allowed")

        // Защита от DNS rebinding: страница атакующего заставляет своё имя
        // резолвиться в адрес телевизора, и тогда Origin становится «своим».
        // Но Host при этом остаётся доменным именем, а пульт и отладка из
        // браузера всегда идут по IP-адресу.
        !isNumericHost(session.headers["host"]) ->
            forbidden("host not allowed")

        else -> super.serve(session)
    }

    private fun forbidden(reason: String): Response =
        newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", reason)

    /** Заголовок Host без порта; отсутствующий Host считаем допустимым. */
    private fun isNumericHost(header: String?): Boolean {
        val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        val host = when {
            raw.startsWith("[") -> raw.drop(1).substringBefore(']')  // [::1]:8765
            raw.count { it == ':' } > 1 -> raw                       // голый IPv6
            else -> raw.substringBeforeLast(':')
        }
        // Двоеточий в доменном имени быть не может, так что литерал IPv6
        // распознаётся по ним же.
        return host.equals("localhost", ignoreCase = true) ||
            host.contains(':') ||
            Patterns.IP_ADDRESS.matcher(host).matches()
    }

    override fun stop() {
        pinger.shutdownNow()
        clients.forEach { it.abort() }
        super.stop()
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = Client(handshake)

    override fun serveHttp(request: IHTTPSession): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", lastState)

    private inner class Client(private val handshake: IHTTPSession) : WebSocket(handshake) {

        private val sender = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(SEND_QUEUE_LIMIT)
        )

        private val terminated = AtomicBoolean(false)
        @Volatile private var sendStartedAt = 0L

        fun enqueue(state: String) = submit { send(state) }

        fun enqueuePing() {
            val started = sendStartedAt
            if (started != 0L && SystemClock.elapsedRealtime() - started >= READ_TIMEOUT_MS) {
                abort()
            } else {
                submit { ping(ByteArray(0)) }
            }
        }

        // Close the transport directly: WebSocket.close() writes a frame and can
        // wait for the same monitor as the blocked sender / incoming ping handler.
        fun abort() {
            if (!terminated.compareAndSet(false, true)) return
            clients.remove(this)
            sender.shutdownNow()
            runCatching { handshake.inputStream.close() }
        }

        private fun submit(task: () -> Unit) {
            if (terminated.get()) return
            try {
                sender.execute {
                    if (terminated.get()) return@execute
                    sendStartedAt = SystemClock.elapsedRealtime()
                    try {
                        task()
                    } catch (_: Exception) {
                        abort()
                    } finally {
                        sendStartedAt = 0L
                    }
                }
            } catch (_: RejectedExecutionException) {
                abort()
            }
        }

        override fun onOpen() {
            clients += this
            enqueue(lastState)
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            abort()
        }

        override fun onMessage(message: WebSocketFrame) {
            message.textPayload?.let { playerSession?.handle(it) }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            abort()
        }
    }
}

/**
 * mDNS-анонс, по которому пульт находит телевизор без ввода IP.
 * Живёт всё время работы приложения, снимать регистрацию не нужно.
 */
fun registerRemoteService(context: Context) {
    runCatching {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "GigaTV"
            serviceType = RemoteControlServer.SERVICE_TYPE
            port = RemoteControlServer.PORT
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo?) {}
            override fun onRegistrationFailed(i: NsdServiceInfo?, error: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo?, error: Int) {}
        })
    }
}
