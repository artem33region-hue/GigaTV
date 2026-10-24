package com.artem.gigatvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { RemoteApp() }
            }
        }
    }
}

@Composable
fun RemoteApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember {
        val prefs = context.getSharedPreferences("remote_identity", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: ByteArray(32).also {
            java.security.SecureRandom().nextBytes(it)
        }.joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("token", it).apply() }
        RemoteClient(scope, token)
    }

    val state by client.state.collectAsState()
    val status by client.status.collectAsState()

    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var connectedTo by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }
    var autoConnect by remember { mutableStateOf(true) }
    var foreground by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, client) {
        var discovery: TvDiscovery? = null
        fun start() {
            if (discovery != null) return
            foreground = true
            connectedTo?.let { client.connect(it) }
            discovery = TvDiscovery(context) { name, host ->
                devices = (devices + (name to host)).distinctBy { it.second }
            }.also { it.start() }
        }
        fun stop() {
            foreground = false
            discovery?.stop()
            discovery = null
            client.close()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    // Нашли телевизор — подключаемся сами, без лишних нажатий.
    LaunchedEffect(devices, foreground) {
        if (foreground && autoConnect && connectedTo == null && devices.isNotEmpty()) {
            autoConnect = false
            val host = devices.first().second
            if (client.connect(host)) connectedTo = host
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text("GigaTV Remote", style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (connectedTo != null) {
            OutlinedButton(onClick = {
                autoConnect = false
                client.close()
                connectedTo = null
            }) { Text("Выбрать другой телевизор") }
        }

        val s = state
        when {
            s == null || connectedTo == null -> ConnectPanel(
                devices = devices,
                manual = manual,
                onManualChange = { manual = it },
                onConnect = { host ->
                    autoConnect = false
                    if (client.connect(host)) connectedTo = host
                }
            )

            !s.active -> Text(
                "На телевизоре сейчас ничего не воспроизводится. " +
                    "Запустите видео — управление появится здесь."
            )

            else -> PlayerControls(s) { client.send(it) }
        }
    }
}

@Composable
private fun ConnectPanel(
    devices: List<Pair<String, String>>,
    manual: String,
    onManualChange: (String) -> Unit,
    onConnect: (String) -> Unit,
) {
    devices.forEach { (name, host) ->
        Button(onClick = { onConnect(host) }, modifier = Modifier.fillMaxWidth()) {
            Text("$name ($host)")
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = manual,
            onValueChange = onManualChange,
            label = { Text("IP телевизора") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = { onConnect(manual.trim()) }) { Text("Подключить") }
    }
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
private fun PlayerControls(s: RemoteState, send: (RemoteCommand) -> Unit) {
    Text(s.title ?: "Без названия", style = MaterialTheme.typography.titleLarge)
    s.video?.let {
        Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Пока тянем ползунок, позицию с телевизора не применяем, иначе он скачет.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    // duration <= 0 — длительность ещё неизвестна (торрент до метаданных, live):
    // ползунок тогда выключен, иначе любое касание отправляло бы seek в самое
    // начало, а итог показывал бы бессмысленный 0:00.
    val durationKnown = s.duration > 0
    val duration = s.duration.coerceAtLeast(1)
    Slider(
        value = when {
            !durationKnown -> 0f
            dragging -> dragValue
            else -> s.position.toFloat() / duration
        },
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = {
            if (durationKnown) send(RemoteCommand("seek", to = (dragValue * duration).toLong()))
            dragging = false
        },
        enabled = durationKnown,
        modifier = Modifier.fillMaxWidth()
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = if (dragging && durationKnown) (dragValue * duration).toLong() else s.position
        Text(formatTime(shown))
        Text(if (durationKnown) formatTime(s.duration) else "--:--")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(onClick = { send(RemoteCommand("seekBy", by = -10_000)) }) { Text("−10 с") }
        Button(onClick = { send(RemoteCommand("toggle")) }, modifier = Modifier.weight(1f)) {
            Text(if (s.playing) "Пауза" else "Играть")
        }
        OutlinedButton(onClick = { send(RemoteCommand("seekBy", by = 30_000)) }) { Text("+30 с") }
    }

    Section("Скорость") {
        ChipRow {
            SPEEDS.forEach { sp ->
                FilterChip(
                    selected = abs(s.speed - sp) < 0.01f,
                    onClick = { send(RemoteCommand("speed", value = sp)) },
                    label = { Text(speedLabel(sp)) }
                )
            }
        }
    }

    if (s.quality.size > 1) Section("Качество") {
        ChipRow {
            FilterChip(
                selected = s.qualityAuto,
                onClick = { send(RemoteCommand("qualityAuto")) },
                label = { Text("Авто") }
            )
            s.quality.forEach { q ->
                FilterChip(
                    selected = !s.qualityAuto && q.selected,
                    onClick = { send(RemoteCommand("quality", group = q.group, index = q.index)) },
                    label = { Text(q.label) }
                )
            }
        }
    }

    if (s.audio.isNotEmpty()) Section("Аудиодорожка") {
        s.audio.forEach { t ->
            TrackRow(t.label, t.detail, t.selected) {
                send(RemoteCommand("audio", group = t.group, index = t.index))
            }
        }
    }

    if (s.subs.isNotEmpty()) Section("Субтитры") {
        TrackRow("Выключены", null, s.subsOff) { send(RemoteCommand("subsOff")) }
        s.subs.forEach { t ->
            TrackRow(t.label, t.detail, !s.subsOff && t.selected) {
                send(RemoteCommand("subs", group = t.group, index = t.index))
            }
        }
    }

    Section("Масштаб") {
        ChipRow {
            listOf("fit" to "Вписать", "zoom" to "Заполнить", "fill" to "Растянуть")
                .forEach { (mode, name) ->
                    FilterChip(
                        selected = s.resize == mode,
                        onClick = { send(RemoteCommand("resize", mode = mode)) },
                        label = { Text(name) }
                    )
                }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) { content() }
}

@Composable
private fun TrackRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun speedLabel(s: Float): String =
    (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "×"

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
