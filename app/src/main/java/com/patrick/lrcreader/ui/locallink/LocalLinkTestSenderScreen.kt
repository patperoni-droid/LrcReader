package com.patrick.lrcreader.ui.locallink

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.locallink.ClockMessage
import com.patrick.lrcreader.core.locallink.HelloMessage
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.core.locallink.LocalLinkServer
import com.patrick.lrcreader.core.locallink.LyricsLinePayload
import com.patrick.lrcreader.core.locallink.LyricsPacketMessage
import com.patrick.lrcreader.core.locallink.ReceiverStatusMessage
import com.patrick.lrcreader.core.network.SmpDeviceAdvertiser
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOCAL_LINK_DIAG_TAG = "LOCAL_LINK_DIAG"

object LocalLinkExperimentalSenderRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var server by mutableStateOf<LocalLinkServer?>(null)
    var port by mutableStateOf<Int?>(null)
    var clockJob by mutableStateOf<Job?>(null)
    var seq by mutableLongStateOf(1L)
    var isClockRunning by mutableStateOf(false)
    var remoteStatus by mutableStateOf("")
    var statusRes by mutableStateOf(R.string.local_link_server_stopped)
    var statusDetail by mutableStateOf<String?>(null)
    var linesSent by mutableStateOf<Int?>(null)
    var sharedSongTitle by mutableStateOf<String?>(null)
    private var liveSharingSongId: String? = null

    private var currentSongIdProvider: () -> String? = { null }
    private var currentSongTitleProvider: () -> String? = { null }
    private var currentParsedLinesProvider: () -> List<LrcLine> = { emptyList() }
    private var currentPositionProvider: () -> Long = { 0L }
    private var currentDurationProvider: () -> Long? = { null }
    private var isPlayingProvider: () -> Boolean = { false }
    private var loadParsedLinesProvider: suspend () -> List<LrcLine> = { emptyList() }

    fun updateLiveSource(
        currentSongId: () -> String?,
        currentSongTitle: () -> String?,
        currentParsedLines: () -> List<LrcLine>,
        currentPositionMs: () -> Long,
        currentDurationMs: () -> Long?,
        isPlaying: () -> Boolean,
        loadParsedLines: suspend () -> List<LrcLine>
    ) {
        currentSongIdProvider = currentSongId
        currentSongTitleProvider = currentSongTitle
        currentParsedLinesProvider = currentParsedLines
        currentPositionProvider = currentPositionMs
        currentDurationProvider = currentDurationMs
        isPlayingProvider = isPlaying
        loadParsedLinesProvider = loadParsedLines
    }

    fun currentSongId(): String? = currentSongIdProvider()?.trim()?.takeIf { it.isNotEmpty() }

    fun currentSongTitle(): String? = currentSongTitleProvider()?.trim()?.takeIf { it.isNotEmpty() }

    fun currentParsedLines(): List<LrcLine> = currentParsedLinesProvider()

    fun currentPositionMs(): Long = currentPositionProvider()

    fun currentDurationMs(): Long? = currentDurationProvider()

    fun isPlaying(): Boolean = isPlayingProvider()

    suspend fun loadParsedLines(): List<LrcLine> = loadParsedLinesProvider()

    fun startLiveSharing(force: Boolean = false) {
        val activeServer = server ?: return
        val songId = currentSongId()
        if (songId == null) {
            statusRes = R.string.local_link_no_current_song
            return
        }
        if (!force && clockJob?.isActive == true && liveSharingSongId == songId) {
            return
        }
        clockJob?.cancel()
        liveSharingSongId = songId
        clockJob = scope.launch {
            Log.d(LOCAL_LINK_DIAG_TAG, "sender_sharing_active songId=$songId")
            var packetSentForCurrentConnection = false
            var lastConnectionState = activeServer.session.connected
            var lastClockMs: Long? = null
            isClockRunning = true
            while (isActive && server === activeServer) {
                val connected = activeServer.session.connected
                if (!connected) {
                    if (lastConnectionState) {
                        packetSentForCurrentConnection = false
                    }
                    lastConnectionState = false
                    statusRes = R.string.local_link_no_receiver_connected
                    delay(200L)
                    continue
                }
                if (!lastConnectionState) {
                    packetSentForCurrentConnection = false
                }
                lastConnectionState = true

                if (!packetSentForCurrentConnection) {
                    val title = currentSongTitle() ?: songId
                    val loadedLines = runCatching {
                        loadParsedLines()
                    }.onFailure { error ->
                        Log.w(
                            LOCAL_LINK_DIAG_TAG,
                            "sender_live_lyrics_prepare_failed songId=$songId",
                            error
                        )
                        statusDetail = error.message ?: error::class.java.simpleName
                    }.getOrDefault(emptyList())
                    val packet = liveLyricsPacket(
                        songId = songId,
                        title = title,
                        lines = loadedLines,
                        durationMs = currentDurationMs(),
                        seq = seq++
                    )
                    val packetSent = activeServer.send(packet)
                    if (packetSent) {
                        Log.d(
                            LOCAL_LINK_DIAG_TAG,
                            "sender_auto_packet_sent songId=$songId lines=${loadedLines.size}"
                        )
                        packetSentForCurrentConnection = true
                        sharedSongTitle = title
                        linesSent = loadedLines.size
                        statusDetail = null
                        statusRes = if (loadedLines.isEmpty()) {
                            R.string.local_link_no_live_lyrics
                        } else {
                            R.string.local_link_live_share_running
                        }
                    } else {
                        statusRes = R.string.local_link_no_receiver_connected
                        delay(200L)
                        continue
                    }
                }

                val currentTimeMs = currentPositionMs().coerceAtLeast(0L)
                val previousClockMs = lastClockMs
                if (previousClockMs != null && currentTimeMs + 1_000L < previousClockMs) {
                    Log.d(
                        LOCAL_LINK_DIAG_TAG,
                        "sender_seek_or_restart songId=$songId from=$previousClockMs to=$currentTimeMs"
                    )
                }
                val clockSent = activeServer.send(
                    ClockMessage(
                        songId = songId,
                        timeMs = currentTimeMs,
                        isPlaying = isPlaying(),
                        seq = seq++,
                        sentAtMs = SystemClock.elapsedRealtime()
                    )
                )
                if (!clockSent) {
                    packetSentForCurrentConnection = false
                }
                lastClockMs = currentTimeMs
                delay(200L)
            }
            if (liveSharingSongId == songId) {
                liveSharingSongId = null
            }
            isClockRunning = false
        }
    }

    fun closeServer() {
        Log.d(LOCAL_LINK_DIAG_TAG, "sender_server_stopped port=$port")
        clockJob?.cancel()
        clockJob = null
        liveSharingSongId = null
        isClockRunning = false
        linesSent = null
        sharedSongTitle = null
        server?.close()
        server = null
        port = null
        remoteStatus = ""
        statusRes = R.string.local_link_server_stopped
        statusDetail = null
    }
}

