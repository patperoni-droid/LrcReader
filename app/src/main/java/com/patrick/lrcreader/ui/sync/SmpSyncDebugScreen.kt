package com.patrick.lrcreader.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.locallink.HelloMessage
import com.patrick.lrcreader.core.locallink.LocalLinkClient
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.core.locallink.LocalLinkServer
import com.patrick.lrcreader.core.locallink.SyncManifestPayloadMessage
import com.patrick.lrcreader.core.locallink.SyncManifestRequestMessage
import com.patrick.lrcreader.core.locallink.UnknownMessage
import com.patrick.lrcreader.core.sync.SmpSyncManifest
import com.patrick.lrcreader.core.sync.SmpSyncManifestComparator
import com.patrick.lrcreader.core.sync.SmpSyncManifestGenerator
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummary
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLine
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLineKind
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarySeverity
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarizer
import com.patrick.lrcreader.core.sync.SmpSyncSongEntry
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

@Composable
fun SmpSyncDebugScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localIp = remember { findLocalIpv4Address() ?: "127.0.0.1" }
    val hostDeviceName = stringResource(R.string.smp_sync_debug_host_device_name)
    val joinDeviceName = stringResource(R.string.smp_sync_debug_join_device_name)
    val experimentalToken = stringResource(R.string.local_link_experimental_token)
    var localManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var comparedManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var comparedManifestTitleRes by remember { mutableStateOf(R.string.smp_sync_debug_fixture_manifest) }
    var summary by remember { mutableStateOf<SmpSyncPlanSummary?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<LocalLinkServer?>(null) }
    var client by remember { mutableStateOf<LocalLinkClient?>(null) }
    var boundPort by remember { mutableStateOf<Int?>(null) }
    var joinHost by remember { mutableStateOf("") }
    var joinPortText by remember { mutableStateOf("") }
    var remoteDeviceName by remember { mutableStateOf<String?>(null) }
    var statusRes by remember { mutableStateOf(R.string.smp_sync_debug_status_idle) }
    var statusDetail by remember { mutableStateOf<String?>(null) }
    var seq by remember { mutableLongStateOf(1L) }
    val isBusy = isGenerating || isConnecting

    suspend fun buildLocalManifest(deviceId: String? = null): SmpSyncManifest {
        return SmpSyncManifestGenerator().generate(
            context = context.applicationContext,
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceId
        )
    }

    fun closeLinks() {
        server?.close()
        client?.close()
        server = null
        client = null
        boundPort = null
        remoteDeviceName = null
        isConnecting = false
        statusDetail = null
        statusRes = R.string.smp_sync_debug_status_idle
    }

    fun generateLocalManifest(compareAfterGenerate: Boolean) {
        if (isBusy) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            statusDetail = null
            runCatching {
                val generated = buildLocalManifest()
                localManifest = generated
                if (compareAfterGenerate) {
                    val fixture = buildDryRunTargetFixture(generated)
                    val plan = SmpSyncManifestComparator().compare(
                        source = generated,
                        target = fixture
                    )
                    comparedManifest = fixture
                    comparedManifestTitleRes = R.string.smp_sync_debug_fixture_manifest
                    summary = SmpSyncPlanSummarizer().summarize(plan)
                }
            }.onFailure { error ->
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun sendLocalManifestResponse(
        requestId: String,
        sendPayload: suspend (SyncManifestPayloadMessage) -> Boolean
    ) {
        if (isGenerating) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            statusDetail = null
            runCatching {
                val generated = buildLocalManifest(deviceId = "smp-sync-local")
                localManifest = generated
                val sent = sendPayload(
                    SyncManifestPayloadMessage.fromManifest(
                        requestId = requestId,
                        manifest = generated,
                        seq = seq++
                    )
                )
                statusRes = if (sent) {
                    R.string.smp_sync_debug_manifest_sent
                } else {
                    R.string.local_link_no_receiver_connected
                }
            }.onFailure { error ->
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun compareWithRemoteManifest(message: SyncManifestPayloadMessage) {
        if (isGenerating) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            statusDetail = null
            runCatching {
                val remote = message.parseManifestOrNull()
                if (remote == null) {
                    comparedManifest = null
                    summary = null
                    statusRes = R.string.smp_sync_debug_invalid_manifest
                    return@runCatching
                }
                val local = buildLocalManifest(deviceId = "smp-sync-local")
                val plan = SmpSyncManifestComparator().compare(
                    source = remote,
                    target = local
                )
                localManifest = local
                comparedManifest = remote
                comparedManifestTitleRes = R.string.smp_sync_debug_remote_manifest
                summary = SmpSyncPlanSummarizer().summarize(plan)
                statusRes = R.string.smp_sync_debug_manifest_received
            }.onFailure { error ->
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun handleServerMessage(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                remoteDeviceName = message.deviceName
                statusRes = R.string.local_link_state_connected
            }
            is SyncManifestRequestMessage -> {
                statusRes = R.string.smp_sync_debug_manifest_request_received
                sendLocalManifestResponse(message.requestId) { payload ->
                    server?.send(payload) ?: false
                }
            }
            is UnknownMessage -> statusRes = R.string.smp_sync_debug_invalid_manifest
            else -> Unit
        }
    }

    fun handleClientMessage(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                remoteDeviceName = message.deviceName
                statusRes = R.string.smp_sync_debug_waiting_for_remote
            }
            is SyncManifestPayloadMessage -> compareWithRemoteManifest(message)
            is UnknownMessage -> statusRes = R.string.smp_sync_debug_invalid_manifest
            else -> Unit
        }
    }

    fun createSession() {
        if (isBusy) return
        closeLinks()
        comparedManifest = null
        summary = null
        val nextServer = LocalLinkServer(
            sessionId = "smp-sync-host-${System.currentTimeMillis()}",
            token = experimentalToken,
            deviceName = hostDeviceName
        )
        server = nextServer
        statusRes = R.string.smp_sync_debug_session_starting
        scope.launch {
            runCatching {
                nextServer.start(scope) { message ->
                    scope.launch { handleServerMessage(message) }
                }
            }.onSuccess { startedPort ->
                boundPort = startedPort
                statusRes = R.string.smp_sync_debug_session_ready
            }.onFailure { error ->
                nextServer.close()
                server = null
                boundPort = null
                statusRes = R.string.local_link_server_start_failed
                statusDetail = error.message ?: error::class.java.simpleName
            }
        }
    }

    fun joinSession() {
        if (isBusy) return
        val port = joinPortText.toIntOrNull()
        if (joinHost.isBlank() || port == null) {
            statusRes = R.string.local_link_invalid_address
            return
        }
        closeLinks()
        comparedManifest = null
        summary = null
        val nextClient = LocalLinkClient(
            host = joinHost.trim(),
            port = port,
            sessionId = "smp-sync-join-${System.currentTimeMillis()}",
            token = experimentalToken,
            deviceName = joinDeviceName,
            reconnectAttempts = 1
        )
        client = nextClient
        isConnecting = true
        statusRes = R.string.smp_sync_debug_session_connecting
        scope.launch {
            val connected = nextClient.connect(scope) { message ->
                scope.launch { handleClientMessage(message) }
            }
            isConnecting = false
            if (!connected) {
                client = null
                statusRes = R.string.local_link_connection_failed
                statusDetail = nextClient.lastFailureReason
                return@launch
            }
            statusRes = R.string.smp_sync_debug_waiting_for_remote
            val requestId = "manifest-${System.currentTimeMillis()}"
            val sent = nextClient.send(
                SyncManifestRequestMessage(
                    requestId = requestId,
                    seq = seq++
                )
            )
            if (!sent) {
                statusRes = R.string.local_link_no_receiver_connected
            } else {
                scope.launch {
                    delay(10_000L)
                    if (client == nextClient &&
                        statusRes == R.string.smp_sync_debug_waiting_for_remote
                    ) {
                        statusRes = R.string.smp_sync_debug_manifest_timeout
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { closeLinks() }
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF121212),
            Color(0xFF171717),
            Color(0xFF101010)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = {
                closeLinks()
                onBack()
            }
        ) {
            Text(
                text = stringResource(R.string.common_back_arrow),
                color = Color(0xFFB0BEC5)
            )
        }

        Text(
            text = stringResource(R.string.smp_sync_debug_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.smp_sync_debug_subtitle),
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { generateLocalManifest(compareAfterGenerate = false) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.smp_sync_debug_generate_manifest))
                    }
                    Button(
                        onClick = { generateLocalManifest(compareAfterGenerate = true) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.smp_sync_debug_compare_fixture))
                    }
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.smp_sync_debug_sync_disabled))
                }

                if (isGenerating) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF90CAF9)
                        )
                        Text(
                            text = stringResource(R.string.smp_sync_debug_generating),
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFAB91),
                        fontSize = 13.sp
                    )
                }
            }
        }

        LocalLinkDryRunCard(
            localIp = localIp,
            boundPort = boundPort,
            joinHost = joinHost,
            onJoinHostChange = { joinHost = it.trim() },
            joinPortText = joinPortText,
            onJoinPortChange = { joinPortText = it.filter(Char::isDigit) },
            remoteDeviceName = remoteDeviceName,
            statusRes = statusRes,
            statusDetail = statusDetail,
            isBusy = isBusy,
            isHosting = server != null,
            isJoined = client != null,
            onCreateSession = { createSession() },
            onJoinSession = { joinSession() },
            onStopSession = { closeLinks() }
        )

        localManifest?.let { manifest ->
            ManifestStatsCard(
                title = stringResource(R.string.smp_sync_debug_local_manifest),
                manifest = manifest
            )
        }

        comparedManifest?.let { manifest ->
            ManifestStatsCard(
                title = stringResource(comparedManifestTitleRes),
                manifest = manifest
            )
        }

        SummaryCard(summary = summary)
    }
}

