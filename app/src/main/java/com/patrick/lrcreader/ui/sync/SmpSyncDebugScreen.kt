package com.patrick.lrcreader.ui.sync

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.locallink.HelloMessage
import com.patrick.lrcreader.core.locallink.LocalLinkClient
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.core.locallink.LocalLinkServer
import com.patrick.lrcreader.core.locallink.SyncPackageChunkMessage
import com.patrick.lrcreader.core.locallink.SyncPackageEndMessage
import com.patrick.lrcreader.core.locallink.SyncPackageStartMessage
import com.patrick.lrcreader.core.locallink.SyncManifestPayloadMessage
import com.patrick.lrcreader.core.locallink.SyncManifestRequestMessage
import com.patrick.lrcreader.core.locallink.UnknownMessage
import com.patrick.lrcreader.core.sync.SmpSyncManifest
import com.patrick.lrcreader.core.sync.SmpSyncManifestComparator
import com.patrick.lrcreader.core.sync.SmpSyncManifestGenerator
import com.patrick.lrcreader.core.sync.SmpSyncManualSelectionPlanner
import com.patrick.lrcreader.core.sync.SmpSyncPackage
import com.patrick.lrcreader.core.sync.SmpSyncPackageArchiveBuilder
import com.patrick.lrcreader.core.sync.SmpSyncPackageArchiveReader
import com.patrick.lrcreader.core.sync.SmpSyncPackageKind
import com.patrick.lrcreader.core.sync.SmpSyncPlanDiagnostics
import com.patrick.lrcreader.core.sync.SmpSyncDiffDiagnosticsBuilder
import com.patrick.lrcreader.core.sync.SmpSyncPackagePreparationException
import com.patrick.lrcreader.core.sync.SmpSyncPackageProgress
import com.patrick.lrcreader.core.sync.SmpSyncPackageProgressPhase
import com.patrick.lrcreader.core.sync.SmpSyncPackageImportResult
import com.patrick.lrcreader.core.sync.SmpSyncPreparedPackage
import com.patrick.lrcreader.core.sync.SmpSyncDeviceRole
import com.patrick.lrcreader.core.sync.SmpSyncPairingState
import com.patrick.lrcreader.core.sync.SmpSyncPeerStore
import com.patrick.lrcreader.core.sync.SmpSyncReceivedPackage
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummary
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLine
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummaryLineKind
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarySeverity
import com.patrick.lrcreader.core.sync.SmpSyncPlanSummarizer
import com.patrick.lrcreader.core.sync.SmpSyncPlaylistEntry
import com.patrick.lrcreader.core.sync.SmpSyncSongEntry
import com.patrick.lrcreader.core.sync.SyncPackageBuilder
import com.patrick.lrcreader.core.sync.SyncPlan
import com.patrick.lrcreader.core.sync.SyncPlanAction
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.UUID

private const val SMP_SYNC_PREFS = "smp_sync_debug"
private const val SMP_SYNC_PREF_HOST = "host"
private const val SMP_SYNC_PREF_PORT = "port"
private const val SMP_SYNC_PACKAGE_CHUNK_BYTES = 64 * 1024
private const val SMP_SYNC_PACKAGE_PREPARE_TIMEOUT_MS = 300_000L
private const val SMP_SYNC_PACKAGE_DIAG_TAG = "SMP_SYNC_PACKAGE_DIAG"

private enum class ManualSyncCategory {
    SONGS,
    PLAYLISTS,
    NOTES,
    PROMPTERS
}