@Composable
fun LocalLinkTestSenderScreen(
    modifier: Modifier = Modifier,
    currentSongId: String? = null,
    currentSongTitle: String? = null,
    currentParsedLines: List<LrcLine> = emptyList(),
    getCurrentPositionMs: () -> Long = { 0L },
    getCurrentDurationMs: () -> Long? = { null },
    isCurrentTrackPlaying: () -> Boolean = { false },
    loadCurrentParsedLines: suspend () -> List<LrcLine> = { currentParsedLines },
    onBack: () -> Unit
) {
    KeepScreenOn()

    val context = LocalContext.current
    val runtime = LocalLinkExperimentalSenderRuntime
    val localIp = remember { findLocalIpv4Address() ?: "127.0.0.1" }
    val emptyValue = stringResource(R.string.local_link_empty_value)
    val senderDeviceName = stringResource(R.string.local_link_sender_device_name)
    val experimentalToken = stringResource(R.string.local_link_experimental_token)
    val receiverReadyStatus = stringResource(R.string.local_link_receiver_ready_status)
    val testSongTitle = stringResource(R.string.local_link_test_payload_title)
    val testSongLines = context.resources.getStringArray(R.array.local_link_test_payload_lines).toList()
    var showAdvancedOptions by remember { mutableStateOf(false) }
    val advertiser = remember(context) { SmpDeviceAdvertiser(context.applicationContext) }
    runtime.updateLiveSource(
        currentSongId = { currentSongId },
        currentSongTitle = { currentSongTitle },
        currentParsedLines = { currentParsedLines },
        currentPositionMs = getCurrentPositionMs,
        currentDurationMs = getCurrentDurationMs,
        isPlaying = isCurrentTrackPlaying,
        loadParsedLines = loadCurrentParsedLines
    )
    fun handleIncoming(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                runtime.remoteStatus = message.deviceName ?: emptyValue
                Log.d(LOCAL_LINK_DIAG_TAG, "sender_received_hello remote=${message.deviceName}")
            }
            is ReceiverStatusMessage -> runtime.remoteStatus = if (message.state == "ready") {
                receiverReadyStatus
            } else {
                message.state
            }
            else -> Unit
        }
    }

    DisposableEffect(advertiser) {
        onDispose { advertiser.stop() }
    }
    LaunchedEffect(advertiser, runtime.server, runtime.port) {
        val activePort = runtime.port
        if (runtime.server != null && activePort != null) {
            advertiser.start(activePort)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111111), Color(0xFF16110E), Color(0xFF080808))
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderRow(
                title = stringResource(R.string.local_link_sender_title),
                onBack = {
                    advertiser.stop()
                    runtime.closeServer()
                    onBack()
                }
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171717)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoLine(
                        label = stringResource(R.string.local_link_status_label),
                        value = stringResource(runtime.statusRes)
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_remote_label),
                        value = runtime.remoteStatus.ifBlank { emptyValue }
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_current_song_label),
                        value = runtime.currentSongTitle() ?: emptyValue
                    )
                    runtime.statusDetail?.let { detail ->
                        Text(
                            text = detail,
                            color = Color(0xFFFFCCBC),
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = runtime.server == null,
                            onClick = {
                                val nextServer = LocalLinkServer(
                                    sessionId = "sender-${System.currentTimeMillis()}",
                                    token = experimentalToken,
                                    deviceName = senderDeviceName
                                )
                                runtime.server = nextServer
                                runtime.statusDetail = null
                                runtime.scope.launch {
                                    runCatching {
                                        nextServer.start(runtime.scope) { message ->
                                            runtime.scope.launch { handleIncoming(message) }
                                        }
                                    }.onSuccess { startedPort ->
                                        runtime.port = startedPort
                                        runtime.statusRes = R.string.local_link_server_ready
                                        advertiser.start(startedPort)
                                        Log.d(
                                            LOCAL_LINK_DIAG_TAG,
                                            "sender_server_started port=$startedPort addresses=${findLocalIpv4Addresses().joinToString()}"
                                        )
                                    }.onFailure { error ->
                                        Log.w(LOCAL_LINK_DIAG_TAG, "sender_server_start_failed", error)
                                        advertiser.stop()
                                        nextServer.close()
                                        runtime.server = null
                                        runtime.port = null
                                        runtime.statusRes = R.string.local_link_server_start_failed
                                        runtime.statusDetail = error.message ?: error::class.java.simpleName
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.local_link_start_server))
                        }
                        TextButton(
                            enabled = runtime.server != null,
                            onClick = {
                                advertiser.stop()
                                runtime.closeServer()
                            }
                        ) {
                            Text(stringResource(R.string.local_link_stop_server))
                        }
                    }
                    TextButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                        Text(stringResource(R.string.second_screen_advanced_title))
                    }
                    if (showAdvancedOptions) {
                        Text(
                            text = stringResource(R.string.second_screen_local_info_title),
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        InfoLine(
                            label = stringResource(R.string.local_link_ip_label),
                            value = localIp
                        )
                        InfoLine(
                            label = stringResource(R.string.local_link_port_label),
                            value = runtime.port?.toString() ?: emptyValue
                        )
                        InfoLine(
                            label = stringResource(R.string.local_link_token_label),
                            value = experimentalToken
                        )
                        Text(
                            text = stringResource(R.string.local_link_manual_pairing_hint),
                            color = Color(0xFF9E9E9E),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111416)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.local_link_test_song_title),
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = runtime.server != null,
                            onClick = {
                                val activeServer = runtime.server ?: return@Button
                                runtime.clockJob?.cancel()
                                runtime.clockJob = runtime.scope.launch {
                                    val songId = "local-link-test-song"
                                    activeServer.send(
                                        testLyricsPacket(
                                            songId = songId,
                                            seq = runtime.seq++,
                                            title = testSongTitle,
                                            lineTexts = testSongLines
                                        )
                                    )
                                    val startedAt = SystemClock.elapsedRealtime()
                                    runtime.isClockRunning = true
                                    while (isActive) {
                                        val timeMs = SystemClock.elapsedRealtime() - startedAt
                                        activeServer.send(
                                            ClockMessage(
                                                songId = songId,
                                                timeMs = timeMs,
                                                isPlaying = true,
                                                seq = runtime.seq++,
                                                sentAtMs = SystemClock.elapsedRealtime()
                                            )
                                        )
                                        if (timeMs >= 32_000L) break
                                        delay(200L)
                                    }
                                    activeServer.send(
                                        ClockMessage(
                                            songId = songId,
                                            timeMs = 32_000L,
                                            isPlaying = false,
                                            seq = runtime.seq++,
                                            sentAtMs = SystemClock.elapsedRealtime()
                                        )
                                    )
                                    runtime.isClockRunning = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.local_link_send_test_song))
                        }
                        TextButton(
                            enabled = runtime.isClockRunning,
                            onClick = {
                                runtime.clockJob?.cancel()
                                runtime.clockJob = null
                                runtime.isClockRunning = false
                            }
                        ) {
                            Text(stringResource(R.string.local_link_stop_clock))
                        }
                    }
                    Text(
                        text = stringResource(R.string.local_link_clock_state, runtime.seq),
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101714)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.local_link_live_song_title),
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_shared_song_label),
                        value = runtime.sharedSongTitle ?: emptyValue
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_lines_sent_label),
                        value = runtime.linesSent?.toString() ?: emptyValue
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = runtime.server != null,
                            onClick = { runtime.startLiveSharing(force = true) }
                        ) {
                            Text(stringResource(R.string.local_link_send_current_song))
                        }
                        TextButton(
                            enabled = runtime.isClockRunning,
                            onClick = {
                                runtime.clockJob?.cancel()
                                runtime.clockJob = null
                                runtime.isClockRunning = false
                            }
                        ) {
                            Text(stringResource(R.string.local_link_stop_clock))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFB0BEC5), fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp)
    }
}

private fun testLyricsPacket(
    songId: String,
    seq: Long,
    title: String,
    lineTexts: List<String>
): LyricsPacketMessage {
    val lineTimes = listOf(0L, 4_000L, 8_000L, 12_000L, 16_000L, 20_000L, 24_000L, 28_000L)
    val lines = lineTimes.zip(lineTexts).map { (timeMs, text) ->
        LyricsLinePayload(timeMs = timeMs, text = text)
    }
    return LyricsPacketMessage(
        songId = songId,
        title = title,
        lines = lines,
        durationMs = 32_000L,
        seq = seq
    )
}

private fun liveLyricsPacket(
    songId: String,
    title: String,
    lines: List<LrcLine>,
    durationMs: Long?,
    seq: Long
): LyricsPacketMessage {
    return LyricsPacketMessage(
        songId = songId,
        title = title,
        lines = lines.map { line ->
            LyricsLinePayload(timeMs = line.timeMs, text = line.text)
        },
        durationMs = durationMs?.takeIf { it > 0L },
        seq = seq
    )
}
