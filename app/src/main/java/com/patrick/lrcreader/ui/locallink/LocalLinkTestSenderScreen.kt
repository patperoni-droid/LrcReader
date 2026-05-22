package com.patrick.lrcreader.ui.locallink

import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.locallink.ClockMessage
import com.patrick.lrcreader.core.locallink.HelloMessage
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.core.locallink.LocalLinkServer
import com.patrick.lrcreader.core.locallink.LyricsLinePayload
import com.patrick.lrcreader.core.locallink.LyricsPacketMessage
import com.patrick.lrcreader.core.locallink.ReceiverStatusMessage
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

@Composable
fun LocalLinkTestSenderScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    KeepScreenOn()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val localIp = remember { findLocalIpv4Address() ?: "127.0.0.1" }
    val emptyValue = stringResource(R.string.local_link_empty_value)
    val senderDeviceName = stringResource(R.string.local_link_sender_device_name)
    val experimentalToken = stringResource(R.string.local_link_experimental_token)
    val receiverReadyStatus = stringResource(R.string.local_link_receiver_ready_status)
    val testSongTitle = stringResource(R.string.local_link_test_payload_title)
    val testSongLines = context.resources.getStringArray(R.array.local_link_test_payload_lines).toList()
    var server by remember { mutableStateOf<LocalLinkServer?>(null) }
    var port by remember { mutableStateOf<Int?>(null) }
    var clockJob by remember { mutableStateOf<Job?>(null) }
    var seq by remember { mutableLongStateOf(1L) }
    var isClockRunning by remember { mutableStateOf(false) }
    var remoteStatus by remember { mutableStateOf("") }
    var statusRes by remember { mutableStateOf(R.string.local_link_server_stopped) }

    fun closeServer() {
        clockJob?.cancel()
        clockJob = null
        isClockRunning = false
        server?.close()
        server = null
        port = null
        statusRes = R.string.local_link_server_stopped
    }

    fun handleIncoming(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> remoteStatus = message.deviceName
            is ReceiverStatusMessage -> remoteStatus = if (message.state == "ready") {
                receiverReadyStatus
            } else {
                message.state
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose { closeServer() }
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
                onBack = onBack
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
                        value = stringResource(statusRes)
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_ip_label),
                        value = localIp
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_port_label),
                        value = port?.toString() ?: emptyValue
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_token_label),
                        value = experimentalToken
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_remote_label),
                        value = remoteStatus.ifBlank { emptyValue }
                    )
                    Text(
                        text = stringResource(R.string.local_link_manual_pairing_hint),
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = server == null,
                            onClick = {
                                val nextServer = LocalLinkServer(
                                    sessionId = "sender-${System.currentTimeMillis()}",
                                    token = experimentalToken,
                                    deviceName = senderDeviceName
                                )
                                server = nextServer
                                scope.launch {
                                    val startedPort = nextServer.start(scope) { message ->
                                        scope.launch { handleIncoming(message) }
                                    }
                                    port = startedPort
                                    statusRes = R.string.local_link_server_ready
                                }
                            }
                        ) {
                            Text(stringResource(R.string.local_link_start_server))
                        }
                        TextButton(
                            enabled = server != null,
                            onClick = { closeServer() }
                        ) {
                            Text(stringResource(R.string.local_link_stop_server))
                        }
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
                            enabled = server != null,
                            onClick = {
                                val activeServer = server ?: return@Button
                                clockJob?.cancel()
                                clockJob = scope.launch {
                                    val songId = "local-link-test-song"
                                    activeServer.send(
                                        testLyricsPacket(
                                            songId = songId,
                                            seq = seq++,
                                            title = testSongTitle,
                                            lineTexts = testSongLines
                                        )
                                    )
                                    val startedAt = SystemClock.elapsedRealtime()
                                    isClockRunning = true
                                    while (isActive) {
                                        val timeMs = SystemClock.elapsedRealtime() - startedAt
                                        activeServer.send(
                                            ClockMessage(
                                                songId = songId,
                                                timeMs = timeMs,
                                                isPlaying = true,
                                                seq = seq++,
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
                                            seq = seq++,
                                            sentAtMs = SystemClock.elapsedRealtime()
                                        )
                                    )
                                    isClockRunning = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.local_link_send_test_song))
                        }
                        TextButton(
                            enabled = isClockRunning,
                            onClick = {
                                clockJob?.cancel()
                                clockJob = null
                                isClockRunning = false
                            }
                        ) {
                            Text(stringResource(R.string.local_link_stop_clock))
                        }
                    }
                    Text(
                        text = stringResource(R.string.local_link_clock_state, seq),
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
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

private fun findLocalIpv4Address(): String? {
    return runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses).asSequence()
            }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