@Composable
fun SmpSyncDebugScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(SMP_SYNC_PREFS, Context.MODE_PRIVATE)
    }
    var peerState by remember(context) {
        mutableStateOf(SmpSyncPeerStore.get(context.applicationContext))
    }
    val scope = rememberCoroutineScope()
    val localIp = remember { findLocalIpv4Address() ?: "127.0.0.1" }
    val hostDeviceName = peerState.localDeviceName
    val joinDeviceName = peerState.localDeviceName
    val experimentalToken = stringResource(R.string.local_link_experimental_token)
    var localManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var localManifestTitleRes by remember { mutableStateOf(R.string.smp_sync_debug_local_manifest) }
    var comparedManifest by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var comparedManifestTitleRes by remember { mutableStateOf(R.string.smp_sync_debug_fixture_manifest) }
    var syncPlan by remember { mutableStateOf<SyncPlan?>(null) }
    var syncDiagnostics by remember { mutableStateOf<SmpSyncPlanDiagnostics?>(null) }
    var sourceManifestForPackage by remember { mutableStateOf<SmpSyncManifest?>(null) }
    var syncPackage by remember { mutableStateOf<SmpSyncPackage?>(null) }
    var preparedPackage by remember { mutableStateOf<SmpSyncPreparedPackage?>(null) }
    var receivedPackage by remember { mutableStateOf<SmpSyncReceivedPackage?>(null) }
    var importResult by remember { mutableStateOf<SmpSyncPackageImportResult?>(null) }
    var receivePackageId by remember { mutableStateOf<String?>(null) }
    var receiveFile by remember { mutableStateOf<File?>(null) }
    var receiveExpectedBytes by remember { mutableStateOf(0L) }
    var receiveBytes by remember { mutableStateOf(0L) }
    var receiveNextChunkIndex by remember { mutableStateOf(0) }
    var receiveChain by remember { mutableStateOf<Job?>(null) }
    var summary by remember { mutableStateOf<SmpSyncPlanSummary?>(null) }
    var manualSyncExpanded by remember { mutableStateOf(false) }
    var manualCategory by remember { mutableStateOf(ManualSyncCategory.SONGS) }
    var manualSearchQuery by remember { mutableStateOf("") }
    var selectedManualSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedManualPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var labModeExpanded by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isPreparingPackage by remember { mutableStateOf(false) }
    var isSendingPackage by remember { mutableStateOf(false) }
    var isImportingPackage by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf<LocalLinkServer?>(null) }
    var client by remember { mutableStateOf<LocalLinkClient?>(null) }
    var boundPort by remember { mutableStateOf<Int?>(null) }
    var joinHost by remember {
        mutableStateOf(
            prefs.getString(SMP_SYNC_PREF_HOST, "").orEmpty()
                .ifBlank { peerState.peer.lastHost.orEmpty() }
        )
    }
    var joinPortText by remember {
        mutableStateOf(
            prefs.getString(SMP_SYNC_PREF_PORT, "").orEmpty()
                .ifBlank { peerState.peer.lastPort?.toString().orEmpty() }
        )
    }
    var remoteDeviceName by remember { mutableStateOf<String?>(null) }
    var remoteDeviceId by remember { mutableStateOf<String?>(null) }
    var remoteDeviceRole by remember { mutableStateOf<String?>(null) }
    var newDeviceDetected by remember { mutableStateOf(false) }
    var statusRes by remember { mutableStateOf(R.string.smp_sync_debug_status_idle) }
    var statusDetail by remember { mutableStateOf<String?>(null) }
    var seq by remember { mutableLongStateOf(1L) }
    val isBusy = isGenerating || isConnecting || isPreparingPackage || isSendingPackage || isImportingPackage
    val hasSavedConnection = joinHost.isNotBlank() && joinPortText.isNotBlank()

    fun saveJoinTarget() {
        prefs.edit()
            .putString(SMP_SYNC_PREF_HOST, joinHost.trim())
            .putString(SMP_SYNC_PREF_PORT, joinPortText.trim())
            .apply()
    }

    suspend fun buildLocalManifest(deviceId: String? = null): SmpSyncManifest {
        return SmpSyncManifestGenerator().generate(
            context = context.applicationContext,
            appVersion = BuildConfig.VERSION_NAME,
            deviceId = deviceId ?: peerState.localDeviceId
        )
    }

    fun updatePreferredRole(role: SmpSyncDeviceRole) {
        peerState = SmpSyncPeerStore.setPreferredRole(context.applicationContext, role)
    }

    fun forgetPairedDevice() {
        peerState = SmpSyncPeerStore.forgetPairedDevice(context.applicationContext)
        joinHost = ""
        joinPortText = ""
        prefs.edit()
            .remove(SMP_SYNC_PREF_HOST)
            .remove(SMP_SYNC_PREF_PORT)
            .apply()
    }

    fun rememberConnectedPeer(
        host: String? = null,
        port: Int? = null,
        deviceName: String? = null,
        deviceId: String? = null,
        deviceRole: String? = null
    ) {
        remoteDeviceName = deviceName ?: deviceId
        remoteDeviceId = deviceId
        remoteDeviceRole = deviceRole
        newDeviceDetected = !SmpSyncPeerStore.canRememberDevice(peerState, deviceId)
        if (newDeviceDetected) return

        peerState = if (!host.isNullOrBlank() && port != null) {
            SmpSyncPeerStore.rememberEndpoint(
                context = context.applicationContext,
                host = host,
                port = port,
                pairedDeviceName = deviceName,
                pairedDeviceId = deviceId
            )
        } else {
            SmpSyncPeerStore.rememberPairedDevice(
                context = context.applicationContext,
                pairedDeviceName = deviceName,
                pairedDeviceId = deviceId
            )
        }
    }

    suspend fun serializeManifestPayload(
        requestId: String,
        manifest: SmpSyncManifest,
        seqValue: Long
    ): SyncManifestPayloadMessage = withContext(Dispatchers.Default) {
        SyncManifestPayloadMessage.fromManifest(
            requestId = requestId,
            manifest = manifest,
            seq = seqValue
        )
    }

    suspend fun parseManifestPayload(message: SyncManifestPayloadMessage): SmpSyncManifest? {
        return withContext(Dispatchers.Default) {
            message.parseManifestOrNull()
        }
    }

    suspend fun buildComparison(
        source: SmpSyncManifest,
        target: SmpSyncManifest
    ): SyncComparisonResult = withContext(Dispatchers.Default) {
        val plan = SmpSyncManifestComparator().compare(
            source = source,
            target = target
        )
        SyncComparisonResult(
            plan = plan,
            summary = SmpSyncPlanSummarizer().summarize(plan),
            diagnostics = SmpSyncDiffDiagnosticsBuilder().build(
                source = source,
                target = target,
                plan = plan
            )
        )
    }

    suspend fun buildPackagePreview(
        source: SmpSyncManifest,
        plan: SyncPlan
    ): SmpSyncPackage = withContext(Dispatchers.Default) {
        SyncPackageBuilder().build(
            sourceManifest = source,
            plan = plan
        )
    }

    suspend fun buildPackageArchive(
        source: SmpSyncManifest,
        plan: SyncPlan,
        onProgress: (SmpSyncPackageProgress) -> Unit
    ): SmpSyncPreparedPackage {
        return withTimeout(SMP_SYNC_PACKAGE_PREPARE_TIMEOUT_MS) {
            SmpSyncPackageArchiveBuilder(context.applicationContext).build(
                sourceManifest = source,
                plan = plan,
                onProgress = onProgress
            )
        }
    }

    fun clearComparison() {
        comparedManifest = null
        summary = null
        syncPlan = null
        syncDiagnostics = null
        sourceManifestForPackage = null
        syncPackage = null
        preparedPackage = null
        receivedPackage = null
        importResult = null
    }

    fun closeLinks() {
        server?.close()
        client?.close()
        server = null
        client = null
        boundPort = null
        remoteDeviceName = null
        remoteDeviceId = null
        remoteDeviceRole = null
        newDeviceDetected = false
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
                localManifestTitleRes = R.string.smp_sync_debug_local_manifest
                clearComparison()
                if (compareAfterGenerate) {
                    statusRes = R.string.smp_sync_debug_comparing
                    val simulatedTarget = buildSimulatedBackupTarget(generated)
                    val result = buildComparison(
                        source = generated,
                        target = simulatedTarget
                    )
                    syncPlan = result.plan
                    summary = result.summary
                    syncDiagnostics = result.diagnostics
                    sourceManifestForPackage = generated
                    comparedManifest = simulatedTarget
                    comparedManifestTitleRes = R.string.smp_sync_debug_fixture_manifest
                    statusRes = R.string.smp_sync_debug_summary_ready
                }
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_exchange_error
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
            statusRes = R.string.smp_sync_debug_generating_local_manifest
            runCatching {
                val generated = buildLocalManifest()
                localManifest = generated
                localManifestTitleRes = R.string.smp_sync_debug_local_manifest
                statusRes = R.string.smp_sync_debug_sending_manifest
                val payload = serializeManifestPayload(
                    requestId = requestId,
                    manifest = generated,
                    seqValue = seq++
                )
                val sent = sendPayload(payload)
                statusRes = if (sent) {
                    R.string.smp_sync_debug_manifest_sent
                } else {
                    R.string.local_link_no_receiver_connected
                }
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_exchange_error
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun compareWithRemoteManifest(
        message: SyncManifestPayloadMessage,
        remoteIsSource: Boolean,
        echoLocalAnalysis: Boolean = false
    ) {
        if (isGenerating) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            statusDetail = null
            statusRes = R.string.smp_sync_debug_receiving_manifest
            runCatching {
                val remote = parseManifestPayload(message)
                if (remote == null) {
                    clearComparison()
                    statusRes = R.string.smp_sync_debug_invalid_manifest
                    return@runCatching
                }
                val local = buildLocalManifest()
                statusRes = R.string.smp_sync_debug_comparing
                val source = if (remoteIsSource) remote else local
                val target = if (remoteIsSource) local else remote
                val result = buildComparison(
                    source = source,
                    target = target
                )
                syncPlan = result.plan
                summary = result.summary
                syncDiagnostics = result.diagnostics
                sourceManifestForPackage = source
                syncPackage = null
                preparedPackage = null
                localManifest = local
                localManifestTitleRes = if (remoteIsSource) {
                    R.string.smp_sync_debug_backup_phone
                } else {
                    R.string.smp_sync_debug_local_manifest
                }
                comparedManifest = remote
                comparedManifestTitleRes = if (remoteIsSource) {
                    R.string.smp_sync_debug_remote_manifest
                } else {
                    R.string.smp_sync_debug_backup_phone
                }
                if (echoLocalAnalysis) {
                    val payload = serializeManifestPayload(
                        requestId = "backup-analysis-${System.currentTimeMillis()}",
                        manifest = local,
                        seqValue = seq++
                    )
                    client?.send(payload)
                }
                statusRes = R.string.smp_sync_debug_summary_ready
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_exchange_error
                errorMessage = error.message
                    ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun handleServerMessage(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                rememberConnectedPeer(
                    deviceName = message.deviceName,
                    deviceId = message.deviceId,
                    deviceRole = message.deviceRole
                )
                statusDetail = null
                statusRes = R.string.local_link_state_connected
            }
            is SyncManifestRequestMessage -> {
                statusRes = R.string.smp_sync_debug_manifest_request_received
                sendLocalManifestResponse(message.requestId) { payload ->
                    server?.send(payload) ?: false
                }
            }
            is SyncManifestPayloadMessage -> {
                compareWithRemoteManifest(
                    message = message,
                    remoteIsSource = false
                )
            }
            is SyncPackageStartMessage -> {
                statusRes = R.string.smp_sync_debug_invalid_package
            }
            is SyncPackageChunkMessage -> {
                statusRes = R.string.smp_sync_debug_invalid_package
            }
            is SyncPackageEndMessage -> {
                statusRes = R.string.smp_sync_debug_invalid_package
            }
            is UnknownMessage -> {
                statusRes = R.string.smp_sync_debug_invalid_manifest
            }
            else -> Unit
        }
    }

    fun beginReceivingPackage(message: SyncPackageStartMessage) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    receiveFile?.delete()
                    val dir = File(context.cacheDir, "smp_sync_received")
                    if (!dir.exists()) dir.mkdirs()
                    File(dir, "received_${message.packageId}.smpsync").also { file ->
                        if (file.exists()) file.delete()
                        file.createNewFile()
                        receiveFile = file
                    }
                }
                receivedPackage = null
                importResult = null
                receivePackageId = message.packageId
                receiveExpectedBytes = message.totalBytes
                receiveBytes = 0L
                receiveNextChunkIndex = 0
                receiveChain = null
                statusRes = R.string.smp_sync_debug_package_receiving
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_invalid_package)
            }
        }
    }

    fun receivePackageChunk(message: SyncPackageChunkMessage) {
        val packageId = receivePackageId ?: return
        val targetFile = receiveFile ?: return
        val previous = receiveChain
        receiveChain = scope.launch {
            previous?.join()
            if (message.packageId != packageId || message.chunkIndex != receiveNextChunkIndex) {
                statusRes = R.string.smp_sync_debug_invalid_package
                return@launch
            }
            runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    message.decodedBytes
                }
                withContext(Dispatchers.IO) {
                    targetFile.appendBytes(bytes)
                }
                receiveBytes += bytes.size
                receiveNextChunkIndex += 1
                statusRes = R.string.smp_sync_debug_package_receiving
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_invalid_package)
            }
        }
    }

    fun finishReceivingPackage(message: SyncPackageEndMessage) {
        val packageId = receivePackageId ?: return
        val targetFile = receiveFile ?: return
        if (message.packageId != packageId) {
            statusRes = R.string.smp_sync_debug_invalid_package
            return
        }
        scope.launch {
            isGenerating = true
            runCatching {
                receiveChain?.join()
                statusRes = R.string.smp_sync_debug_package_validating
                val actualSha = withContext(Dispatchers.IO) { sha256(targetFile) }
                if (!actualSha.equals(message.sha256, ignoreCase = true)) {
                    statusRes = R.string.smp_sync_debug_invalid_package
                    return@runCatching
                }
                val received = SmpSyncPackageArchiveReader(context.applicationContext)
                    .readReceivedPackage(targetFile)
                if (received == null) {
                    statusRes = R.string.smp_sync_debug_invalid_package
                    return@runCatching
                }
                receivedPackage = received
                receivePackageId = null
                syncPackage = received.syncPackage
                statusRes = R.string.smp_sync_debug_package_received
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_invalid_package)
            }
            isGenerating = false
        }
    }

    fun handleClientMessage(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                rememberConnectedPeer(
                    host = joinHost,
                    port = joinPortText.toIntOrNull(),
                    deviceName = message.deviceName,
                    deviceId = message.deviceId,
                    deviceRole = message.deviceRole
                )
                statusDetail = null
                statusRes = R.string.smp_sync_debug_waiting_for_remote
            }
            is SyncManifestPayloadMessage -> {
                compareWithRemoteManifest(
                    message = message,
                    remoteIsSource = true,
                    echoLocalAnalysis = true
                )
            }
            is SyncPackageStartMessage -> {
                beginReceivingPackage(message)
            }
            is SyncPackageChunkMessage -> {
                receivePackageChunk(message)
            }
            is SyncPackageEndMessage -> {
                finishReceivingPackage(message)
            }
            is UnknownMessage -> {
                statusRes = R.string.smp_sync_debug_invalid_manifest
            }
            else -> Unit
        }
    }

    fun createSession() {
        if (isBusy) return
        closeLinks()
        clearComparison()
        val nextServer = LocalLinkServer(
            sessionId = "smp-sync-host-${System.currentTimeMillis()}",
            token = experimentalToken,
            deviceName = hostDeviceName,
            deviceId = peerState.localDeviceId,
            deviceRole = peerState.preferredRole.name
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
                statusRes = R.string.smp_sync_debug_waiting_device
            }.onFailure { error ->
                nextServer.close()
                server = null
                boundPort = null
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = error.message
                    ?: context.getString(R.string.local_link_server_start_failed)
            }
        }
    }

    fun joinSession() {
        if (isBusy) return
        val port = joinPortText.toIntOrNull()
        if (joinHost.isBlank() || port == null) {
            statusRes = R.string.smp_sync_debug_connection_error
            statusDetail = context.getString(R.string.local_link_invalid_address)
            return
        }
        saveJoinTarget()
        closeLinks()
        clearComparison()
        val nextClient = LocalLinkClient(
            host = joinHost.trim(),
            port = port,
            sessionId = "smp-sync-join-${System.currentTimeMillis()}",
            token = experimentalToken,
            deviceName = joinDeviceName,
            deviceId = peerState.localDeviceId,
            deviceRole = peerState.preferredRole.name,
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
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = nextClient.lastFailureReason
                    ?: context.getString(R.string.local_link_connection_failed)
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
                statusRes = R.string.smp_sync_debug_connection_error
                statusDetail = context.getString(R.string.local_link_no_receiver_connected)
            } else {
                scope.launch {
                    delay(10_000L)
                    if (client == nextClient &&
                        statusRes == R.string.smp_sync_debug_waiting_for_remote
                    ) {
                        statusRes = R.string.smp_sync_debug_connection_error
                        statusDetail = context.getString(R.string.smp_sync_debug_manifest_timeout)
                    }
                }
            }
        }
    }

    fun prepareSyncPackage() {
        val source = sourceManifestForPackage ?: return
        val plan = syncPlan ?: return
        if (isBusy) return
        scope.launch {
            val startedAt = System.currentTimeMillis()
            isPreparingPackage = true
            errorMessage = null
            statusDetail = context.getString(R.string.smp_sync_debug_package_prepare_starting, plan.items.size)
            statusRes = R.string.smp_sync_debug_preparing_sync
            Log.i(
                SMP_SYNC_PACKAGE_DIAG_TAG,
                "ui:prepare_start planItems=${plan.items.size} sourceSongs=${source.songs.size} playlists=${source.playlists.size} families=${source.families.size}"
            )
            runCatching {
                val hosting = server != null
                if (hosting) {
                    val prepared = buildPackageArchive(
                        source = source,
                        plan = plan,
                        onProgress = { progress ->
                            scope.launch {
                                statusDetail = packageProgressText(context, progress)
                            }
                        }
                    )
                    preparedPackage = prepared
                    syncPackage = prepared.syncPackage
                } else {
                    preparedPackage = null
                    syncPackage = buildPackagePreview(
                        source = source,
                        plan = plan
                    )
                }
                statusRes = R.string.smp_sync_debug_ready_to_sync
                statusDetail = context.getString(
                    R.string.smp_sync_debug_package_prepare_done,
                    syncPackage?.itemCount ?: 0
                )
                Log.i(
                    SMP_SYNC_PACKAGE_DIAG_TAG,
                    "ui:prepare_success items=${syncPackage?.itemCount ?: 0} elapsedMs=${System.currentTimeMillis() - startedAt}"
                )
            }.onFailure { error ->
                Log.e(
                    SMP_SYNC_PACKAGE_DIAG_TAG,
                    "ui:prepare_failed elapsedMs=${System.currentTimeMillis() - startedAt}",
                    error
                )
                statusRes = R.string.smp_sync_debug_exchange_error
                statusDetail = when (error) {
                    is TimeoutCancellationException -> {
                        context.getString(R.string.smp_sync_debug_package_prepare_timeout)
                    }
                    is SmpSyncPackagePreparationException -> {
                        context.getString(
                            R.string.smp_sync_debug_package_prepare_failed_item,
                            error.title?.takeIf { it.isNotBlank() }
                                ?: error.entityId?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.local_link_empty_value)
                        )
                    }
                    else -> error.message ?: context.getString(R.string.smp_sync_debug_error)
                }
                errorMessage = statusDetail
            }
            isPreparingPackage = false
            Log.i(
                SMP_SYNC_PACKAGE_DIAG_TAG,
                "ui:prepare_loading_false elapsedMs=${System.currentTimeMillis() - startedAt}"
            )
        }
    }

    suspend fun sendPackageNow(
        prepared: SmpSyncPreparedPackage,
        allowLargeFullSongTransfer: Boolean
    ) {
        val activeServer = server ?: return
        if (!allowLargeFullSongTransfer &&
            prepared.syncPackage.fullSongCount > SmpSyncPlanDiagnostics.LARGE_FULL_SONG_TRANSFER_THRESHOLD
        ) {
            statusRes = R.string.smp_sync_debug_package_send_failed
            statusDetail = context.getString(R.string.smp_sync_debug_excessive_full_sync_blocked)
            return
        }
        isSendingPackage = true
        errorMessage = null
        statusDetail = null
        statusRes = R.string.smp_sync_debug_package_sending
        runCatching {
            val packageId = "package-${System.currentTimeMillis()}-${UUID.randomUUID()}"
            val started = activeServer.send(
                SyncPackageStartMessage(
                    packageId = packageId,
                    totalBytes = prepared.sizeBytes,
                    fullSongCount = prepared.syncPackage.fullSongCount,
                    playlistCount = prepared.syncPackage.playlistStateCount,
                    familyCount = prepared.syncPackage.familyStateCount,
                    replacementSongCount = prepared.syncPackage.items.count {
                        it.diffStatus == com.patrick.lrcreader.core.sync.SyncDiffStatus.MODIFIED_ON_A
                    },
                    seq = seq++
                )
            )
            if (!started) {
                statusRes = R.string.local_link_no_receiver_connected
                return@runCatching
            }
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(SMP_SYNC_PACKAGE_CHUNK_BYTES)
                prepared.file.inputStream().buffered().use { input ->
                    var chunkIndex = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        val chunk = SyncPackageChunkMessage.fromBytes(
                            packageId = packageId,
                            chunkIndex = chunkIndex,
                            bytes = buffer,
                            byteCount = read,
                            seq = seq++
                        )
                        val sent = activeServer.send(chunk)
                        if (!sent) error("send_failed")
                        chunkIndex += 1
                    }
                }
            }
            val ended = activeServer.send(
                SyncPackageEndMessage(
                    packageId = packageId,
                    sha256 = prepared.sha256,
                    seq = seq++
                )
            )
            statusRes = if (ended) {
                peerState = SmpSyncPeerStore.markSyncCompleted(context.applicationContext)
                R.string.smp_sync_debug_package_sent
            } else {
                R.string.local_link_no_receiver_connected
            }
        }.onFailure { error ->
            statusRes = R.string.smp_sync_debug_connection_error
            statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_package_send_failed)
        }
        isSendingPackage = false
    }

    fun sendPreparedPackage() {
        val prepared = preparedPackage ?: return
        if (isBusy) return
        scope.launch {
            sendPackageNow(
                prepared = prepared,
                allowLargeFullSongTransfer = false
            )
        }
    }

    fun prepareManualSelectionAndSend() {
        if (isBusy) return
        val songIds = selectedManualSongIds
        val playlistIds = selectedManualPlaylistIds
        if (songIds.isEmpty() && playlistIds.isEmpty()) {
            statusDetail = context.getString(R.string.smp_sync_manual_empty_selection)
            return
        }
        scope.launch {
            val startedAt = System.currentTimeMillis()
            isPreparingPackage = true
            errorMessage = null
            statusRes = R.string.smp_sync_debug_preparing_sync
            statusDetail = context.getString(
                R.string.smp_sync_manual_prepare_selection,
                songIds.size + playlistIds.size
            )
            runCatching {
                val baseManifest = localManifest ?: buildLocalManifest()
                localManifest = baseManifest
                localManifestTitleRes = R.string.smp_sync_debug_local_manifest
                val source = baseManifest.copy(
                    deviceId = peerState.localDeviceId,
                    generatedAt = System.currentTimeMillis(),
                    songs = baseManifest.songs.filter { song -> song.songId in songIds },
                    playlists = baseManifest.playlists.filter { playlist ->
                        playlist.uiIdentityKey() in playlistIds
                    },
                    families = emptyList(),
                    globalState = null
                )
                val plan = SmpSyncManualSelectionPlanner().buildPlan(
                    sourceManifest = source,
                    selectedSongIds = songIds,
                    selectedPlaylistIds = playlistIds
                )
                if (plan.items.isEmpty()) {
                    statusDetail = context.getString(R.string.smp_sync_manual_empty_selection)
                    return@runCatching
                }
                sourceManifestForPackage = source
                syncPlan = plan
                summary = SmpSyncPlanSummarizer().summarize(plan)
                syncDiagnostics = null
                comparedManifest = null
                preparedPackage = null
                syncPackage = buildPackagePreview(
                    source = source,
                    plan = plan
                )

                val activeServer = server
                if (activeServer == null) {
                    statusRes = R.string.smp_sync_debug_ready_to_sync
                    statusDetail = context.getString(
                        R.string.smp_sync_manual_ready_without_session,
                        syncPackage?.itemCount ?: 0
                    )
                    return@runCatching
                }

                val prepared = buildPackageArchive(
                    source = source,
                    plan = plan,
                    onProgress = { progress ->
                        scope.launch {
                            statusDetail = packageProgressText(context, progress)
                        }
                    }
                )
                preparedPackage = prepared
                syncPackage = prepared.syncPackage
                statusRes = R.string.smp_sync_debug_ready_to_sync
                statusDetail = context.getString(
                    R.string.smp_sync_debug_package_prepare_done,
                    prepared.syncPackage.itemCount
                )
                isPreparingPackage = false
                sendPackageNow(
                    prepared = prepared,
                    allowLargeFullSongTransfer = true
                )
            }.onFailure { error ->
                Log.e(
                    SMP_SYNC_PACKAGE_DIAG_TAG,
                    "ui:manual_prepare_failed elapsedMs=${System.currentTimeMillis() - startedAt}",
                    error
                )
                statusRes = R.string.smp_sync_debug_exchange_error
                statusDetail = when (error) {
                    is TimeoutCancellationException -> {
                        context.getString(R.string.smp_sync_debug_package_prepare_timeout)
                    }
                    is SmpSyncPackagePreparationException -> {
                        context.getString(
                            R.string.smp_sync_debug_package_prepare_failed_item,
                            error.title?.takeIf { it.isNotBlank() }
                                ?: error.entityId?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.local_link_empty_value)
                        )
                    }
                    else -> error.message ?: context.getString(R.string.smp_sync_debug_error)
                }
                errorMessage = statusDetail
            }
            isPreparingPackage = false
        }
    }

    fun importReceivedPackage() {
        val pendingPackage = receivedPackage ?: return
        if (isBusy) return
        scope.launch {
            isImportingPackage = true
            errorMessage = null
            statusDetail = null
            statusRes = R.string.smp_sync_debug_importing_package
            runCatching {
                val result = SmpSyncPackageArchiveReader(context.applicationContext)
                    .importReceivedPackage(
                        receivedPackage = pendingPackage,
                        allowReplace = true
                )
                importResult = result
                if (result.isSuccess) {
                    peerState = SmpSyncPeerStore.markSyncCompleted(context.applicationContext)
                    val postImport = result.postImportDiagnostics
                    syncPlan = postImport?.remainingPlan
                    summary = postImport?.remainingPlan?.let { remainingPlan ->
                        SmpSyncPlanSummarizer().summarize(remainingPlan)
                    }
                    syncDiagnostics = postImport?.planDiagnostics
                    syncPackage = null
                    preparedPackage = null
                    sourceManifestForPackage = postImport
                        ?.remainingPlan
                        ?.takeIf { it.hasPackageActions() }
                        ?.let { pendingPackage.sourceManifest }
                    statusRes = if (postImport?.isUpToDate != false) {
                        R.string.smp_sync_debug_backup_updated
                    } else {
                        R.string.smp_sync_debug_import_done
                    }
                    statusDetail = postImport
                        ?.takeIf { !it.isUpToDate }
                        ?.let {
                            context.getString(
                                R.string.smp_sync_debug_post_import_remaining,
                                it.remainingItemCount
                            )
                        }
                } else {
                    statusRes = R.string.smp_sync_debug_import_failed
                    statusDetail = result.failureReason
                }
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_import_failed
                statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_import_failed)
            }
            isImportingPackage = false
        }
    }

    fun rerunPostImportAnalysis() {
        val pendingPackage = receivedPackage ?: return
        if (isBusy) return
        scope.launch {
            isGenerating = true
            errorMessage = null
            statusDetail = null
            statusRes = R.string.smp_sync_debug_generating_local_manifest
            runCatching {
                val local = buildLocalManifest()
                statusRes = R.string.smp_sync_debug_comparing
                val result = buildComparison(
                    source = pendingPackage.sourceManifest,
                    target = local
                )
                localManifest = local
                localManifestTitleRes = R.string.smp_sync_debug_backup_phone
                comparedManifest = pendingPackage.sourceManifest
                comparedManifestTitleRes = R.string.smp_sync_debug_remote_manifest
                syncPlan = result.plan
                summary = result.summary
                syncDiagnostics = result.diagnostics
                syncPackage = null
                preparedPackage = null
                sourceManifestForPackage = result.plan
                    .takeIf { it.hasPackageActions() }
                    ?.let { pendingPackage.sourceManifest }
                val remainingWorkCount = result.plan.remainingWorkCount()
                statusRes = if (remainingWorkCount == 0) {
                    R.string.smp_sync_debug_backup_updated
                } else {
                    R.string.smp_sync_debug_summary_ready
                }
                statusDetail = remainingWorkCount
                    .takeIf { it > 0 }
                    ?.let {
                        context.getString(
                            R.string.smp_sync_debug_post_import_remaining,
                            it
                        )
                    }
            }.onFailure { error ->
                statusRes = R.string.smp_sync_debug_exchange_error
                statusDetail = error.message ?: context.getString(R.string.smp_sync_debug_error)
            }
            isGenerating = false
        }
    }

    fun cancelReceivedPackage() {
        receiveFile?.delete()
        receiveFile = null
        receivedPackage = null
        importResult = null
        receivePackageId = null
        receiveChain = null
        receiveBytes = 0L
        receiveExpectedBytes = 0L
        receiveNextChunkIndex = 0
        statusRes = R.string.smp_sync_debug_summary_ready
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
            .imePadding()
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
            text = stringResource(R.string.smp_sync_live_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.smp_sync_live_subtitle),
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp
        )

        SyncPeerIdentityCard(
            peerState = peerState,
            remoteDeviceId = remoteDeviceId,
            remoteDeviceRole = remoteDeviceRole,
            newDeviceDetected = newDeviceDetected,
            onSetMain = { updatePreferredRole(SmpSyncDeviceRole.MAIN) },
            onSetBackup = { updatePreferredRole(SmpSyncDeviceRole.BACKUP) },
            onForgetPeer = { forgetPairedDevice() }
        )

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
            hasSavedConnection = hasSavedConnection,
            onCreateSession = { createSession() },
            onJoinSession = { joinSession() },
            onReconnect = { joinSession() },
            onStopSession = { closeLinks() }
        )

        SyncUserStatusCard(
            isBusy = isBusy,
            statusRes = statusRes,
            statusDetail = statusDetail,
            errorMessage = errorMessage
        )

        ManualSelectionSyncCard(
            manifest = localManifest,
            expanded = manualSyncExpanded,
            category = manualCategory,
            searchQuery = manualSearchQuery,
            selectedSongIds = selectedManualSongIds,
            selectedPlaylistIds = selectedManualPlaylistIds,
            isBusy = isBusy,
            isConnected = server != null,
            onToggleExpanded = {
                val shouldOpen = !manualSyncExpanded
                manualSyncExpanded = shouldOpen
                if (shouldOpen && localManifest == null && !isBusy) {
                    generateLocalManifest(compareAfterGenerate = false)
                }
            },
            onCategoryChange = { category ->
                manualCategory = category
                manualSearchQuery = ""
            },
            onSearchChange = { manualSearchQuery = it },
            onToggleSong = { songId ->
                selectedManualSongIds = if (songId in selectedManualSongIds) {
                    selectedManualSongIds - songId
                } else {
                    selectedManualSongIds + songId
                }
            },
            onTogglePlaylist = { playlistId ->
                selectedManualPlaylistIds = if (playlistId in selectedManualPlaylistIds) {
                    selectedManualPlaylistIds - playlistId
                } else {
                    selectedManualPlaylistIds + playlistId
                }
            },
            onSelectAllSongs = { songIds ->
                selectedManualSongIds = selectedManualSongIds + songIds
            },
            onSelectAllPlaylists = { playlistIds ->
                selectedManualPlaylistIds = selectedManualPlaylistIds + playlistIds
            },
            onClear = {
                selectedManualSongIds = emptySet()
                selectedManualPlaylistIds = emptySet()
            },
            onSend = { prepareManualSelectionAndSend() }
        )

        if (receivePackageId != null || receivedPackage != null || importResult != null) {
            ReceivedPackageCard(
                receivedPackage = receivedPackage,
                importResult = importResult,
                receivedBytes = receiveBytes,
                expectedBytes = receiveExpectedBytes,
                isImporting = isImportingPackage || isGenerating,
                onImport = { importReceivedPackage() },
                onCancel = { cancelReceivedPackage() }
            )
        }

        LabModeCard(
            expanded = labModeExpanded,
            isBusy = isBusy,
            isGenerating = isGenerating,
            errorMessage = errorMessage,
            onToggle = { labModeExpanded = !labModeExpanded },
            onAnalyze = { generateLocalManifest(compareAfterGenerate = false) },
            onCompare = { generateLocalManifest(compareAfterGenerate = true) }
        ) {
            localManifest?.let { manifest ->
                ManifestStatsCard(
                    title = stringResource(localManifestTitleRes),
                    manifest = manifest
                )
            }

            comparedManifest?.let { manifest ->
                ManifestStatsCard(
                    title = stringResource(comparedManifestTitleRes),
                    manifest = manifest
                )
            }

            if (summary != null) {
                SyncPackagePreviewCard(
                    syncPackage = syncPackage,
                    preparedPackage = preparedPackage,
                    diagnostics = syncDiagnostics,
                    canPrepare = sourceManifestForPackage != null && syncPlan != null,
                    isPreparing = isPreparingPackage,
                    isSending = isSendingPackage,
                    canSend = server != null &&
                        preparedPackage != null &&
                        syncPackage?.hasExcessiveFullSongs() != true,
                    onPrepare = { prepareSyncPackage() },
                    onSend = { sendPreparedPackage() }
                )
            }

            SyncDiagnosticsCard(diagnostics = syncDiagnostics)
            SummaryCard(summary = summary)
        }
    }
}

