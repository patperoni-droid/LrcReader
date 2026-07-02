package com.patrick.lrcreader.ui.locallink

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.locallink.ClockMessage
import com.patrick.lrcreader.core.locallink.HelloMessage
import com.patrick.lrcreader.core.locallink.LocalLinkClient
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.core.locallink.LyricsPacketMessage
import com.patrick.lrcreader.core.locallink.ReceiverStatusMessage
import com.patrick.lrcreader.core.locallink.UnknownMessage
import com.patrick.lrcreader.core.network.SmpDeviceDiscovery
import com.patrick.lrcreader.core.network.SmpDiscoveredDevice
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOCAL_LINK_DIAG_TAG = "LOCAL_LINK_DIAG"
private const val LOCAL_LINK_PREFS = "local_link_receiver"
private const val LOCAL_LINK_PREF_HOST = "host"
private const val LOCAL_LINK_PREF_PORT = "port"

@Composable
fun LocalLinkReceiverScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    KeepScreenOn()

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(LOCAL_LINK_PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(prefs.getString(LOCAL_LINK_PREF_HOST, "").orEmpty()) }
    var portText by remember { mutableStateOf(prefs.getString(LOCAL_LINK_PREF_PORT, "").orEmpty()) }
    var receiverState by remember { mutableStateOf(ReceiverState.Waiting) }
    var statusMessageRes by remember { mutableStateOf<Int?>(null) }
    var statusDetail by remember { mutableStateOf<String?>(null) }
    var client by remember { mutableStateOf<LocalLinkClient?>(null) }
    var shouldStayConnected by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var packet by remember { mutableStateOf<LyricsPacketMessage?>(null) }
    var clock by remember { mutableStateOf<ClockMessage?>(null) }
    var activeSongId by remember { mutableStateOf<String?>(null) }
    var lastTimeMs by remember { mutableStateOf<Long?>(null) }
    var lastLoggedLineIndex by remember { mutableStateOf<Int?>(null) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    val receiverDeviceName = stringResource(R.string.local_link_receiver_device_name)
    val experimentalToken = stringResource(R.string.local_link_experimental_token)
    val discovery = remember(context) { SmpDeviceDiscovery(context.applicationContext) }
    val discoveredDevices by discovery.devices.collectAsState()
    val localIp = remember { findLocalIpv4Address() ?: "127.0.0.1" }

    val lines = remember(packet) {
        packet?.lines
            ?.map { LrcLine(timeMs = it.timeMs, text = it.text) }
            .orEmpty()
    }
    val activeIndex = remember(lines, clock) {
        findActiveLrcIndex(lines, clock?.timeMs ?: 0L)
    }

    LaunchedEffect(activeSongId, activeIndex) {
        if (activeSongId != null && activeIndex >= 0 && activeIndex != lastLoggedLineIndex) {
            Log.d(
                LOCAL_LINK_DIAG_TAG,
                "receiver_line_index_changed songId=$activeSongId index=$activeIndex"
            )
            lastLoggedLineIndex = activeIndex
        }
    }

    fun disconnect() {
        shouldStayConnected = false
        reconnecting = false
        client?.close()
        client = null
        receiverState = ReceiverState.Disconnected
    }

    fun handleMessage(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> receiverState = ReceiverState.Connected
            is LyricsPacketMessage -> {
                Log.d(
                    LOCAL_LINK_DIAG_TAG,
                    "receiver_packet_received songId=${message.songId} title=${message.title} lineCount=${message.lines.size}"
                )
                packet = message
                clock = null
                lastTimeMs = null
                lastLoggedLineIndex = null
                if (message.lines.isNotEmpty()) {
                    if (activeSongId != message.songId) {
                        Log.d(
                            LOCAL_LINK_DIAG_TAG,
                            "receiver_active_song_changed previous=$activeSongId next=${message.songId}"
                        )
                    }
                    activeSongId = message.songId
                } else {
                    activeSongId = null
                    Log.d(
                        LOCAL_LINK_DIAG_TAG,
                        "receiver_clock_ignored reason=empty_packet songId=${message.songId}"
                    )
                }
                receiverState = ReceiverState.Connected
            }
            is ClockMessage -> {
                Log.d(
                    LOCAL_LINK_DIAG_TAG,
                    "receiver_clock_received songId=${message.songId} timeMs=${message.timeMs}"
                )
                val expectedSongId = activeSongId
                if (expectedSongId == null) {
                    Log.d(
                        LOCAL_LINK_DIAG_TAG,
                        "receiver_clock_ignored reason=missing_packet songId=${message.songId}"
                    )
                    receiverState = ReceiverState.Desynced
                } else if (expectedSongId == message.songId) {
                    clock = message
                    lastTimeMs = message.timeMs
                    receiverState = ReceiverState.Connected
                } else {
                    Log.d(
                        LOCAL_LINK_DIAG_TAG,
                        "receiver_clock_ignored reason=song_mismatch expected=$expectedSongId actual=${message.songId}"
                    )
                    receiverState = ReceiverState.Desynced
                }
            }
            is UnknownMessage -> receiverState = ReceiverState.Desynced
            else -> Unit
        }
    }

    fun connectReceiver() {
        val port = portText.toIntOrNull()
        if (host.isBlank() || port == null) {
            statusMessageRes = R.string.local_link_invalid_address
            receiverState = ReceiverState.Desynced
            return
        }
        prefs.edit()
            .putString(LOCAL_LINK_PREF_HOST, host)
            .putString(LOCAL_LINK_PREF_PORT, portText)
            .apply()
        val nextClient = LocalLinkClient(
            host = host,
            port = port,
            sessionId = "receiver-${System.currentTimeMillis()}",
            token = experimentalToken,
            deviceName = receiverDeviceName,
            reconnectAttempts = 1
        )
        client?.close()
        client = nextClient
        shouldStayConnected = true
        receiverState = if (reconnecting) ReceiverState.Reconnecting else ReceiverState.Waiting
        statusMessageRes = null
        statusDetail = null
        scope.launch {
            Log.d(
                LOCAL_LINK_DIAG_TAG,
                "receiver_connect_start host=$host port=$port"
            )
            val connected = nextClient.connect(scope) { message ->
                scope.launch { handleMessage(message) }
            }
            if (!connected) {
                reconnecting = false
                client = null
                receiverState = ReceiverState.Disconnected
                statusMessageRes = R.string.local_link_connection_failed
                statusDetail = nextClient.lastFailureReason
                Log.w(
                    LOCAL_LINK_DIAG_TAG,
                    "receiver_connect_failed host=$host port=$port reason=${nextClient.lastFailureReason}"
                )
            } else {
                reconnecting = false
                Log.d(
                    LOCAL_LINK_DIAG_TAG,
                    "receiver_connect_ok host=$host port=$port"
                )
                nextClient.send(
                    ReceiverStatusMessage(
                        state = "ready",
                        activeSongId = packet?.songId,
                        seq = clock?.seq ?: 0L
                    )
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { disconnect() }
    }
    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    LaunchedEffect(client, shouldStayConnected, host, portText) {
        while (shouldStayConnected) {
            delay(2_000L)
            val activeClient = client
            if (activeClient != null && !activeClient.session.connected && !reconnecting) {
                reconnecting = true
                receiverState = ReceiverState.Reconnecting
                delay(1_500L)
                connectReceiver()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.common_back_arrow), color = Color(0xFF9E9E9E))
                }
                ReceiverStatusPill(receiverState)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { connectReceiver() }
                ) {
                    Text(
                        text = stringResource(R.string.local_link_reconnect),
                        color = Color(0xFFBDBDBD)
                    )
                }
            }

            if (packet == null || receiverState == ReceiverState.Disconnected || receiverState == ReceiverState.Desynced) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.local_link_receiver_setup_hint),
                            color = Color(0xFFE0E0E0),
                            fontSize = 14.sp
                        )
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
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it.trim() },
                                label = { Text(stringResource(R.string.local_link_host_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter(Char::isDigit) },
                                label = { Text(stringResource(R.string.local_link_port_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    enabled = client == null,
                                    onClick = { connectReceiver() }
                                ) {
                                    Text(stringResource(R.string.local_link_connect))
                                }
                                TextButton(
                                    enabled = client != null,
                                    onClick = { disconnect() }
                                ) {
                                    Text(stringResource(R.string.local_link_disconnect))
                                }
                            }
                        }
                        statusMessageRes?.let { resId ->
                            Text(
                                text = stringResource(resId),
                                color = Color(0xFFFFAB91),
                                fontSize = 13.sp
                            )
                        }
                        statusDetail?.let { detail ->
                            Text(
                                text = detail,
                                color = Color(0xFFFFCCBC),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                DiscoveredSessionsCard(
                    devices = discoveredDevices,
                    onDeviceClick = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.second_screen_discovery_connect_later),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            LyricsWindow(
                title = packet?.title ?: stringResource(R.string.local_link_no_lyrics),
                previous = lines.getOrNull(activeIndex - 1)?.text,
                current = lines.getOrNull(activeIndex)?.text,
                next = lines.getOrNull(activeIndex + 1)?.text,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiscoveredSessionsCard(
    devices: List<SmpDiscoveredDevice>,
    onDeviceClick: (SmpDiscoveredDevice) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121815)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.second_screen_available_devices_title),
                color = Color.White,
                fontSize = 17.sp
            )
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.second_screen_no_devices_found),
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp
                )
            } else {
                devices.forEachIndexed { index, device ->
                    if (index > 0) {
                        HorizontalDivider(color = Color(0xFF26342E))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceClick(device) }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = device.deviceName,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.second_screen_device_available),
                            color = Color(0xFF80CBC4),
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.second_screen_discovered_info_title),
                            color = Color(0xFFBDBDBD),
                            fontSize = 13.sp
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_device_name_label),
                            value = device.deviceName
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_device_id_label),
                            value = device.deviceId
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_nsd_ip_label),
                            value = device.hostAddress?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.local_link_empty_value)
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_nsd_port_label),
                            value = device.port.toString()
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_protocol_version_label),
                            value = device.protocolVersion.toString()
                        )
                        DiagnosticInfoLine(
                            label = stringResource(R.string.second_screen_capabilities_label),
                            value = device.capabilities.joinToString()
                                .takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.local_link_empty_value)
                        )
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