@Composable
private fun LocalLinkDryRunCard(
    localIp: String,
    boundPort: Int?,
    joinHost: String,
    onJoinHostChange: (String) -> Unit,
    joinPortText: String,
    onJoinPortChange: (String) -> Unit,
    remoteDeviceName: String?,
    statusRes: Int,
    statusDetail: String?,
    isBusy: Boolean,
    isHosting: Boolean,
    isJoined: Boolean,
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onStopSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C20)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_real_section_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.smp_sync_debug_real_section_subtitle),
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp
            )
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
                value = boundPort?.toString() ?: stringResource(R.string.local_link_empty_value)
            )
            InfoLine(
                label = stringResource(R.string.local_link_remote_label),
                value = remoteDeviceName ?: stringResource(R.string.local_link_empty_value)
            )
            statusDetail?.let { detail ->
                Text(
                    text = detail,
                    color = Color(0xFFFFCCBC),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onCreateSession,
                enabled = !isBusy && !isHosting && !isJoined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_debug_create_session))
            }
            Text(
                text = stringResource(R.string.smp_sync_debug_join_hint),
                color = Color(0xFF90A4AE),
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = joinHost,
                onValueChange = onJoinHostChange,
                label = { Text(stringResource(R.string.local_link_host_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = joinPortText,
                onValueChange = onJoinPortChange,
                label = { Text(stringResource(R.string.local_link_port_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onJoinSession,
                    enabled = !isBusy && !isHosting && !isJoined,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.smp_sync_debug_join_session))
                }
                TextButton(
                    onClick = onStopSession,
                    enabled = isHosting || isJoined,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.smp_sync_debug_stop_session))
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
        Text(
            text = label,
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun ManifestStatsCard(
    title: String,
    manifest: SmpSyncManifest
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_manifest_counts,
                    manifest.songs.size,
                    manifest.playlists.size,
                    manifest.families.size
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(R.string.smp_sync_debug_generated_at, manifest.generatedAt),
                color = Color(0xFF8FA3AD),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: SmpSyncPlanSummary?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_summary_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (summary == null) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_no_summary),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
                return@Column
            }

            summary.lines.forEach { line ->
                SummaryLine(line)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.smp_sync_debug_dry_run_only),
                color = Color(0xFF90CAF9),
                fontSize = 12.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun SummaryLine(line: SmpSyncPlanSummaryLine) {
    val color = when (line.severity) {
        SmpSyncPlanSummarySeverity.INFO -> Color(0xFFCFD8DC)
        SmpSyncPlanSummarySeverity.ACTION -> Color(0xFFA5D6A7)
        SmpSyncPlanSummarySeverity.WARNING -> Color(0xFFFFCC80)
    }
    Text(
        text = summaryLineText(line),
        color = color,
        fontSize = 14.sp
    )
}

@Composable
private fun summaryLineText(line: SmpSyncPlanSummaryLine): String {
    return when (line.kind) {
        SmpSyncPlanSummaryLineKind.SONGS_IDENTICAL ->
            stringResource(R.string.smp_sync_debug_line_songs_identical, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_B ->
            stringResource(R.string.smp_sync_debug_line_songs_absent_on_b, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_A ->
            stringResource(R.string.smp_sync_debug_line_songs_modified_on_a, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_B ->
            stringResource(R.string.smp_sync_debug_line_songs_modified_on_b, line.count)
        SmpSyncPlanSummaryLineKind.POSSIBLE_CONFLICTS ->
            stringResource(R.string.smp_sync_debug_line_conflicts, line.count)
        SmpSyncPlanSummaryLineKind.PLAYLISTS_DIFFERENT ->
            stringResource(R.string.smp_sync_debug_line_playlists_different, line.count)
        SmpSyncPlanSummaryLineKind.FAMILIES_DIFFERENT ->
            stringResource(R.string.smp_sync_debug_line_families_different, line.count)
        SmpSyncPlanSummaryLineKind.BROKEN_REFERENCES ->
            stringResource(R.string.smp_sync_debug_line_broken_references, line.count)
        SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_A ->
            stringResource(R.string.smp_sync_debug_line_songs_absent_on_a, line.count)
        SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION ->
            stringResource(R.string.smp_sync_debug_line_no_auto_delete)
    }
}

private fun buildDryRunTargetFixture(source: SmpSyncManifest): SmpSyncManifest {
    val targetSongs = when {
        source.songs.isEmpty() -> listOf(
            SmpSyncSongEntry(
                songId = "fixture_only_on_backup",
                title = "Fixture backup only",
                fullSongHash = "fixture-backup-only"
            )
        )
        source.songs.size == 1 -> emptyList()
        else -> source.songs.dropLast(1).mapIndexed { index, song ->
            if (index == 0) {
                song.copy(fullSongHash = "${song.fullSongHash}:backup")
            } else {
                song
            }
        }
    }

    val targetPlaylists = source.playlists.mapIndexed { index, playlist ->
        if (index == 0) {
            playlist.copy(fullPlaylistHash = "${playlist.fullPlaylistHash}:backup")
        } else {
            playlist
        }
    }

    val targetFamilies = source.families.mapIndexed { index, family ->
        if (index == 0) {
            family.copy(hash = "${family.hash}:backup")
        } else {
            family
        }
    }

    return source.copy(
        deviceId = "fixture-backup",
        generatedAt = source.generatedAt,
        songs = targetSongs,
        playlists = targetPlaylists,
        families = targetFamilies
    )
}

private fun findLocalIpv4Address(): String? {
    return runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { address -> networkInterface.name to address.hostAddress.orEmpty() }
            }
            .filter { (_, address) -> address.isNotBlank() }
            .sortedWith(
                compareBy<Pair<String, String>>(
                    { (name, _) ->
                        if (name.contains("wlan", ignoreCase = true) ||
                            name.contains("ap", ignoreCase = true)
                        ) {
                            0
                        } else {
                            1
                        }
                    },
                    { (_, address) ->
                        if (address.startsWith("192.168.") ||
                            address.startsWith("172.") ||
                            address.startsWith("10.")
                        ) {
                            0
                        } else {
                            1
                        }
                    }
                )
            )
            .map { (_, address) -> address }
            .distinct()
            .firstOrNull()
    }.getOrNull()
}