@Composable
private fun SyncPeerIdentityCard(
    peerState: SmpSyncPairingState,
    remoteDeviceId: String?,
    remoteDeviceRole: String?,
    newDeviceDetected: Boolean,
    onSetMain: () -> Unit,
    onSetBackup: () -> Unit,
    onForgetPeer: () -> Unit
) {
    val emptyValue = stringResource(R.string.local_link_empty_value)
    val roleLabel = when (peerState.preferredRole) {
        SmpSyncDeviceRole.MAIN -> stringResource(R.string.smp_sync_peer_role_main)
        SmpSyncDeviceRole.BACKUP -> stringResource(R.string.smp_sync_peer_role_backup)
        SmpSyncDeviceRole.UNKNOWN -> stringResource(R.string.smp_sync_peer_role_unknown)
    }
    val endpoint = if (peerState.peer.hasKnownEndpoint) {
        "${peerState.peer.lastHost}:${peerState.peer.lastPort}"
    } else {
        emptyValue
    }
    val lastSync = peerState.peer.lastSyncAt?.let { timestamp ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    } ?: emptyValue
    val remoteRoleLabel = remoteDeviceRole
        ?.let(SmpSyncDeviceRole::fromStoredValue)
        ?.let { role ->
            when (role) {
                SmpSyncDeviceRole.MAIN -> stringResource(R.string.smp_sync_peer_role_main)
                SmpSyncDeviceRole.BACKUP -> stringResource(R.string.smp_sync_peer_role_backup)
                SmpSyncDeviceRole.UNKNOWN -> stringResource(R.string.smp_sync_peer_role_unknown)
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161E24)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_peer_identity_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            InfoLine(
                label = stringResource(R.string.smp_sync_peer_local_name),
                value = peerState.localDeviceName
            )
            InfoLine(
                label = stringResource(R.string.smp_sync_peer_role),
                value = roleLabel
            )
            InfoLine(
                label = stringResource(R.string.smp_sync_peer_paired_device),
                value = peerState.peer.pairedDeviceName ?: emptyValue
            )
            InfoLine(
                label = stringResource(R.string.smp_sync_peer_last_endpoint),
                value = endpoint
            )
            InfoLine(
                label = stringResource(R.string.smp_sync_peer_last_sync),
                value = lastSync
            )
            if (!remoteDeviceId.isNullOrBlank()) {
                Text(
                    text = stringResource(
                        if (newDeviceDetected) {
                            R.string.smp_sync_peer_new_device_detected
                        } else {
                            R.string.smp_sync_peer_known_device
                        }
                    ),
                    color = if (newDeviceDetected) Color(0xFFFFCC80) else Color(0xFFA5D6A7),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            remoteRoleLabel?.let { label ->
                InfoLine(
                    label = stringResource(R.string.smp_sync_peer_remote_role),
                    value = label
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onSetMain,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.smp_sync_peer_set_main))
                }
                TextButton(
                    onClick = onSetBackup,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.smp_sync_peer_set_backup))
                }
            }
            TextButton(
                onClick = onForgetPeer,
                enabled = peerState.peer.hasPairedDevice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_peer_forget))
            }
        }
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
    hasSavedConnection: Boolean,
    onCreateSession: () -> Unit,
    onJoinSession: () -> Unit,
    onReconnect: () -> Unit,
    onStopSession: () -> Unit
) {
    val connected = (isHosting || isJoined) && remoteDeviceName != null
    val connectionStatus = when {
        connected -> stringResource(R.string.smp_sync_debug_connected_to, remoteDeviceName.orEmpty())
        statusRes == R.string.smp_sync_debug_connection_error -> {
            val detail = statusDetail?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.local_link_connection_failed)
            stringResource(R.string.smp_sync_debug_connection_error_with_detail, detail)
        }
        isHosting -> stringResource(R.string.smp_sync_debug_waiting_device)
        isJoined -> stringResource(R.string.smp_sync_debug_waiting_for_remote)
        else -> stringResource(R.string.smp_sync_debug_disconnected)
    }
    val statusColor = when {
        connected -> Color(0xFFA5D6A7)
        statusRes == R.string.smp_sync_debug_connection_error -> Color(0xFFFFAB91)
        isHosting || isJoined -> Color(0xFFFFCC80)
        else -> Color(0xFFB0BEC5)
    }

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
                text = stringResource(R.string.smp_sync_live_connection_title),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = connectionStatus,
                color = statusColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202A2F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            InfoLine(
                label = stringResource(R.string.local_link_remote_label),
                value = remoteDeviceName ?: stringResource(R.string.local_link_empty_value)
            )
            Text(
                text = stringResource(R.string.smp_sync_live_main_phone),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = onCreateSession,
                enabled = !isBusy && !isHosting && !isJoined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_live_create_connection))
            }
            if (isHosting && boundPort != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202A2F), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.smp_sync_live_share_connection_details),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_ip_label),
                        value = localIp
                    )
                    InfoLine(
                        label = stringResource(R.string.local_link_port_label),
                        value = boundPort.toString()
                    )
                }
            }
            Text(
                text = stringResource(R.string.smp_sync_live_backup_phone),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onJoinSession,
                enabled = !isBusy && !isHosting && !isJoined,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_live_join_connection))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onReconnect,
                    enabled = hasSavedConnection && !isBusy && !isHosting && !isJoined,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.local_link_reconnect))
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
private fun SyncUserStatusCard(
    isBusy: Boolean,
    statusRes: Int,
    statusDetail: String?,
    errorMessage: String?
) {
    if (!isBusy && statusDetail.isNullOrBlank() && errorMessage.isNullOrBlank()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color(0xFF90CAF9))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(statusRes),
                    color = if (errorMessage == null) Color.White else Color(0xFFFFAB91),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                val detail = errorMessage?.takeIf { it.isNotBlank() }
                    ?: statusDetail?.takeIf { it.isNotBlank() }
                detail?.let {
                    Text(
                        text = it,
                        color = if (errorMessage == null) Color(0xFFB0BEC5) else Color(0xFFFFAB91),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LabModeCard(
    expanded: Boolean,
    isBusy: Boolean,
    isGenerating: Boolean,
    errorMessage: String?,
    onToggle: () -> Unit,
    onAnalyze: () -> Unit,
    onCompare: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171717)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (expanded) {
                            R.string.smp_sync_lab_hide
                        } else {
                            R.string.smp_sync_lab_show
                        }
                    )
                )
            }

            if (!expanded) return@Column

            Text(
                text = stringResource(R.string.smp_sync_lab_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.smp_sync_lab_subtitle),
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Button(
                onClick = onAnalyze,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_debug_generate_manifest))
            }
            Button(
                onClick = onCompare,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_debug_compare_fixture))
            }

            if (isGenerating) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color(0xFF90CAF9))
                    Text(
                        text = stringResource(R.string.smp_sync_debug_generating),
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp
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

            content()
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
private fun ManualSelectionSyncCard(
    manifest: SmpSyncManifest?,
    expanded: Boolean,
    category: ManualSyncCategory,
    searchQuery: String,
    selectedSongIds: Set<String>,
    selectedPlaylistIds: Set<String>,
    isBusy: Boolean,
    isConnected: Boolean,
    onToggleExpanded: () -> Unit,
    onCategoryChange: (ManualSyncCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSong: (String) -> Unit,
    onTogglePlaylist: (String) -> Unit,
    onSelectAllSongs: (Set<String>) -> Unit,
    onSelectAllPlaylists: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit
) {
    val selectedCount = selectedSongIds.size + selectedPlaylistIds.size
    val normalizedQuery = searchQuery.trim().lowercase()
    val visibleSongs = manifest?.songs
        ?.asSequence()
        ?.filter { song ->
            normalizedQuery.isEmpty() ||
                song.title.lowercase().contains(normalizedQuery) ||
                song.songId.lowercase().contains(normalizedQuery)
        }
        ?.sortedBy { song -> song.title.lowercase() }
        ?.take(80)
        ?.toList()
        .orEmpty()
    val visiblePlaylists = manifest?.playlists
        ?.asSequence()
        ?.filter { playlist ->
            val identity = playlist.uiIdentityKey()
            normalizedQuery.isEmpty() ||
                playlist.playlistName.lowercase().contains(normalizedQuery) ||
                identity.lowercase().contains(normalizedQuery)
        }
        ?.sortedBy { playlist -> playlist.playlistName.lowercase() }
        ?.take(80)
        ?.toList()
        .orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182019)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_manual_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.smp_sync_manual_subtitle),
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp
            )
            Button(
                onClick = onToggleExpanded,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_manual_choose))
            }

            if (!expanded) return@Column

            if (!isConnected) {
                Text(
                    text = stringResource(R.string.smp_sync_manual_no_session_hint),
                    color = Color(0xFFFFCC80),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ManualCategoryButton(
                    text = stringResource(R.string.smp_sync_manual_category_songs),
                    selected = category == ManualSyncCategory.SONGS,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryChange(ManualSyncCategory.SONGS) }
                )
                ManualCategoryButton(
                    text = stringResource(R.string.smp_sync_manual_category_playlists),
                    selected = category == ManualSyncCategory.PLAYLISTS,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryChange(ManualSyncCategory.PLAYLISTS) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ManualCategoryButton(
                    text = stringResource(R.string.smp_sync_manual_category_notes),
                    selected = category == ManualSyncCategory.NOTES,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryChange(ManualSyncCategory.NOTES) }
                )
                ManualCategoryButton(
                    text = stringResource(R.string.smp_sync_manual_category_prompters),
                    selected = category == ManualSyncCategory.PROMPTERS,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryChange(ManualSyncCategory.PROMPTERS) }
                )
            }

            if (category == ManualSyncCategory.NOTES || category == ManualSyncCategory.PROMPTERS) {
                Text(
                    text = stringResource(R.string.smp_sync_manual_future_category),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text(stringResource(R.string.smp_sync_manual_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.smp_sync_manual_selected_count, selectedCount),
                        color = Color(0xFFA5D6A7),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (category == ManualSyncCategory.SONGS) {
                                onSelectAllSongs(visibleSongs.map { it.songId }.toSet())
                            } else {
                                onSelectAllPlaylists(visiblePlaylists.map { it.uiIdentityKey() }.toSet())
                            }
                        },
                        enabled = !isBusy && manifest != null
                    ) {
                        Text(text = stringResource(R.string.smp_sync_manual_select_all))
                    }
                    TextButton(
                        onClick = onClear,
                        enabled = !isBusy && selectedCount > 0
                    ) {
                        Text(text = stringResource(R.string.smp_sync_manual_clear))
                    }
                }

                if (manifest == null) {
                    Text(
                        text = stringResource(R.string.smp_sync_manual_manifest_needed),
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp
                    )
                } else if (category == ManualSyncCategory.SONGS) {
                    ManualSongSelectionList(
                        songs = visibleSongs,
                        selectedSongIds = selectedSongIds,
                        onToggleSong = onToggleSong
                    )
                } else {
                    ManualPlaylistSelectionList(
                        playlists = visiblePlaylists,
                        selectedPlaylistIds = selectedPlaylistIds,
                        onTogglePlaylist = onTogglePlaylist
                    )
                }

                if ((category == ManualSyncCategory.SONGS && visibleSongs.size == 80) ||
                    (category == ManualSyncCategory.PLAYLISTS && visiblePlaylists.size == 80)
                ) {
                    Text(
                        text = stringResource(R.string.smp_sync_manual_list_limited),
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                onClick = onSend,
                enabled = !isBusy && selectedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_manual_send))
            }
        }
    }
}