@Composable
private fun DiagnosticInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF8FA59B), fontSize = 12.sp)
        Text(
            text = value,
            color = Color(0xFFE0F2F1),
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun LyricsWindow(
    title: String,
    previous: String?,
    current: String?,
    next: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = Color(0xFFBDBDBD),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(34.dp))
        Text(
            text = previous.orEmpty(),
            color = Color(0xFF5F5F5F),
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = current ?: stringResource(R.string.local_link_waiting_for_clock),
            color = Color.White,
            fontSize = 44.sp,
            textAlign = TextAlign.Center,
            lineHeight = 52.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = next.orEmpty(),
            color = Color(0xFF8A8A8A),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReceiverStatusPill(state: ReceiverState) {
    val color = when (state) {
        ReceiverState.Waiting -> Color(0xFFFFC107)
        ReceiverState.Connected -> Color(0xFF66BB6A)
        ReceiverState.Reconnecting -> Color(0xFF42A5F5)
        ReceiverState.Desynced -> Color(0xFFFF7043)
        ReceiverState.Disconnected -> Color(0xFF9E9E9E)
    }
    Text(
        text = stringResource(state.labelRes),
        color = Color.Black,
        fontSize = 12.sp,
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
internal fun HeaderRow(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back_arrow))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(0.35f))
    }
}

@Composable
internal fun KeepScreenOn() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private enum class ReceiverState(val labelRes: Int) {
    Waiting(R.string.local_link_state_waiting),
    Connected(R.string.local_link_state_connected),
    Reconnecting(R.string.local_link_state_reconnecting),
    Desynced(R.string.local_link_state_desynced),
    Disconnected(R.string.local_link_state_disconnected)
}