@Composable
private fun ManualCategoryButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .background(
                if (selected) Color(0xFF26352A) else Color(0xFF202428),
                RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            text = text,
            color = if (selected) Color(0xFFA5D6A7) else Color(0xFFCFD8DC),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ManualSongSelectionList(
    songs: List<SmpSyncSongEntry>,
    selectedSongIds: Set<String>,
    onToggleSong: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        songs.forEach { song ->
            ManualSelectionRow(
                title = song.title,
                subtitle = song.songId,
                checked = song.songId in selectedSongIds,
                onClick = { onToggleSong(song.songId) }
            )
        }
    }
}

@Composable
private fun ManualPlaylistSelectionList(
    playlists: List<SmpSyncPlaylistEntry>,
    selectedPlaylistIds: Set<String>,
    onTogglePlaylist: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        playlists.forEach { playlist ->
            val identity = playlist.uiIdentityKey()
            ManualSelectionRow(
                title = playlist.playlistName,
                subtitle = identity,
                checked = identity in selectedPlaylistIds,
                onClick = { onTogglePlaylist(identity) }
            )
        }
    }
}

@Composable
private fun ManualSelectionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF202820), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFF90A4AE),
                fontSize = 11.sp
            )
        }
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
private fun SyncPackagePreviewCard(
    syncPackage: SmpSyncPackage?,
    preparedPackage: SmpSyncPreparedPackage?,
    diagnostics: SmpSyncPlanDiagnostics?,
    canPrepare: Boolean,
    isPreparing: Boolean,
    isSending: Boolean,
    canSend: Boolean,
    onPrepare: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C20)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_package_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = onPrepare,
                enabled = canPrepare && !isPreparing && !isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_debug_prepare_sync))
            }
            Button(
                onClick = onSend,
                enabled = canSend && !isPreparing && !isSending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.smp_sync_debug_send_package))
            }

            if (isPreparing || isSending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF90CAF9)
                    )
                    Text(
                        text = stringResource(
                            if (isSending) {
                                R.string.smp_sync_debug_package_sending
                            } else {
                                R.string.smp_sync_debug_preparing_sync
                            }
                        ),
                        color = Color(0xFFE0E0E0),
                        fontSize = 13.sp
                    )
                }
            }

            if (syncPackage == null) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_package_empty),
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
                return@Column
            }

            Text(
                text = stringResource(
                    R.string.smp_sync_debug_package_items,
                    syncPackage.itemCount
                ),
                color = Color(0xFFA5D6A7),
                fontSize = 14.sp
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_package_size,
                    preparedPackage?.sizeBytes?.formattedByteSize()
                        ?: syncPackage.formattedEstimatedSize()
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_package_full_songs,
                    syncPackage.fullSongCount
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            if (diagnostics?.hasLargeFullSongTransfer == true) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_excessive_full_sync_blocked),
                    color = Color(0xFFFFCC80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        R.string.smp_sync_debug_many_full_songs_warning,
                        diagnostics.fullSongCount
                    ),
                    color = Color(0xFFFFE0B2),
                    fontSize = 12.sp
                )
                diagnostics.fullSongReasonCounts.entries.take(6).forEach { (reason, count) ->
                    Text(
                        text = stringResource(
                            R.string.smp_sync_debug_reason_count,
                            reason,
                            count
                        ),
                        color = Color(0xFFFFE0B2),
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_package_playlists,
                    syncPackage.playlistStateCount
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_package_families,
                    syncPackage.familyStateCount
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(R.string.smp_sync_debug_package_not_sent),
                color = Color(0xFF90CAF9),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SyncDiagnosticsCard(diagnostics: SmpSyncPlanDiagnostics?) {
    if (diagnostics == null) return
    if (
        diagnostics.modifiedSongs.isEmpty() &&
        diagnostics.modifiedPlaylists.isEmpty() &&
        diagnostics.sameTitleDifferentSongIds.isEmpty()
    ) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF201A16)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_diagnostics_title),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (diagnostics.sameTitleDifferentSongIds.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.smp_sync_debug_same_title_diff_id_title,
                        diagnostics.sameTitleDifferentSongIds.size
                    ),
                    color = Color(0xFFFFCC80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                diagnostics.sameTitleDifferentSongIds.take(8).forEach { item ->
                    Text(
                        text = stringResource(
                            R.string.smp_sync_debug_identity_candidate_line,
                            item.sourceTitle,
                            item.sourceNormalizedTitle,
                            item.sourceSongId,
                            item.targetTitle,
                            item.targetNormalizedTitle,
                            item.targetSongId,
                            item.targetAudioHash ?: "-",
                            item.targetLyricsHash ?: "-"
                        ),
                        color = Color(0xFFFFE0B2),
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.smp_sync_debug_modified_songs_title,
                    diagnostics.modifiedSongs.size
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (diagnostics.fullSongReasonCounts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_full_song_reasons_title),
                    color = Color(0xFFFFCC80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                diagnostics.fullSongReasonCounts.entries.take(6).forEach { (reason, count) ->
                    Text(
                        text = stringResource(
                            R.string.smp_sync_debug_reason_count,
                            reason,
                            count
                        ),
                        color = Color(0xFFFFE0B2),
                        fontSize = 12.sp
                    )
                }
            }
            val fullSongSamples = diagnostics.modifiedSongs
                .filter { song -> song.packageKind == SmpSyncPackageKind.SONG_FULL }
                .take(5)
            if (fullSongSamples.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.smp_sync_debug_full_song_samples_title),
                    color = Color(0xFFFFCC80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                fullSongSamples.forEach { song ->
                    val components = song.differentComponents
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: song.primaryReason
                    val lineText = if (song.sameTitleDifferentSongId != null) {
                        val candidate = song.sameTitleDifferentCandidates.firstOrNull()
                        stringResource(
                            R.string.smp_sync_debug_identity_candidate_line,
                            candidate?.sourceTitle ?: song.title,
                            candidate?.sourceNormalizedTitle ?: song.title,
                            song.sourceSongId,
                            candidate?.targetTitle ?: song.title,
                            candidate?.targetNormalizedTitle ?: song.title,
                            song.sameTitleDifferentSongId,
                            candidate?.targetAudioHash ?: "-",
                            candidate?.targetLyricsHash ?: "-"
                        )
                    } else {
                        stringResource(
                            R.string.smp_sync_debug_full_song_sample_line,
                            song.title,
                            song.sourceSongId,
                            song.targetSongId ?: "-",
                            song.status.name,
                            components
                        )
                    }
                    Text(
                        text = lineText,
                        color = Color(0xFFFFE0B2),
                        fontSize = 12.sp
                    )
                }
            }
            diagnostics.modifiedSongs.take(12).forEach { song ->
                val components = song.differentComponents
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString()
                    ?: song.primaryReason
                val lineText = if (song.sameTitleDifferentSongId != null) {
                    val candidate = song.sameTitleDifferentCandidates.firstOrNull()
                    stringResource(
                        R.string.smp_sync_debug_identity_candidate_line,
                        candidate?.sourceTitle ?: song.title,
                        candidate?.sourceNormalizedTitle ?: song.title,
                        song.sourceSongId,
                        candidate?.targetTitle ?: song.title,
                        candidate?.targetNormalizedTitle ?: song.title,
                        song.sameTitleDifferentSongId,
                        candidate?.targetAudioHash ?: "-",
                        candidate?.targetLyricsHash ?: "-"
                    )
                } else {
                    stringResource(
                        R.string.smp_sync_debug_modified_song_line,
                        song.title,
                        song.primaryReason,
                        components
                    )
                }
                Text(
                    text = lineText,
                    color = Color(0xFFCFD8DC),
                    fontSize = 12.sp
                )
            }
            if (diagnostics.modifiedSongs.size > 12) {
                Text(
                    text = stringResource(
                        R.string.smp_sync_debug_more_modified_songs,
                        diagnostics.modifiedSongs.size - 12
                    ),
                    color = Color(0xFF90A4AE),
                    fontSize = 12.sp
                )
            }

            if (diagnostics.modifiedPlaylists.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.smp_sync_debug_modified_playlists_title,
                        diagnostics.modifiedPlaylists.size
                    ),
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                diagnostics.modifiedPlaylists.take(8).forEach { playlist ->
                    val components = playlist.differentComponents
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: playlist.primaryReason
                    Text(
                        text = stringResource(
                            R.string.smp_sync_debug_modified_playlist_line,
                            playlist.playlistName,
                            playlist.primaryReason,
                            components
                        ),
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp
                    )
                    Text(
                        text = stringResource(
                            R.string.smp_sync_debug_playlist_identity_detail,
                            playlist.sourcePlaylistName ?: playlist.playlistName,
                            playlist.targetPlaylistName ?: "-",
                            playlist.sourceItemCount ?: -1,
                            playlist.targetItemCount ?: -1,
                            playlist.firstDifferentItem ?: "-",
                            if (playlist.orderDifferent) {
                                stringResource(R.string.smp_sync_debug_yes)
                            } else {
                                stringResource(R.string.smp_sync_debug_no)
                            },
                            playlist.duplicateItems.joinToString().ifBlank { "-" }
                        ),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    if (playlist.sameTitleDifferentSongIds.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.smp_sync_debug_playlist_same_title_ids,
                                playlist.sameTitleDifferentSongIds.joinToString { item ->
                                    "${item.sourceTitle}: ${item.sourceSongId} / ${item.targetSongId}"
                                }
                            ),
                            color = Color(0xFFFFE0B2),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedPackageCard(
    receivedPackage: SmpSyncReceivedPackage?,
    importResult: SmpSyncPackageImportResult?,
    receivedBytes: Long,
    expectedBytes: Long,
    isImporting: Boolean,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2024)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.smp_sync_debug_received_title),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (receivedPackage == null) {
                Text(
                    text = stringResource(
                        R.string.smp_sync_debug_receive_progress,
                        receivedBytes.formattedByteSize(),
                        expectedBytes.formattedByteSize()
                    ),
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp
                )
                return@Column
            }

            Text(
                text = stringResource(
                    R.string.smp_sync_debug_received_songs,
                    receivedPackage.fullSongCount
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(
                    R.string.smp_sync_debug_received_playlists,
                    receivedPackage.syncPackage.playlistStateCount
                ),
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp
            )
            Text(
                text = stringResource(R.string.smp_sync_debug_line_no_auto_delete),
                color = Color(0xFF90CAF9),
                fontSize = 12.sp
            )

            importResult?.let { result ->
                val textRes = if (result.isSuccess) {
                    R.string.smp_sync_debug_import_result
                } else {
                    R.string.smp_sync_debug_import_failed_with_reason
                }
                Text(
                    text = if (result.isSuccess) {
                        stringResource(
                            textRes,
                            result.importedSongCount,
                            result.playlistCount,
                            result.familyCount
                        )
                    } else {
                        stringResource(textRes, result.failureReason.orEmpty())
                    },
                    color = if (result.isSuccess) Color(0xFFA5D6A7) else Color(0xFFFFAB91),
                    fontSize = 13.sp
                )
                if (result.isSuccess) {
                    val postImport = result.postImportDiagnostics
                    val statusText = if (postImport?.isUpToDate != false) {
                        stringResource(R.string.smp_sync_debug_backup_updated)
                    } else {
                        stringResource(
                            R.string.smp_sync_debug_post_import_remaining,
                            postImport.remainingItemCount
                        )
                    }
                    Text(
                        text = statusText,
                        color = if (postImport?.isUpToDate != false) Color(0xFFA5D6A7) else Color(0xFFFFCC80),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (importResult?.isSuccess != true) {
                    Button(
                        onClick = onImport,
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.smp_sync_debug_import_package))
                    }
                }
                TextButton(
                    onClick = onCancel,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            if (importResult?.isSuccess == true) {
                                R.string.common_ok
                            } else {
                                R.string.common_cancel
                            }
                        )
                    )
                }
            }
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

@Composable
private fun SmpSyncPackage.formattedEstimatedSize(): String {
    val bytes = estimatedBytes ?: return stringResource(R.string.smp_sync_debug_package_size_unknown)
    return bytes.formattedByteSize()
}

private fun SmpSyncPackage.hasExcessiveFullSongs(): Boolean {
    return fullSongCount > SmpSyncPlanDiagnostics.LARGE_FULL_SONG_TRANSFER_THRESHOLD
}

private fun SmpSyncPlaylistEntry.uiIdentityKey(): String {
    return playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
}

@Composable
private fun Long.formattedByteSize(): String {
    val bytes = coerceAtLeast(0L)
    return when {
        bytes < 1024L -> stringResource(R.string.smp_sync_debug_size_bytes, bytes)
        bytes < 1024L * 1024L -> stringResource(
            R.string.smp_sync_debug_size_kb,
            bytes / 1024.0
        )
        bytes < 1024L * 1024L * 1024L -> stringResource(
            R.string.smp_sync_debug_size_mb,
            bytes / (1024.0 * 1024.0)
        )
        else -> stringResource(
            R.string.smp_sync_debug_size_gb,
            bytes / (1024.0 * 1024.0 * 1024.0)
        )
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(SMP_SYNC_PACKAGE_CHUNK_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun packageProgressText(
    context: Context,
    progress: SmpSyncPackageProgress
): String {
    val itemLabel = progress.title?.takeIf { it.isNotBlank() }
        ?: progress.entityId?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.local_link_empty_value)
    return when (progress.phase) {
        SmpSyncPackageProgressPhase.STARTED -> {
            context.getString(R.string.smp_sync_debug_package_prepare_started)
        }
        SmpSyncPackageProgressPhase.SCANNED_LIBRARY -> {
            context.getString(R.string.smp_sync_debug_package_prepare_scanned)
        }
        SmpSyncPackageProgressPhase.BUILT_PLAN_ITEMS -> {
            context.getString(R.string.smp_sync_debug_package_prepare_items_ready)
        }
        SmpSyncPackageProgressPhase.ITEM_STARTED -> {
            context.getString(
                R.string.smp_sync_debug_package_prepare_item,
                progress.itemIndex,
                progress.itemCount,
                itemLabel
            )
        }
        SmpSyncPackageProgressPhase.ITEM_FINISHED -> {
            context.getString(
                R.string.smp_sync_debug_package_prepare_item_done,
                progress.itemIndex,
                progress.itemCount,
                itemLabel
            )
        }
        SmpSyncPackageProgressPhase.PLAYLISTS_STARTED -> {
            context.getString(R.string.smp_sync_debug_package_prepare_playlists)
        }
        SmpSyncPackageProgressPhase.PLAYLISTS_FINISHED -> {
            context.getString(R.string.smp_sync_debug_package_prepare_playlists_done)
        }
        SmpSyncPackageProgressPhase.HASH_STARTED -> {
            context.getString(R.string.smp_sync_debug_package_prepare_checking)
        }
        SmpSyncPackageProgressPhase.FINISHED -> {
            context.getString(R.string.smp_sync_debug_package_prepare_finished)
        }
    }
}

private data class SyncComparisonResult(
    val plan: SyncPlan,
    val summary: SmpSyncPlanSummary,
    val diagnostics: SmpSyncPlanDiagnostics
)

private fun SyncPlan.hasPackageActions(): Boolean {
    return items.any { item ->
        item.action == SyncPlanAction.COPY_TO_B ||
            item.action == SyncPlanAction.UPDATE_PLAYLIST_ON_B ||
            item.action == SyncPlanAction.UPDATE_FAMILY_ON_B
    }
}

private fun SyncPlan.remainingWorkCount(): Int {
    return items.count { item -> item.action != SyncPlanAction.KEEP }
}

private fun buildSimulatedBackupTarget(source: SmpSyncManifest): SmpSyncManifest {
    val targetSongs = when {
        source.songs.isEmpty() -> listOf(
            SmpSyncSongEntry(
                songId = "simulated_only_on_backup",
                title = "Backup-only test song",
                fullSongHash = "simulated-backup-only"
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
        deviceId = "simulated-backup",
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
