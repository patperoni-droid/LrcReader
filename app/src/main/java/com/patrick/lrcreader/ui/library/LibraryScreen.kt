package com.patrick.lrcreader.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsInternal
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.ImportAudioManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.SmpPreparationNoticePrefs
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.core.search.SearchEngine
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpBatchImportProcessor
import com.patrick.lrcreader.smp.SmpConverter
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpImportedUiSignal
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.LibraryFolderCache
import com.patrick.lrcreader.ui.MoveResult
import com.patrick.lrcreader.ui.SmpPreparationNoticeDialog
import com.patrick.lrcreader.ui.clearPersistedUris
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.patrick.lrcreader.core.StorageModePrefs
import androidx.compose.runtime.saveable.rememberSaveable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private val PROMPTER_FOLDER_URI: Uri = Uri.parse("spl-prompter://folder")
private val SMP_FOLDER_URI: Uri = Uri.parse("spl-smp://folder")
private const val LIBRARY_VIEW_MODE_SONGS = "songs"
private const val LIBRARY_VIEW_MODE_FILES = "files"
private const val IMPORT_PROOF_TAG = "IMPORT_PROOF"
private const val IMPORT_TRACE_TAG = "IMPORT_TRACE"
private const val SMP_VIEW_TRACE_TAG = "SMP_VIEW_TRACE"
private const val LIB_SMP_TRACE_TAG = "LIB_SMP_TRACE"
private const val LIBRARY_PERF_TRACE_TAG = "LIBRARY_PERF_TRACE"
private val buildEntriesPerfCounter = AtomicInteger(0)
private val initialLoadEffectPerfCounter = AtomicInteger(0)
private val refreshCurrentEffectPerfCounter = AtomicInteger(0)
private val folderChangeEffectPerfCounter = AtomicInteger(0)
private val importedSmpEffectPerfCounter = AtomicInteger(0)
private val uiEntriesSnapshotPerfCounter = AtomicInteger(0)
private val buildSmpEntriesTraceCounter = AtomicInteger(0)

private fun isPrompterFolderUri(uri: Uri?): Boolean = uri?.scheme == "spl-prompter"
private fun isSmpFolderUri(uri: Uri?): Boolean = uri?.scheme == "spl-smp"

private fun summarizeLibraryEntries(entries: List<LibraryEntry>, limit: Int = 20): String {
    val rendered = entries.take(limit).joinToString(", ") { entry ->
        val type = if (entry.isDirectory) "dir" else "file"
        "$type:${entry.name}:${entry.uri}"
    }
    return if (entries.size > limit) "[$rendered, ...]" else "[$rendered]"
}

private fun summarizeSmpSongs(
    songs: List<com.patrick.lrcreader.smp.SongUnit>,
    limit: Int = 20
): String {
    val rendered = songs.take(limit).joinToString(", ") { song ->
        "${song.id}:${song.title}"
    }
    return if (songs.size > limit) "[$rendered, ...]" else "[$rendered]"
}

private data class PendingPlaylistAssignRequest(
    val playlistName: String,
    val directItemUris: List<String>,
    val batchPlan: SmpBatchImportProcessor.BatchPlan?
)

private data class BatchProgressVisualBounds(
    val floor: Float,
    val ceiling: Float
)

private val HIDDEN_LEGACY_FOLDER_NAMES = setOf(
    "backingtracks",
    "accords",
    "audio",
    "lyrics",
    "midi",
    "videos",
    "export",
    "exports",
    "import",
    "imports"
)

private fun extractPrompterId(uri: Uri): String? {
    val raw = uri.toString()
    if (!raw.startsWith("prompter://")) return null
    return raw.removePrefix("prompter://").ifBlank { null }
}

private fun shouldHideLegacyFolderName(name: String): Boolean {
    return name.trim().lowercase() in HIDDEN_LEGACY_FOLDER_NAMES
}

private fun isBackupFolderName(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized == "backup" || normalized == "backups"
}

private fun isConfigFolderName(name: String): Boolean {
    return name.trim().equals("Config", ignoreCase = true)
}

private fun isDjFolderName(name: String): Boolean {
    return name.trim().equals("DJ", ignoreCase = true)
}

private fun shouldHideFromMainLibrary(name: String): Boolean {
    return shouldHideLegacyFolderName(name) ||
        isBackupFolderName(name) ||
        isConfigFolderName(name) ||
        isDjFolderName(name)
}

private fun resolveFolderName(context: android.content.Context, uri: Uri): String? {
    return when (uri.scheme) {
        "file" -> File(uri.path ?: "").name.ifBlank { null }
        "spl-prompter",
        "spl-smp" -> null
        else -> {
            (DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri))
                ?.name
                ?.takeIf { it.isNotBlank() }
        }
    }
}

@Composable
private fun LibraryViewModeButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) accent else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    workspaceSnapshot: WorkspaceResolver.Snapshot,
    workspaceVersion: Int = 0,
    currentPlayingSongId: String? = null,
    reselectRootSignal: Int = 0,
    searchToggleSignal: Int = 0,
    smpRefreshVersion: Int = 0,
    lastImportedSmpSignal: SmpImportedUiSignal? = null,
    onConsumeImportedSmpAutoOpen: () -> Unit = {},
    onWorkspaceChanged: () -> Unit = {},
    onAfterBackupImport: () -> Unit = {},
    onImportExternalSmp: () -> Unit,
    onSyncWorkspaceSmpArchives: suspend () -> Int = { 0 },
    onImportGeneratedSmp: suspend (Uri) -> com.patrick.lrcreader.smp.SongUnit?,
    onImportGeneratedSmpFailureReason: () -> String? = { null },
    onDeleteSmpSong: suspend (String) -> Boolean = { false },
    onPlayFromLibrary: (String) -> Unit
) {
    val context = LocalContext.current
    Log.e("SIG_LIB", "SIG#0 TOP composable 2026-02-08 18:00 Z")
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }

    // Palette analogique commune
    val titleColor = Color(0xFFFFF8E1)
    val subtitleColor = Color(0xFFB0BEC5)
    val cardBg = Color(0xFF181818)
    val rowBorder = Color(0x33FFFFFF)
    val accent = Color(0xFFFFC107)

    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Strings (pré-calculés, utilisables partout, y compris dans les callbacks)
    val sScanning = stringResource(R.string.library_scanning)
    val sMoving = stringResource(R.string.library_moving)
    val sImporting = stringResource(R.string.library_importing_music)
    val sConvertingSmp = stringResource(R.string.library_converting_smp)
    val sDeleting = stringResource(R.string.library_deleting)
    val sRenaming = stringResource(R.string.library_renaming)
    val sLoading = stringResource(R.string.common_loading)
    val sSearch = stringResource(R.string.common_search_placeholder)
    val sNoFolderHint = stringResource(R.string.library_no_folder_hint)
    val sNoFolderSelected = stringResource(R.string.library_no_folder_selected)
    val sBackupImportAction = stringResource(R.string.library_list_import_backup)
    val sBackupImporting = stringResource(R.string.backup_import_in_progress)
    val sBackupImportSuccess = stringResource(R.string.backup_import_success)
    val sBackupImportEmptyUnreadable = stringResource(R.string.backup_import_empty_unreadable)
    val sBackupUnknownError = stringResource(R.string.backup_unknown_error)
    val sDjExcludedReason = stringResource(R.string.library_dj_excluded_reason)
    val sDeleteBackingTrackTitle = stringResource(R.string.library_delete_backing_track_title)
    val sDeleteFileTitle = stringResource(R.string.library_delete_file_title)
    val sDeleteFolderTitle = stringResource(R.string.library_delete_folder_title)
    val sDeleteSelectedTitle = stringResource(R.string.library_delete_selected_title)
    val sDeleteAudioOnly = stringResource(R.string.library_delete_audio_question)
    val sDeleteAudioPlusLrc = stringResource(R.string.library_delete_audio_plus_lrc)
    val sDeleteConfirmText = stringResource(R.string.library_delete_file_confirm_text)
    val sDeleteFolderConfirmText = stringResource(R.string.library_delete_folder_confirm_text)
    val sDeletePermanently = stringResource(R.string.library_list_delete_permanently)
    val sDeleteSmpTitle = stringResource(R.string.library_delete_smp_title)
    val sDeleteSmpConfirmText = stringResource(R.string.library_delete_smp_confirm_text)
    val sDeleteSmpFailed = stringResource(R.string.library_delete_smp_failed)
    val sShareSmpFailed = stringResource(R.string.library_share_smp_failed)
    val sCopyFailed = stringResource(R.string.library_copy_failed)
    val sPrompterFolder = stringResource(R.string.main_menu_prompter)
    val sSmpFolder = stringResource(R.string.library_smp_folder)
    val sSmpEmptyState = stringResource(R.string.library_smp_empty_state)
    val sSmpDetectedImporting = stringResource(R.string.library_smp_detected_importing)
    val sSongsView = stringResource(R.string.library_view_mode_songs)
    val sFilesView = stringResource(R.string.library_view_mode_files)
    val sConvertSmpSingleSuccess = stringResource(R.string.library_convert_smp_success_single)
    val sConvertSmpSingleFailed = stringResource(R.string.library_convert_smp_failed_single)
    val sConvertSmpNoMp3 = stringResource(R.string.library_convert_smp_no_mp3)
    val sCopying = stringResource(R.string.library_copying)
    val sBatchPreparing = stringResource(R.string.smp_batch_progress_title)
    val sBatchUnsupportedOnly = stringResource(R.string.smp_batch_unsupported_only)
    val sBatchStageConverting = stringResource(R.string.smp_batch_stage_converting)
    val sBatchStageImporting = stringResource(R.string.smp_batch_stage_importing)
    val sBatchStagePersistingArchive = stringResource(R.string.smp_batch_stage_persisting_archive)
    val sBatchStagePlaylist = stringResource(R.string.smp_batch_stage_playlist)

    // State
    var showLrcEditor by remember { mutableStateOf(false) }
    var lrcEditorUri by remember { mutableStateOf<Uri?>(null) }
    var lrcEditorName by remember { mutableStateOf("") }
    var lrcEditorText by remember { mutableStateOf("") }

    val storageMode = workspaceSnapshot.mode

    val backend: LibraryBackend = remember(workspaceVersion, storageMode) {
        when (storageMode) {
            StorageModePrefs.Mode.INTERNAL -> LibraryBackendInternal(context, workspaceSnapshot)
            StorageModePrefs.Mode.SAF -> LibraryBackendSaf(context, workspaceSnapshot)
            else -> LibraryBackendSaf(context, workspaceSnapshot) // sécurité si un jour il y a une 3e valeur / null / migration
        }
    }

    Log.e("SIG_LIB", "BOOT storageMode=$storageMode backend=${backend.javaClass.simpleName}")

    val initialFolder = remember(workspaceVersion, storageMode, workspaceSnapshot.workspaceRootUri) {
        backend.getRootUri()
    }
    var libraryViewMode by rememberSaveable { mutableStateOf(LIBRARY_VIEW_MODE_SONGS) }
    var currentFolderUri by remember { mutableStateOf<Uri?>(initialFolder) }
    var folderStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var songItems by remember { mutableStateOf<List<LibrarySongItem>>(emptyList()) }
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingStartedAt by remember { mutableStateOf(0L) }
    var moveProgress by remember { mutableStateOf<Float?>(null) }
    var moveLabel by remember { mutableStateOf<String?>(null) }

    var indexAll by remember { mutableStateOf<List<LibraryIndexCache.CachedEntry>>(emptyList()) }
    var importTargetFolderUri by remember { mutableStateOf<Uri?>(null) }
    val smpConverter = remember(context) { SmpConverter(context) }
    val smpBatchProcessor = remember(context) { SmpBatchImportProcessor(context, smpConverter) }
    val smpLibraryScanner = remember(context) { SmpLibraryScanner(context) }
    var initialLoadDone by remember { mutableStateOf(false) }
    var lastHandledImportedSmpRefresh by remember { mutableIntStateOf(-1) }
    var importedAutoOpenDeferredRetryRequestVersion by remember { mutableIntStateOf(-1) }
    var importedAutoOpenDeferredRetryToken by remember { mutableIntStateOf(0) }
    var importedAutoOpenReconcileVersion by remember { mutableIntStateOf(-1) }
    var archivePersistProofShown by remember { mutableStateOf(false) }
    var importedAutoOpenLaunchProofVersion by remember { mutableIntStateOf(-1) }
    var importedAutoOpenDeferredProofVersion by remember { mutableIntStateOf(-1) }
    var importedAutoOpenSuccessProofVersion by remember { mutableIntStateOf(-1) }
    var pendingDirectImportPlan by remember {
        mutableStateOf<SmpBatchImportProcessor.BatchPlan?>(null)
    }
    var currentBatchProgress by remember {
        mutableStateOf<SmpBatchImportProcessor.Progress?>(null)
    }
    var lastLoggedMoveProgressBucket by remember { mutableIntStateOf(-1) }

    fun initialFolderStack(root: Uri, folderToShow: Uri): List<Uri> {
        return if (folderToShow.toString() == root.toString()) emptyList() else listOf(root)
    }

    fun resolveFilesInitialFolder(
        root: Uri,
        indexSnapshot: List<LibraryIndexCache.CachedEntry> = indexAll
    ): Uri {
        return backend.chooseInitialFolder(root, indexSnapshot)
    }

    fun normalizeSelection(selection: Collection<Uri>): List<Uri> {
        return selection.distinctBy { it.toString() }
    }

    fun emitImportProof(
        phase: String,
        detail: String,
        toastMessage: String? = null
    ) {
        Log.w(
            IMPORT_PROOF_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} file=LibraryScreen.kt phase=$phase detail=$detail"
        )
        toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun batchProgressBounds(progress: SmpBatchImportProcessor.Progress): BatchProgressVisualBounds {
        val total = progress.totalCount.coerceAtLeast(1)
        val base = (progress.currentItemIndex - 1).coerceAtLeast(0).toFloat() / total.toFloat()
        val stageRange = when (progress.stage) {
            SmpBatchImportProcessor.ProgressStage.CONVERTING -> 0f to 0.28f
            // Library direct imports do not have a playlist stage, so IMPORTING must carry
            // the visual progress almost to completion instead of plateauing too early.
            SmpBatchImportProcessor.ProgressStage.IMPORTING -> 0.28f to 0.992f
            SmpBatchImportProcessor.ProgressStage.ADDING_TO_PLAYLIST -> 0.992f to 0.997f
        }
        return BatchProgressVisualBounds(
            floor = (base + stageRange.first / total.toFloat()).coerceIn(0f, 1f),
            ceiling = (base + stageRange.second / total.toFloat()).coerceIn(0f, 1f)
        )
    }

    fun batchProgressLabel(
        progress: SmpBatchImportProcessor.Progress,
        persistingArchive: Boolean = false
    ): String {
        val stageLabel = when {
            persistingArchive && progress.stage == SmpBatchImportProcessor.ProgressStage.IMPORTING ->
                sBatchStagePersistingArchive

            else -> when (progress.stage) {
            SmpBatchImportProcessor.ProgressStage.CONVERTING -> sBatchStageConverting
            SmpBatchImportProcessor.ProgressStage.IMPORTING -> sBatchStageImporting
            SmpBatchImportProcessor.ProgressStage.ADDING_TO_PLAYLIST -> sBatchStagePlaylist
            }
        }
        return "$stageLabel ${progress.currentItemIndex}/${progress.totalCount}\n${progress.displayName}"
    }

    LaunchedEffect(
        currentBatchProgress?.currentItemIndex,
        currentBatchProgress?.totalCount,
        currentBatchProgress?.stage
    ) {
        val progress = currentBatchProgress ?: return@LaunchedEffect
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=library_current_batch_progress index=${progress.currentItemIndex}/${progress.totalCount} stage=${progress.stage} displayName=${progress.displayName}"
        )
        val bounds = batchProgressBounds(progress)
        moveProgress = (moveProgress ?: 0f).coerceAtLeast(bounds.floor)

        when (progress.stage) {
            SmpBatchImportProcessor.ProgressStage.IMPORTING -> {
                val total = progress.totalCount.coerceAtLeast(1).toFloat()
                val minStep = 0.0045f / total
                val itemVisualEnd = (progress.currentItemIndex.toFloat() / total).coerceIn(0f, 1f)
                val archivePersistLabel = batchProgressLabel(progress, persistingArchive = true)
                val lingerCeiling = (
                    itemVisualEnd - (0.0015f / total)
                    ).coerceAtLeast(bounds.ceiling).coerceAtMost(0.9985f)
                while (currentBatchProgress == progress) {
                    val current = moveProgress ?: bounds.floor
                    if (current >= bounds.ceiling) {
                        if (moveLabel != archivePersistLabel) {
                            moveLabel = archivePersistLabel
                        }
                        if (!archivePersistProofShown) {
                            archivePersistProofShown = true
                            emitImportProof(
                                phase = "archive_persist_label_path",
                                detail = "item=${progress.currentItemIndex}/${progress.totalCount} name=${progress.displayName}",
                                toastMessage = "IMPORT_PROOF archive persist UI path"
                            )
                        }
                        if (current < lingerCeiling) {
                            val remaining = lingerCeiling - current
                            val step = maxOf(remaining * 0.08f, minStep * 0.25f)
                            moveProgress = (current + step).coerceAtMost(lingerCeiling)
                        }
                        delay(200L)
                        continue
                    }
                    val remaining = bounds.ceiling - current
                    val step = maxOf(remaining * 0.14f, minStep)
                    moveProgress = (current + step).coerceAtMost(bounds.ceiling)
                    delay(180L)
                }
            }

            else -> {
                repeat(6) { index ->
                    if (currentBatchProgress != progress) return@LaunchedEffect
                    val fraction = (index + 1).toFloat() / 6f
                    val target = bounds.floor + ((bounds.ceiling - bounds.floor) * fraction)
                    val current = moveProgress ?: bounds.floor
                    if (target > current) {
                        moveProgress = target
                    }
                    delay(40L)
                }
            }
        }
    }

    LaunchedEffect(moveProgress, isLoading) {
        val progress = moveProgress
        if (!isLoading || progress == null) {
            lastLoggedMoveProgressBucket = -1
            return@LaunchedEffect
        }
        val bucket = (((progress.coerceIn(0f, 1f)) * 100f).toInt() / 5) * 5
        if (bucket != lastLoggedMoveProgressBucket) {
            lastLoggedMoveProgressBucket = bucket
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=library_move_progress_bucket bucketPct=$bucket rawProgress=$progress label=${moveLabel ?: "null"}"
            )
        }
    }

    fun runDirectBatchImport(plan: SmpBatchImportProcessor.BatchPlan) {
        scope.launch {
            loadingStartedAt = System.currentTimeMillis()
            isLoading = true
            moveLabel = sBatchPreparing
            moveProgress = 0f
            currentBatchProgress = null
            archivePersistProofShown = false
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=library_direct_batch_start totalCount=${plan.totalCount} supportedCount=${plan.supportedCount}"
            )
            if (plan.hasAudioToPrepare) {
                emitImportProof(
                    phase = "library_direct_batch_audio_path",
                    detail = "totalCount=${plan.totalCount} supportedCount=${plan.supportedCount}",
                    toastMessage = "IMPORT_PROOF audio->SMP batch path"
                )
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    smpBatchProcessor.process(
                        plan = plan,
                        importSmp = onImportGeneratedSmp,
                        importFailureReasonProvider = onImportGeneratedSmpFailureReason,
                        onProgress = { progress ->
                            mainHandler.post {
                                currentBatchProgress = progress
                                moveLabel = batchProgressLabel(progress)
                            }
                        }
                    )
                }

                currentBatchProgress = null
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=library_direct_batch_force_100 successCount=${result.successCount} failureCount=${result.failureCount}"
                )
                moveProgress = 1f
                moveLabel = sBatchPreparing

                val summaryMessage = context.getString(
                    R.string.smp_batch_import_summary,
                    result.successCount,
                    result.failureCount
                )
                Toast.makeText(context, summaryMessage, Toast.LENGTH_SHORT).show()
            } finally {
                pendingDirectImportPlan = null
                importTargetFolderUri = null
                currentBatchProgress = null
                val elapsed = System.currentTimeMillis() - loadingStartedAt
                val minMs = 500L
                if (elapsed < minMs) delay(minMs - elapsed)
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=library_direct_batch_end durationMs=$elapsed"
                )
                isLoading = false
                moveProgress = null
                moveLabel = null
            }
        }
    }

    fun buildPrompterEntries(): List<LibraryEntry> {
        return TextSongRepository.listAll(context).map { song ->
            LibraryEntry(
                uri = Uri.parse(song.uri),
                name = song.title.ifBlank { "Prompter" },
                isDirectory = false
            )
        }
    }

    fun resolveSongDisplayTitle(songId: String, fallbackTitle: String): String {
        val playbackItem = buildSmpItem(songId)
        return TitleAliasesStore.getTitleForTrack(context, playbackItem)
            ?: PlaylistRepository.getAnyCustomTitleForUri(playbackItem)
            ?: fallbackTitle
    }

    fun buildLibrarySongItems(): List<LibrarySongItem> {
        val songs = smpLibraryScanner.listSongs()
        return songs
            .map { song ->
                val fallbackTitle = song.title.ifBlank { song.id }
                val playbackItem = buildSmpItem(song.id)
                LibrarySongItem(
                    song = song,
                    playbackItem = playbackItem,
                    displayTitle = resolveSongDisplayTitle(song.id, fallbackTitle),
                    fallbackTitle = fallbackTitle
                )
            }
            .sortedBy { it.displayTitle.lowercase() }
    }

    suspend fun buildLibrarySongItemsAsync(): List<LibrarySongItem> = withContext(Dispatchers.IO) {
        buildLibrarySongItems()
    }

    fun buildSmpEntries(): List<LibraryEntry> {
        val songs = smpLibraryScanner.listSongs()
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=build_smp_entries songsCount=${songs.size} songs=${summarizeSmpSongs(songs)}"
        )
        val entries = songs
            .map { song ->
                val uriString = buildSmpItem(song.id)
                val displayName = resolveSongDisplayTitle(
                    songId = song.id,
                    fallbackTitle = song.title.ifBlank { song.id }
                )
                LibraryEntry(
                    uri = Uri.parse(uriString),
                    name = displayName,
                    isDirectory = false
                )
            }
            .sortedBy { it.name.lowercase() }
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=build_smp_entries_done entriesCount=${entries.size} entries=${summarizeLibraryEntries(entries)}"
        )
        return entries
    }

    suspend fun buildSmpEntriesAsync(): List<LibraryEntry> = withContext(Dispatchers.IO) {
        val callId = buildSmpEntriesTraceCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        Log.i(
            SMP_VIEW_TRACE_TAG,
            "elapsedMs=$startMs step=build_smp_entries_async_start call=$callId"
        )
        val result = buildSmpEntries()
        Log.i(
            SMP_VIEW_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=build_smp_entries_async_end call=$callId durationMs=${SystemClock.elapsedRealtime() - startMs} count=${result.size} entries=${summarizeLibraryEntries(result)}"
        )
        result
    }

    suspend fun refreshSmpEntriesAsync(): List<LibraryEntry> {
        LibraryFolderCache.remove(SMP_FOLDER_URI)
        val smpEntries = buildSmpEntriesAsync()
        LibraryFolderCache.put(SMP_FOLDER_URI, smpEntries)
        return smpEntries
    }

    fun mergeSmpEntriesWithImportedSignal(
        smpEntries: List<LibraryEntry>,
        importedUriString: String,
        importedTitle: String
    ): List<LibraryEntry> {
        val existingEntry = smpEntries.firstOrNull { it.uri.toString() == importedUriString }
        val mergedEntry = existingEntry ?: LibraryEntry(
            uri = Uri.parse(importedUriString),
            name = importedTitle,
            isDirectory = false
        )
        return (smpEntries.filterNot { it.uri.toString() == importedUriString } + mergedEntry)
            .sortedBy { it.name.lowercase() }
    }

    fun showSmpEntriesImmediately(smpEntries: List<LibraryEntry>) {
        LibraryFolderCache.put(SMP_FOLDER_URI, smpEntries)
        currentFolderUri
            ?.takeUnless { isSmpFolderUri(it) }
            ?.let { currentFolder ->
                if (folderStack.lastOrNull()?.toString() != currentFolder.toString()) {
                    folderStack = folderStack + currentFolder
                }
            }
        currentFolderUri = SMP_FOLDER_URI
        if (entries != smpEntries) {
            entries = smpEntries
        }
        selectedSongs = emptySet()
        Log.i(
            SMP_VIEW_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=show_smp_entries_immediately currentFolderUri=$currentFolderUri entriesSize=${smpEntries.size} entries=${summarizeLibraryEntries(smpEntries)}"
        )
    }

    fun decorateEntriesForFolder(folderUri: Uri, source: List<LibraryEntry>): List<LibraryEntry> {
        if (isPrompterFolderUri(folderUri)) {
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=decorate_entries folder=$folderUri mode=virtual_prompter sourceCount=${source.size}"
            )
            return buildPrompterEntries()
        }
        if (isSmpFolderUri(folderUri)) {
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=decorate_entries folder=$folderUri mode=virtual_smp sourceCount=${source.size}"
            )
            return buildSmpEntries()
        }

        val isFilesViewMode = libraryViewMode == LIBRARY_VIEW_MODE_FILES
        val visibleSource = if (isFilesViewMode) {
            source
        } else {
            source.filterNot { entry ->
                entry.isDirectory &&
                    !isPrompterFolderUri(entry.uri) &&
                    !isSmpFolderUri(entry.uri) &&
                    shouldHideFromMainLibrary(entry.name)
            }
        }

        val root = backend.getRootUri()
        val isRootFolder = root != null && folderUri.toString() == root.toString()
        if (!isRootFolder || isFilesViewMode) {
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=decorate_entries folder=$folderUri root=$root isRoot=$isRootFolder filesView=$isFilesViewMode sourceCount=${source.size} visibleCount=${visibleSource.size}"
            )
            return visibleSource
        }

        val extraEntries = mutableListOf<LibraryEntry>()
        val alreadyHasPrompter = visibleSource.any { it.isDirectory && isPrompterFolderUri(it.uri) }
        if (!alreadyHasPrompter) {
            extraEntries += LibraryEntry(PROMPTER_FOLDER_URI, sPrompterFolder, isDirectory = true)
        }

        val alreadyHasSmp = visibleSource.any { it.isDirectory && isSmpFolderUri(it.uri) }
        if (!alreadyHasSmp) {
            extraEntries += LibraryEntry(SMP_FOLDER_URI, sSmpFolder, isDirectory = true)
        }

        if (extraEntries.isEmpty()) {
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=decorate_root_entries folder=$folderUri root=$root sourceCount=${source.size} visibleCount=${visibleSource.size} alreadyHasPrompter=$alreadyHasPrompter alreadyHasSmp=$alreadyHasSmp extraCount=0 hasVirtualSmp=${visibleSource.any { isSmpFolderUri(it.uri) }} entries=${summarizeLibraryEntries(visibleSource)}"
            )
            return visibleSource
        }

        val decorated = (visibleSource + extraEntries)
            .sortedWith(
                compareByDescending<LibraryEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=decorate_root_entries folder=$folderUri root=$root sourceCount=${source.size} visibleCount=${visibleSource.size} alreadyHasPrompter=$alreadyHasPrompter alreadyHasSmp=$alreadyHasSmp extraCount=${extraEntries.size} hasVirtualSmp=${decorated.any { isSmpFolderUri(it.uri) }} entries=${summarizeLibraryEntries(decorated)}"
        )
        return decorated
    }

    fun buildEntriesForFolder(
        folderUri: Uri,
        useCache: Boolean = true,
        currentFolderForLog: Uri? = currentFolderUri,
        indexSnapshot: List<LibraryIndexCache.CachedEntry> = indexAll
    ): List<LibraryEntry> {
        val callId = buildEntriesPerfCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        val rootUri = backend.getRootUri()
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=build_entries_start call=$callId timeMs=$startMs folder=$folderUri useCache=$useCache currentFolderUri=$currentFolderForLog root=$rootUri"
        )
        if (useCache) {
            LibraryFolderCache.get(folderUri)?.let { cached ->
                val durationMs = SystemClock.elapsedRealtime() - startMs
                Log.i(
                    LIBRARY_PERF_TRACE_TAG,
                    "step=build_entries_done call=$callId durationMs=$durationMs folder=$folderUri cache=hit count=${cached.size}"
                )
                Log.i(
                    LIB_SMP_TRACE_TAG,
                    "step=build_entries cache=hit folder=$folderUri root=$rootUri count=${cached.size} hasVirtualSmp=${cached.any { isSmpFolderUri(it.uri) }} entries=${summarizeLibraryEntries(cached)}"
                )
                return cached
            }
        }
        val fresh = if (isPrompterFolderUri(folderUri) || isSmpFolderUri(folderUri)) {
            emptyList()
        } else {
            backend.listFolder(
                folderUri = folderUri,
                indexAll = indexSnapshot,
                djExcludedReason = sDjExcludedReason
            )
        }
        val decorated = decorateEntriesForFolder(folderUri, fresh)
        LibraryFolderCache.put(folderUri, decorated)
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=build_entries_done call=$callId durationMs=$durationMs folder=$folderUri cache=miss freshCount=${fresh.size} decoratedCount=${decorated.size}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=build_entries cache=miss folder=$folderUri root=$rootUri freshCount=${fresh.size} decoratedCount=${decorated.size} hasVirtualSmp=${decorated.any { isSmpFolderUri(it.uri) }} entries=${summarizeLibraryEntries(decorated)}"
        )
        return decorated
    }

    fun applyEntriesIfCurrent(folderUri: Uri, newEntries: List<LibraryEntry>) {
        if (currentFolderUri?.toString() != folderUri.toString()) return
        if (entries == newEntries) return
        entries = newEntries
    }

    fun showCachedEntries(folderUri: Uri): Boolean {
        val cached = LibraryFolderCache.get(folderUri) ?: return false
        applyEntriesIfCurrent(folderUri, cached)
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=folder_cache_hit_immediate timeMs=${SystemClock.elapsedRealtime()} folder=$folderUri count=${cached.size}"
        )
        return true
    }

    suspend fun loadFolderEntriesAsync(
        folderUri: Uri,
        forceRefresh: Boolean = false,
        clearVisibleOnMiss: Boolean = false
    ): List<LibraryEntry> {
        if (forceRefresh) {
            LibraryFolderCache.remove(folderUri)
        } else {
            LibraryFolderCache.get(folderUri)?.let { cached ->
                applyEntriesIfCurrent(folderUri, cached)
                return cached
            }
        }

        if (clearVisibleOnMiss) {
            applyEntriesIfCurrent(folderUri, emptyList())
        }

        val indexSnapshot = indexAll
        val currentFolderForLog = currentFolderUri
        val loaded = withContext(Dispatchers.IO) {
            buildEntriesForFolder(
                folderUri = folderUri,
                useCache = !forceRefresh,
                currentFolderForLog = currentFolderForLog,
                indexSnapshot = indexSnapshot
            )
        }
        applyEntriesIfCurrent(folderUri, loaded)
        return loaded
    }

    suspend fun runGlobalScan(root: Uri, folderToShow: Uri) {
        LibraryFolderCache.clear()
        backend.scanAll(
            root = root,
            folderToShow = folderToShow,
            onIndexAll = { indexAll = it },
            onEntries = {
                val decorated = decorateEntriesForFolder(folderToShow, it)
                applyEntriesIfCurrent(folderToShow, decorated)
                LibraryFolderCache.put(folderToShow, decorated)
            }
        )
    }

    suspend fun refreshLibraryFolder(folderUri: Uri?) {
        val root = backend.getRootUri() ?: return
        val folderToShow = folderUri ?: root
        currentFolderUri = folderToShow
        runGlobalScan(
            root = root,
            folderToShow = folderToShow
        )
    }

    LaunchedEffect(smpRefreshVersion) {
        val callId = refreshCurrentEffectPerfCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_refresh_current_launch call=$callId timeMs=$startMs smpRefreshVersion=$smpRefreshVersion currentFolderUri=$currentFolderUri"
        )
        if (!initialLoadDone) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_refresh_current_end call=$callId durationMs=$durationMs result=skip_initial_load_not_done"
            )
            return@LaunchedEffect
        }
        val currentFolder = currentFolderUri ?: run {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_refresh_current_end call=$callId durationMs=$durationMs result=skip_no_current_folder"
            )
            return@LaunchedEffect
        }
        val rootFolder = backend.getRootUri()
        val shouldRefreshCurrentFolder =
            isSmpFolderUri(currentFolder) ||
                (rootFolder != null && currentFolder.toString() == rootFolder.toString())
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_refresh_current_start smpRefreshVersion=$smpRefreshVersion currentFolderUri=$currentFolder root=$rootFolder shouldRefresh=$shouldRefreshCurrentFolder entriesSize=${entries.size}"
        )
        val pendingImportSignal = lastImportedSmpSignal
        val importedAutoOpenPending = pendingImportSignal?.let { signal ->
            signal.requestVersion == smpRefreshVersion &&
                signal.requestVersion >= 0 &&
                signal.songId.isNotBlank()
        } == true
        if (importedAutoOpenPending) {
            Log.i(
                SMP_VIEW_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=refresh_current_paused_for_import smpRefreshVersion=$smpRefreshVersion requestVersion=${lastImportedSmpSignal?.requestVersion} songId=${lastImportedSmpSignal?.songId} currentFolderUri=$currentFolder"
            )
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_refresh_current_end call=$callId durationMs=$durationMs result=skip_pending_imported_auto_open"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_refresh_current_skip reason=pending_imported_auto_open smpRefreshVersion=$smpRefreshVersion requestVersion=${lastImportedSmpSignal?.requestVersion} currentFolderUri=$currentFolder"
            )
            return@LaunchedEffect
        }
        if (!shouldRefreshCurrentFolder) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_refresh_current_end call=$callId durationMs=$durationMs result=skip currentFolderUri=$currentFolder root=$rootFolder"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_refresh_current_skip smpRefreshVersion=$smpRefreshVersion currentFolderUri=$currentFolder root=$rootFolder"
            )
            return@LaunchedEffect
        }
        loadFolderEntriesAsync(
            folderUri = currentFolder,
            forceRefresh = true
        )
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_refresh_current_end call=$callId durationMs=$durationMs result=done currentFolderUri=$currentFolder root=$rootFolder entriesSize=${entries.size}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_refresh_current_done smpRefreshVersion=$smpRefreshVersion currentFolderUri=$currentFolder root=$rootFolder entriesSize=${entries.size} entries=${summarizeLibraryEntries(entries)}"
        )
    }

    LaunchedEffect(currentFolderUri, storageMode) {
        val callId = folderChangeEffectPerfCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_folder_change_launch call=$callId timeMs=$startMs storageMode=$storageMode currentFolderUri=$currentFolderUri"
        )
        if (!initialLoadDone) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_folder_change_end call=$callId durationMs=$durationMs result=skip_initial_load_not_done storageMode=$storageMode"
            )
            return@LaunchedEffect
        }
        val root = backend.getRootUri() ?: run {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_folder_change_end call=$callId durationMs=$durationMs result=skip_no_root storageMode=$storageMode"
            )
            return@LaunchedEffect
        }
        val currentFolder = currentFolderUri ?: root
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_folder_change_start storageMode=$storageMode currentFolderUri=$currentFolderUri root=$root entriesSize=${entries.size}"
        )

        val shouldRedirectToRoot =
            libraryViewMode != LIBRARY_VIEW_MODE_FILES &&
                !isPrompterFolderUri(currentFolder) &&
                !isSmpFolderUri(currentFolder) &&
                resolveFolderName(context, currentFolder)
                    ?.let(::shouldHideFromMainLibrary) == true

        val folderToShow = if (shouldRedirectToRoot) root else currentFolder
        if (currentFolderUri?.toString() != folderToShow.toString()) {
            currentFolderUri = folderToShow
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_folder_change_end call=$callId durationMs=$durationMs storageMode=$storageMode result=redirect root=$root folderToShow=$folderToShow"
            )
            return@LaunchedEffect
        }
        if (!showCachedEntries(folderToShow)) {
            loadFolderEntriesAsync(
                folderUri = folderToShow,
                clearVisibleOnMiss = true
            )
        }
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_folder_change_end call=$callId durationMs=$durationMs storageMode=$storageMode root=$root folderToShow=$folderToShow entriesSize=${entries.size}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_folder_change_done storageMode=$storageMode currentFolderUri=$currentFolderUri root=$root folderToShow=$folderToShow entriesSize=${entries.size} entries=${summarizeLibraryEntries(entries)}"
        )
    }

    fun removePrompterFromAllPlaylists(uriString: String) {
        PlaylistRepository.getPlaylists().forEach { playlist ->
            PlaylistRepository.removeSongFromPlaylist(playlist, uriString)
        }
    }

    // dialogs state
    var showAssignDialog by remember { mutableStateOf(false) }
    var pendingAssignRequest by remember {
        mutableStateOf<PendingPlaylistAssignRequest?>(null)
    }
    var pendingBackupImportUri by remember { mutableStateOf<Uri?>(null) }
    var backupImportInProgress by remember { mutableStateOf(false) }

    // ✅ delete planifiée (audio + associés potentiels)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeletePlan by remember { mutableStateOf<LibraryDeletePlan?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var pendingDeleteSelection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingDeleteSmpUri by remember { mutableStateOf<Uri?>(null) }
    var deleteSmpInProgress by remember { mutableStateOf(false) }

    var pendingMoveSelection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showMoveBrowser by remember { mutableStateOf(false) }
    var moveBrowserFolder by remember { mutableStateOf<Uri?>(null) }
    var moveBrowserStack by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCopySelection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showCopyBrowser by remember { mutableStateOf(false) }
    var copyBrowserFolder by remember { mutableStateOf<Uri?>(null) }
    var copyBrowserStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editPrompterId by remember { mutableStateOf<String?>(null) }
    var editPrompterTitle by remember { mutableStateOf("") }
    var editPrompterContent by remember { mutableStateOf("") }
    var showEditPrompterDialog by remember { mutableStateOf(false) }

    fun openMoveBrowserForSelection(selection: Collection<Uri>) {
        val normalizedSelection = normalizeSelection(selection)
        val root = backend.getRootUri()
        if (root == null || normalizedSelection.isEmpty()) {
            showMoveBrowser = false
            pendingMoveSelection = emptyList()
            return
        }
        pendingMoveSelection = normalizedSelection
        moveBrowserFolder = root
        moveBrowserStack = emptyList()
        showMoveBrowser = true
    }

    fun openCopyBrowserForSelection(selection: Collection<Uri>) {
        val normalizedSelection = normalizeSelection(selection)
        val root = backend.getRootUri()
        if (root == null || normalizedSelection.isEmpty()) {
            showCopyBrowser = false
            pendingCopySelection = emptyList()
            return
        }
        pendingCopySelection = normalizedSelection
        copyBrowserFolder = root
        copyBrowserStack = emptyList()
        showCopyBrowser = true
    }

    fun toggleSelection(uri: Uri) {
        selectedSongs = if (selectedSongs.contains(uri)) selectedSongs - uri else selectedSongs + uri
    }

    fun beginAliasRename(entry: LibraryEntry, initialTitle: String? = null) {
        renameTarget = entry
        renameText = initialTitle
            ?: TitleAliasesStore.getTitleForTrack(context, entry.uri.toString())
            ?: PlaylistRepository.getAnyCustomTitleForUri(entry.uri.toString())
            ?: entry.name
    }

    // search
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    val isSongViewMode = libraryViewMode == LIBRARY_VIEW_MODE_SONGS
    val titleAliasVersion = TitleAliasesStore.version.intValue
    data class SearchableLibraryEntry(
        val entry: LibraryEntry,
        val indexedItem: SearchEngine.IndexedItem
    )
    data class SearchableLibrarySong(
        val item: LibrarySongItem,
        val indexedItem: SearchEngine.IndexedItem
    )
    val searchableEntries = remember(entries, titleAliasVersion, context) {
        entries
            .asSequence()
            .map { entry ->
                val uriString = entry.uri.toString()
                val displayTitle = if (!entry.isDirectory) {
                    TitleAliasesStore.getTitleForTrack(context, uriString)
                        ?: PlaylistRepository.getAnyCustomTitleForUri(uriString)
                        ?: entry.name
                } else {
                    entry.name
                }
                SearchableLibraryEntry(
                    entry = entry,
                    indexedItem = SearchEngine.index(
                        id = uriString,
                        displayTitle = displayTitle,
                        fallbackName = entry.name
                    )
                )
            }
            .toList()
    }
    val searchableSongItems = remember(songItems) {
        songItems.map { item ->
            SearchableLibrarySong(
                item = item,
                indexedItem = SearchEngine.index(
                    id = item.songId,
                    displayTitle = item.displayTitle,
                    fallbackName = item.fallbackTitle
                )
            )
        }
    }
    val filteredEntries = remember(searchQuery, searchableEntries) {
        val normalizedQuery = SearchEngine.normalize(searchQuery)
        if (normalizedQuery.isBlank()) {
            searchableEntries.map { it.entry }
        } else {
            val filteredIds = SearchEngine.filter(
                items = searchableEntries.map { it.indexedItem },
                query = searchQuery
            ).asSequence().map { it.id }.toSet()
            searchableEntries
                .filter { it.indexedItem.id in filteredIds }
                .map { it.entry }
        }
    }
    val filteredSongItems = remember(searchQuery, searchableSongItems) {
        val normalizedQuery = SearchEngine.normalize(searchQuery)
        if (normalizedQuery.isBlank()) {
            searchableSongItems.map { it.item }
        } else {
            val filteredIds = SearchEngine.filter(
                items = searchableSongItems.map { it.indexedItem },
                query = searchQuery
            ).asSequence().map { it.id }.toSet()
            searchableSongItems
                .filter { it.indexedItem.id in filteredIds }
                .map { it.item }
        }
    }
    suspend fun injectSmpEntriesAndCheckVisible(
        smpEntries: List<LibraryEntry>,
        importedUriString: String,
        importedSongId: String,
        phase: String
    ): Boolean {
        showSmpEntriesImmediately(smpEntries)
        withFrameNanos { }
        val visibleInEntries = entries.any { it.uri.toString() == importedUriString }
        val visibleInFiltered = filteredEntries.any { it.uri.toString() == importedUriString }
        val onSmpFolder = isSmpFolderUri(currentFolderUri)
        val visible = onSmpFolder && visibleInEntries && visibleInFiltered
        Log.i(
            SMP_VIEW_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=inject_smp_entries_check_visible phase=$phase importedSongId=$importedSongId onSmpFolder=$onSmpFolder visibleInEntries=$visibleInEntries visibleInFiltered=$visibleInFiltered entriesSize=${entries.size} filteredSize=${filteredEntries.size}"
        )
        return visible
    }
    val entriesSummary = remember(entries) { summarizeLibraryEntries(entries) }
    val filteredEntriesSummary = remember(filteredEntries) { summarizeLibraryEntries(filteredEntries) }
    LaunchedEffect(currentFolderUri, searchQuery, entriesSummary, filteredEntriesSummary) {
        val callId = uiEntriesSnapshotPerfCounter.incrementAndGet()
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=ui_entries_snapshot call=$callId timeMs=${SystemClock.elapsedRealtime()} currentFolderUri=$currentFolderUri entriesCount=${entries.size} filteredCount=${filteredEntries.size}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=ui_entries_snapshot currentFolderUri=$currentFolderUri root=${backend.getRootUri()} searchQuery=$searchQuery entriesCount=${entries.size} filteredCount=${filteredEntries.size} hasVirtualSmpInEntries=${entries.any { isSmpFolderUri(it.uri) }} hasVirtualSmpInFiltered=${filteredEntries.any { isSmpFolderUri(it.uri) }} entries=$entriesSummary filtered=$filteredEntriesSummary"
        )
    }

    val canImportBackupJsonFromCurrentFolder = remember(context, currentFolderUri) {
        currentFolderUri
            ?.let { resolveFolderName(context, it) }
            ?.let(::isBackupFolderName) == true
    }
    val activeSearchableCount = if (isSongViewMode) searchableSongItems.size else searchableEntries.size
    val activeFilteredCount = if (isSongViewMode) filteredSongItems.size else filteredEntries.size
    LaunchedEffect(searchQuery, activeSearchableCount, activeFilteredCount, currentFolderUri, isSongViewMode) {
        if (BuildConfig.DEBUG) {
            val normalizedQuery = SearchEngine.normalize(searchQuery)
            val itemsBefore = activeSearchableCount
            val itemsAfter = activeFilteredCount
            Log.d(
                "SEARCH_PROOF",
                "mode=LIBRARY query='$normalizedQuery' playlist=- viewMode=$libraryViewMode itemsBefore=$itemsBefore itemsAfter=$itemsAfter"
            )
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(searchToggleSignal) {
        if (searchToggleSignal == 0) return@LaunchedEffect
        if (isSearchVisible) {
            searchQuery = ""
            isSearchVisible = false
            focusManager.clearFocus(force = true)
        } else {
            isSearchVisible = true
        }
    }

    LaunchedEffect(reselectRootSignal) {
        if (reselectRootSignal == 0) return@LaunchedEffect
        val root = backend.getRootUri() ?: return@LaunchedEffect
        val currentFolder = currentFolderUri ?: root
        if (currentFolder.toString() == root.toString()) return@LaunchedEffect

        currentFolderUri = root
        folderStack = emptyList()
        searchQuery = ""
        selectedSongs = emptySet()
        if (!showCachedEntries(root)) {
            scope.launch {
                loadFolderEntriesAsync(
                    folderUri = root,
                    clearVisibleOnMiss = true
                )
            }
        }
    }

    LaunchedEffect(initialLoadDone, smpRefreshVersion, titleAliasVersion) {
        if (!initialLoadDone) return@LaunchedEffect
        songItems = buildLibrarySongItemsAsync()
    }

    LaunchedEffect(
        initialLoadDone,
        smpRefreshVersion,
        lastImportedSmpSignal,
        importedAutoOpenDeferredRetryToken
    ) {
        val callId = importedSmpEffectPerfCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        val importedSignal = lastImportedSmpSignal
        val importedRequestVersion = importedSignal?.requestVersion ?: -1
        fun consumeImportedSmpAutoOpen(reason: String) {
            importedAutoOpenDeferredRetryRequestVersion = -1
            Log.i(
                SMP_VIEW_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=consume_imported_auto_open reason=$reason smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion songId=${importedSignal?.songId} currentFolderUri=$currentFolderUri entriesSize=${entries.size}"
            )
            onConsumeImportedSmpAutoOpen()
        }
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_imported_smp_launch call=$callId timeMs=$startMs initialLoadDone=$initialLoadDone smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion songId=${importedSignal?.songId}"
        )
        Log.i(
            SMP_VIEW_TRACE_TAG,
            "elapsedMs=$startMs step=effect_imported_smp_launch call=$callId initialLoadDone=$initialLoadDone smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion songId=${importedSignal?.songId} currentFolderUri=$currentFolderUri"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_imported_smp_start initialLoadDone=$initialLoadDone smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion lastImportedSmpSongId=${importedSignal?.songId} currentFolderUri=$currentFolderUri entriesSize=${entries.size}"
        )
        if (!initialLoadDone) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_initial_load_not_done"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=initial_load_not_done smpRefreshVersion=$smpRefreshVersion"
            )
            return@LaunchedEffect
        }
        val importedSongId = importedSignal?.songId?.trim().takeUnless { it.isNullOrEmpty() } ?: run {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_no_song_id"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=no_imported_song_id smpRefreshVersion=$smpRefreshVersion"
            )
            return@LaunchedEffect
        }
        if (importedRequestVersion < 0) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_no_request_version"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=no_request_version smpRefreshVersion=$smpRefreshVersion"
            )
            return@LaunchedEffect
        }
        if (importedRequestVersion != smpRefreshVersion) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_request_version_mismatch"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=request_version_mismatch smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion"
            )
            consumeImportedSmpAutoOpen("request_version_mismatch")
            return@LaunchedEffect
        }
        if (importedRequestVersion == lastHandledImportedSmpRefresh) {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_already_handled"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=already_handled smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion lastHandled=$lastHandledImportedSmpRefresh"
            )
            consumeImportedSmpAutoOpen("already_handled")
            return@LaunchedEffect
        }
        if (importedAutoOpenLaunchProofVersion != importedRequestVersion) {
            importedAutoOpenLaunchProofVersion = importedRequestVersion
            emitImportProof(
                phase = "smp_auto_open_launch",
                detail = "songId=$importedSongId requestVersion=$importedRequestVersion currentFolderUri=$currentFolderUri",
                toastMessage = "IMPORT_PROOF SMP auto-open path"
            )
        }

        val importedUriString = buildSmpItem(importedSongId)
        val importedTitle = importedSignal?.title?.trim().takeUnless { it.isNullOrEmpty() } ?: importedSongId
        searchQuery = ""
        var smpEntries = mergeSmpEntriesWithImportedSignal(
            smpEntries = LibraryFolderCache.get(SMP_FOLDER_URI)
                ?: if (isSmpFolderUri(currentFolderUri)) entries else emptyList(),
            importedUriString = importedUriString,
            importedTitle = importedTitle
        )
        var isImportedSongVisible = injectSmpEntriesAndCheckVisible(
            smpEntries = smpEntries,
            importedUriString = importedUriString,
            importedSongId = importedSongId,
            phase = "provisional"
        )
        if (isImportedSongVisible && importedAutoOpenReconcileVersion != importedRequestVersion) {
            importedAutoOpenReconcileVersion = importedRequestVersion
            scope.launch {
                repeat(10) { attempt ->
                    delay(if (attempt == 0) 0L else 350L)
                    val refreshed = refreshSmpEntriesAsync()
                    val merged = mergeSmpEntriesWithImportedSignal(
                        smpEntries = refreshed,
                        importedUriString = importedUriString,
                        importedTitle = importedTitle
                    )
                    showSmpEntriesImmediately(merged)
                    val resolved = refreshed.any { it.uri.toString() == importedUriString }
                    Log.i(
                        SMP_VIEW_TRACE_TAG,
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_reconcile attempt=$attempt importedSongId=$importedSongId resolved=$resolved refreshedCount=${refreshed.size}"
                    )
                    if (resolved) return@launch
                }
            }
        }
        if (!isImportedSongVisible) {
            smpEntries = buildSmpEntriesAsync()
            smpEntries = mergeSmpEntriesWithImportedSignal(
                smpEntries = smpEntries,
                importedUriString = importedUriString,
                importedTitle = importedTitle
            )
            isImportedSongVisible = injectSmpEntriesAndCheckVisible(
                smpEntries = smpEntries,
                importedUriString = importedUriString,
                importedSongId = importedSongId,
                phase = "initial"
            )
        }
        if (!isImportedSongVisible) {
            for (attempt in 1..10) {
                if (isImportedSongVisible) break
                Log.i(
                    SMP_VIEW_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_retry_start attempt=$attempt importedSongId=$importedSongId"
                )
                delay(180L)
                smpEntries = refreshSmpEntriesAsync()
                smpEntries = mergeSmpEntriesWithImportedSignal(
                    smpEntries = smpEntries,
                    importedUriString = importedUriString,
                    importedTitle = importedTitle
                )
                isImportedSongVisible = injectSmpEntriesAndCheckVisible(
                    smpEntries = smpEntries,
                    importedUriString = importedUriString,
                    importedSongId = importedSongId,
                    phase = "retry_$attempt"
                )
                Log.i(
                    SMP_VIEW_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_retry_done attempt=$attempt importedSongId=$importedSongId visible=$isImportedSongVisible smpEntriesCount=${smpEntries.size}"
                )
                if (isImportedSongVisible) {
                    Log.i(
                        LIB_SMP_TRACE_TAG,
                        "step=effect_imported_smp_retry_visible importedSongId=$importedSongId attempt=$attempt smpEntriesCount=${smpEntries.size}"
                    )
                }
            }
        }
        if (!isImportedSongVisible) {
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_final_retry_start importedSongId=$importedSongId smpEntriesCount=${smpEntries.size}"
            )
            Log.i(
                SMP_VIEW_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_final_retry_start importedSongId=$importedSongId smpEntriesCount=${smpEntries.size}"
            )
            delay(1200L)
            smpEntries = refreshSmpEntriesAsync()
            smpEntries = mergeSmpEntriesWithImportedSignal(
                smpEntries = smpEntries,
                importedUriString = importedUriString,
                importedTitle = importedTitle
            )
            isImportedSongVisible = injectSmpEntriesAndCheckVisible(
                smpEntries = smpEntries,
                importedUriString = importedUriString,
                importedSongId = importedSongId,
                phase = "final_retry"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_final_retry_done importedSongId=$importedSongId visible=$isImportedSongVisible smpEntriesCount=${smpEntries.size}"
            )
            Log.i(
                SMP_VIEW_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_final_retry_done importedSongId=$importedSongId visible=$isImportedSongVisible smpEntriesCount=${smpEntries.size}"
            )
        }
        if (!isImportedSongVisible) {
            if (importedAutoOpenDeferredRetryRequestVersion != importedRequestVersion) {
                importedAutoOpenDeferredRetryRequestVersion = importedRequestVersion
                if (importedAutoOpenDeferredProofVersion != importedRequestVersion) {
                    importedAutoOpenDeferredProofVersion = importedRequestVersion
                    emitImportProof(
                        phase = "smp_auto_open_deferred_retry",
                        detail = "songId=$importedSongId requestVersion=$importedRequestVersion entriesCount=${smpEntries.size}",
                        toastMessage = "IMPORT_PROOF SMP auto-open retry path"
                    )
                }
                Log.i(
                    SMP_VIEW_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_deferred_retry_schedule importedSongId=$importedSongId requestVersion=$importedRequestVersion smpEntriesCount=${smpEntries.size}"
                )
                delay(1800L)
                importedAutoOpenDeferredRetryToken++
                Log.i(
                    SMP_VIEW_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_deferred_retry_relaunch importedSongId=$importedSongId requestVersion=$importedRequestVersion retryToken=$importedAutoOpenDeferredRetryToken"
                )
                return@LaunchedEffect
            }

            consumeImportedSmpAutoOpen("imported_song_not_visible_after_deferred_retry")
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=skip_imported_song_not_visible songId=$importedSongId smpEntriesCount=${smpEntries.size}"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_imported_smp_skip reason=imported_song_not_visible importedSongId=$importedSongId smpEntriesCount=${smpEntries.size}"
            )
            return@LaunchedEffect
        }

        val successVisible = injectSmpEntriesAndCheckVisible(
            smpEntries = smpEntries,
            importedUriString = importedUriString,
            importedSongId = importedSongId,
            phase = "success_gate"
        )
        if (!successVisible) {
            Log.i(
                SMP_VIEW_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=effect_imported_smp_success_gate_rejected importedSongId=$importedSongId requestVersion=$importedRequestVersion"
            )
            lastHandledImportedSmpRefresh = -1
            if (importedAutoOpenDeferredRetryRequestVersion != importedRequestVersion) {
                importedAutoOpenDeferredRetryRequestVersion = importedRequestVersion
                delay(1800L)
                importedAutoOpenDeferredRetryToken++
                return@LaunchedEffect
            }
            consumeImportedSmpAutoOpen("success_gate_rejected")
            return@LaunchedEffect
        }
        lastHandledImportedSmpRefresh = importedRequestVersion
        if (importedAutoOpenSuccessProofVersion != importedRequestVersion) {
            importedAutoOpenSuccessProofVersion = importedRequestVersion
            emitImportProof(
                phase = "smp_auto_open_success",
                detail = "songId=$importedSongId requestVersion=$importedRequestVersion entriesCount=${smpEntries.size}",
                toastMessage = "IMPORT_PROOF SMP auto-open success"
            )
        }
        consumeImportedSmpAutoOpen("success")
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_imported_smp_end call=$callId durationMs=$durationMs result=done currentFolderUri=$currentFolderUri entriesSize=${entries.size}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_imported_smp_done initialLoadDone=$initialLoadDone smpRefreshVersion=$smpRefreshVersion requestVersion=$importedRequestVersion currentFolderUri=$currentFolderUri entriesSize=${entries.size} entries=${summarizeLibraryEntries(entries)}"
        )
    }

    val bottomBarHeight = 56.dp

    // ✅ QUICK PLAY (sans ouvrir le lecteur)
    val quickPlayer = remember { ExoPlayer.Builder(context).build() }
    var quickNowUri by remember { mutableStateOf<Uri?>(null) }
    var quickIsPlaying by remember { mutableStateOf(false) }

    DisposableEffect(quickPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                quickIsPlaying = isPlaying
                if (isPlaying) {
                    val session = runCatching { quickPlayer.audioSessionId }.getOrDefault(0)
                    Log.d(
                        "METER",
                        "PLAY_START engine=Other.LibraryQuickPlayer sessionId=$session isPlaying=$isPlaying"
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    quickIsPlaying = false
                }
            }
        }
        quickPlayer.addListener(listener)

        onDispose {
            quickPlayer.removeListener(listener)
            quickPlayer.release()
        }
    }

    fun quickPlayToggle(uri: Uri) {
        try {
            if (quickNowUri == null || quickNowUri != uri) {
                quickNowUri = uri
                quickPlayer.setMediaItem(MediaItem.fromUri(uri))
                quickPlayer.prepare()
                quickPlayer.playWhenReady = true
                return
            }
            if (quickPlayer.isPlaying) quickPlayer.pause() else quickPlayer.play()
        } catch (e: Exception) {
            Log.e("LibraryQuickPlay", "Erreur quick play", e)
        }
    }

    fun readTextFromUri(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    fun writeTextToUri(uri: Uri, text: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(text) }
            true
        }.getOrElse { false }
    }

    fun stopQuickPlay() {
        try {
            if (quickPlayer.isPlaying) quickPlayer.pause()
        } catch (_: Exception) {
        }
        quickIsPlaying = false
    }

    fun startLoading(label: String, determinate: Boolean) {
        loadingStartedAt = System.currentTimeMillis()
        isLoading = true
        moveLabel = label
        moveProgress = if (determinate) 0f else null
    }

    suspend fun stopLoadingNice() {
        val elapsed = System.currentTimeMillis() - loadingStartedAt
        val minMs = 500L
        if (elapsed < minMs) delay(minMs - elapsed)
        isLoading = false
        moveProgress = null
        moveLabel = null
    }

    fun shareSmpSong(uri: Uri) {
        val songId = getSmpSongId(uri.toString())
        if (songId == null) {
            Toast.makeText(context, sShareSmpFailed, Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            startLoading(sLoading, determinate = false)
            try {
                val shareUri = withContext(Dispatchers.IO) {
                    val song = smpLibraryScanner.findSongById(songId) ?: return@withContext null
                    val exportedFile = SmpExporter.exportSongUnitToCacheSmp(context, song)
                        ?: return@withContext null
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportedFile
                    )
                }

                if (shareUri == null) {
                    Toast.makeText(context, sShareSmpFailed, Toast.LENGTH_SHORT).show()
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.backup_share)
                        )
                    )
                }
            } finally {
                stopLoadingNice()
            }
        }
    }

    fun runLibraryPlaylistAssignment(request: PendingPlaylistAssignRequest) {
        scope.launch {
            val hasBatchWork = request.batchPlan != null
            if (hasBatchWork) {
                startLoading(sBatchPreparing, determinate = true)
                moveProgress = 0f
                currentBatchProgress = null
            }

            try {
                PlaylistRepository.createIfNotExists(request.playlistName)

                var successCount = 0
                var failureCount = 0

                    request.directItemUris.forEach { itemUriString ->
                        runCatching {
                            PlaylistRepository.assignSongToPlaylist(
                                playlistName = request.playlistName,
                                songUri = itemUriString,
                                songId = getSmpSongId(itemUriString)
                            )
                        }.onSuccess {
                            successCount += 1
                            Log.i(
                            "PLAYLIST_ASSIGN_LIB",
                            "step=direct_assign playlist=${request.playlistName} item=$itemUriString"
                        )
                    }.onFailure { error ->
                        failureCount += 1
                        Log.e(
                            "PLAYLIST_ASSIGN_LIB",
                            "step=direct_assign_failed playlist=${request.playlistName} item=$itemUriString",
                            error
                        )
                    }
                }

                request.batchPlan?.let { plan ->
                    val result = withContext(Dispatchers.IO) {
                        smpBatchProcessor.process(
                            plan = plan,
                            playlistName = request.playlistName,
                            importSmp = onImportGeneratedSmp,
                            importFailureReasonProvider = onImportGeneratedSmpFailureReason,
                            addImportedSongToPlaylist = { targetPlaylist, importedSong ->
                                withContext(Dispatchers.Main) {
                                    runCatching {
                                        val smpMarker = buildSmpItem(importedSong.id)
                                        PlaylistRepository.createIfNotExists(targetPlaylist)
                                        PlaylistRepository.assignSongToPlaylist(
                                            playlistName = targetPlaylist,
                                            songUri = smpMarker,
                                            songId = importedSong.id
                                        )
                                        PlaylistRepository.renameSongInPlaylist(
                                            playlistName = targetPlaylist,
                                            uri = smpMarker,
                                            newTitle = importedSong.title
                                        )
                                    }
                                }
                            },
                            onProgress = { progress ->
                                mainHandler.post {
                                    currentBatchProgress = progress
                                    moveLabel = batchProgressLabel(progress)
                                }
                            }
                        )
                    }
                    currentBatchProgress = null
                    Log.i(
                        IMPORT_TRACE_TAG,
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=library_playlist_batch_force_100 successCount=${result.successCount} failureCount=${result.failureCount} playlist=${request.playlistName}"
                    )
                    moveProgress = 1f
                    successCount += result.successCount
                    failureCount += result.failureCount
                }

                if (successCount == 0 && failureCount > 0 && request.directItemUris.isEmpty()) {
                    Toast.makeText(context, sBatchUnsupportedOnly, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.smp_batch_playlist_summary,
                            successCount,
                            failureCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingAssignRequest = null
                if (hasBatchWork) {
                    currentBatchProgress = null
                    stopLoadingNice()
                }
            }
        }
    }

    fun prepareLibraryPlaylistAssignment(playlistName: String, selection: Set<Uri>) {
        scope.launch {
            val directItemUris = mutableListOf<String>()
            val batchUris = mutableListOf<Uri>()

            selection.forEach { uri ->
                val uriString = uri.toString()
                when {
                    uriString.startsWith("prompter://") -> directItemUris += uriString
                    getSmpSongId(uriString) != null -> directItemUris += uriString
                    else -> batchUris += uri
                }
            }

            val batchPlan = if (batchUris.isNotEmpty()) {
                withContext(Dispatchers.IO) { smpBatchProcessor.buildPlan(batchUris) }
            } else {
                null
            }

            val request = PendingPlaylistAssignRequest(
                playlistName = playlistName,
                directItemUris = directItemUris,
                batchPlan = batchPlan
            )

            Log.i(
                "PLAYLIST_ASSIGN_LIB",
                "step=prepare playlist=$playlistName directCount=${directItemUris.size} batchCount=${batchPlan?.totalCount ?: 0} hasAudioToPrepare=${batchPlan?.hasAudioToPrepare == true}"
            )

            if (batchPlan?.hasAudioToPrepare == true && SmpPreparationNoticePrefs.shouldShow(context)) {
                pendingAssignRequest = request
            } else {
                runLibraryPlaylistAssignment(request)
            }
        }
    }

    fun summarizeDeleteRoles(items: List<LibraryDeleteItem>): String {
        val hasLyrics = items.any { it.role == LibraryDeleteRole.LYRICS }
        val hasAccords = items.any { it.role == LibraryDeleteRole.ACCORDS }
        return when {
            hasLyrics && hasAccords -> "Lyrics + Accords"
            hasLyrics -> "Lyrics"
            hasAccords -> "Accords"
            else -> ".lrc"
        }
    }

    fun applyDeleteResult(result: LibraryDeleteResult) {
        result.results
            .filter { it.success }
            .forEach { itemResult ->
                selectedSongs = selectedSongs - itemResult.item.uri
            }

        if (!result.hasFailures) return

        val failed = result.results.count { !it.success }
        val total = result.results.size
        Toast.makeText(
            context,
            context.getString(R.string.library_delete_partial_failure, failed, total),
            Toast.LENGTH_LONG
        ).show()
    }

    fun fallbackDeletePlan(uri: Uri): LibraryDeletePlan {
        return LibraryDeletePlan(
            target = LibraryDeleteItem(
                uri = uri,
                role = LibraryDeleteRole.FILE,
                displayName = uri.lastPathSegment ?: "file"
            ),
            associated = emptyList()
        )
    }

    suspend fun planDeleteForUri(uri: Uri): LibraryDeletePlan {
        return runCatching {
            backend.planDelete(
                target = uri,
                indexAll = indexAll
            )
        }.getOrElse {
            fallbackDeletePlan(uri)
        }
    }

    fun isDirectoryUri(uri: Uri): Boolean {
        indexAll.firstOrNull { it.uriString == uri.toString() }?.let { return it.isDirectory }
        return runCatching {
            when (uri.scheme) {
                "file" -> File(uri.path ?: "").isDirectory
                else -> {
                    val doc = DocumentFile.fromSingleUri(context, uri)
                        ?: DocumentFile.fromTreeUri(context, uri)
                    doc?.isDirectory == true
                }
            }
        }.getOrDefault(false)
    }

    suspend fun runSelectionTransfer(
        label: String,
        sources: List<Uri>,
        dest: Uri,
        transfer: suspend (Uri, Uri, (Float?, String?) -> Unit) -> MoveResult
    ): Int {
        val normalizedSources = normalizeSelection(sources)
        if (normalizedSources.isEmpty()) return 0

        val total = normalizedSources.size.coerceAtLeast(1)
        var successCount = 0

        normalizedSources.forEachIndexed { index, srcUri ->
            val baseProgress = index.toFloat() / total.toFloat()
            val progressSpan = 1f / total.toFloat()
            moveLabel = "$label ${index + 1}/$total"
            val result = transfer(srcUri, dest) { progress, itemLabel ->
                val clamped = (progress ?: 0f).coerceIn(0f, 1f)
                moveProgress = (baseProgress + (clamped * progressSpan)).coerceIn(0f, 1f)
                moveLabel = itemLabel ?: "$label ${index + 1}/$total"
            }
            if (result.ok) {
                successCount += 1
            }
            moveProgress = ((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

        return successCount
    }

    fun resolveEntryDisplayName(uri: Uri): String {
        return runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name
                ?: DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "backup.json"
    }

    fun isSmpFile(uri: Uri): Boolean {
        val nameCandidates = linkedSetOf<String>()
        val displayName = resolveEntryDisplayName(uri).trim()
        if (displayName.isNotEmpty()) {
            nameCandidates += displayName
        }
        val singleName = runCatching { DocumentFile.fromSingleUri(context, uri)?.name?.trim() }.getOrNull()
        if (!singleName.isNullOrEmpty()) {
            nameCandidates += singleName
        }
        val lastPathSegment = uri.lastPathSegment?.trim()
        if (!lastPathSegment.isNullOrEmpty()) {
            nameCandidates += lastPathSegment
        }
        if (nameCandidates.any { it.endsWith(".smp", ignoreCase = true) }) {
            return true
        }

        val cleanMime = context.contentResolver.getType(uri)?.trim()?.lowercase()
        return cleanMime == "application/vnd.stage-music-player" ||
            cleanMime == "application/x-stage-music-player"
    }

    suspend fun importBackupJson(uri: Uri) {
        val fileLabel = resolveEntryDisplayName(uri)
        backupImportInProgress = true
        startLoading(sBackupImporting, determinate = false)
        try {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)
                    ?.use { input -> input.readBytes().toString(Charsets.UTF_8) }
            }

            if (json.isNullOrBlank()) {
                Toast.makeText(context, sBackupImportEmptyUnreadable, Toast.LENGTH_SHORT).show()
                return
            }

            withContext(Dispatchers.IO) {
                BackupManager.importState(context, json)
                LibraryIndexCache.clear(context)
            }
            onAfterBackupImport()
            Toast.makeText(context, sBackupImportSuccess, Toast.LENGTH_SHORT).show()
            Log.i("BackupImport", "Import réussi depuis bibliothèque: file=$fileLabel uri=$uri")
        } catch (error: Exception) {
            val detail = error.message ?: sBackupUnknownError
            Toast.makeText(
                context,
                context.getString(R.string.backup_import_failed, detail),
                Toast.LENGTH_LONG
            ).show()
            Log.e("BackupImport", "Import échoué depuis bibliothèque: file=$fileLabel uri=$uri", error)
        } finally {
            backupImportInProgress = false
            pendingBackupImportUri = null
            stopLoadingNice()
        }
    }

    // ---------- SAF launchers ----------
    val pickRootFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
// ✅ Si l'utilisateur passe par le picker SAF, on est forcément en mode SAF.
            StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
            scope.launch {
                startLoading(sScanning, determinate = false)
                try {
                    val folders = initializeSafWorkspaceFromPickedTree(
                        context = context,
                        pickedTreeUri = uri,
                        stage = "library_screen:pick_root"
                    ) ?: return@launch
                    BackupFolderPrefs.setDone(context, true)

                    currentFolderUri = folders.rootUri
                    folderStack = emptyList()

// --- rescan ---
                    runGlobalScan(
                        root = folders.rootUri,
                        folderToShow = folders.rootUri
                    )
                    onWorkspaceChanged()

                } finally {
                    stopLoadingNice()
                }
            }
        }
    )

    val moveToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { destUri ->
            val srcUris = pendingMoveSelection
            if (destUri != null && srcUris.isNotEmpty()) {
                scope.launch {
                    startLoading(sMoving, determinate = true)
                    try {
                        persistTreePermIfPossible(context, destUri)
                        val movedCount = runSelectionTransfer(
                            label = sMoving,
                            sources = srcUris,
                            dest = destUri
                        ) { srcUri, targetUri, onProgress ->
                            backend.move(
                                mainHandler = mainHandler,
                                srcUri = srcUri,
                                destUri = targetUri,
                                indexAll = indexAll,
                                onProgress = onProgress
                            ).also(::libraryLogMove)
                        }

                        val root = backend.getRootUri()
                        val refreshFolder = currentFolderUri ?: destUri
                        if (movedCount > 0 && root != null) {
                            runGlobalScan(root = root, folderToShow = refreshFolder)
                            selectedSongs = emptySet()
                        }
                    } finally {
                        pendingMoveSelection = emptyList()
                        stopLoadingNice()
                    }
                }
            } else {
                pendingMoveSelection = emptyList()
            }
        }
    )

    val copyToFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { destUri ->
            val srcUris = pendingCopySelection
            if (destUri != null && srcUris.isNotEmpty()) {
                scope.launch {
                    startLoading(sCopying, determinate = true)
                    try {
                        persistTreePermIfPossible(context, destUri)
                        val copiedCount = runSelectionTransfer(
                            label = sCopying,
                            sources = srcUris,
                            dest = destUri
                        ) { srcUri, targetUri, onProgress ->
                            backend.copyFile(
                                mainHandler = mainHandler,
                                srcUri = srcUri,
                                destUri = targetUri,
                                indexAll = indexAll,
                                onProgress = onProgress
                            )
                        }

                        val root = backend.getRootUri()
                        val refreshFolder = currentFolderUri ?: destUri
                        if (copiedCount > 0 && root != null) {
                            runGlobalScan(root = root, folderToShow = refreshFolder)
                        }
                        if (copiedCount < srcUris.size) {
                            Toast.makeText(context, sCopyFailed, Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        pendingCopySelection = emptyList()
                        stopLoadingNice()
                    }
                }
            } else {
                pendingCopySelection = emptyList()
            }
        }
    )

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { pickedUris ->
        if (pickedUris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            val singlePickedUri = pickedUris.singleOrNull()
            if (singlePickedUri != null && isSmpFile(singlePickedUri)) {
                val displayName = resolveEntryDisplayName(singlePickedUri)
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=redirect_audio_to_smp_import uri=$singlePickedUri name=$displayName"
                )
                Toast.makeText(context, sSmpDetectedImporting, Toast.LENGTH_SHORT).show()
                onImportGeneratedSmp(singlePickedUri)
                return@launch
            }

            val plan = withContext(Dispatchers.IO) {
                smpBatchProcessor.buildPlan(pickedUris)
            }
            if (!plan.hasSupportedItems) {
                importTargetFolderUri = null
                Toast.makeText(context, sBatchUnsupportedOnly, Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (plan.hasAudioToPrepare && SmpPreparationNoticePrefs.shouldShow(context)) {
                pendingDirectImportPlan = plan
            } else {
                runDirectBatchImport(plan)
            }
        }
    }

    // ---------- initial load ----------
    Log.e("SIG_LIB", "SIG#1 JUST BEFORE LaunchedEffect 2026-02-08 18:00 Z")
    LaunchedEffect(workspaceVersion) {
        val callId = initialLoadEffectPerfCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        var effectResult = "done"
        Log.e("SIG_LIB", "SIG#2 ENTER LaunchedEffect(Unit)")
        Log.i(
            LIBRARY_PERF_TRACE_TAG,
            "step=effect_initial_load_launch call=$callId timeMs=$startMs workspaceVersion=$workspaceVersion workspaceStatus=${workspaceSnapshot.status} workspaceRoot=${workspaceSnapshot.workspaceRootUri}"
        )
        Log.i(
            LIB_SMP_TRACE_TAG,
            "step=effect_initial_load_start workspaceVersion=$workspaceVersion workspaceStatus=${workspaceSnapshot.status} workspaceRoot=${workspaceSnapshot.workspaceRootUri} currentFolderUri=$currentFolderUri initialLoadDone=$initialLoadDone entriesSize=${entries.size}"
        )
        initialLoadDone = false
        try {
            withContext(Dispatchers.IO) {
                backend.ensureBaseFolders()
            }
            val root = backend.getRootUri()

            if (root == null) {
                effectResult = "no_root"
                currentFolderUri = null
                entries = emptyList()
                songItems = emptyList()
                Log.i(
                    LIB_SMP_TRACE_TAG,
                    "step=effect_initial_load_no_root workspaceVersion=$workspaceVersion workspaceStatus=${workspaceSnapshot.status} currentFolderUri=$currentFolderUri initialLoadDone=$initialLoadDone"
                )
                return@LaunchedEffect
            }

            indexAll = backend.loadIndex()
            val backendInitialFolder = backend.chooseInitialFolder(root, indexAll)
            val folderToShow = if (libraryViewMode == LIBRARY_VIEW_MODE_FILES) {
                backendInitialFolder
            } else {
                root
            }
            Log.i(
                "LIB_SCAN_DIAG",
                "mount root=$root indexSize=${indexAll.size} willFullScan=${indexAll.isEmpty()} backendInitialFolder=$backendInitialFolder folderToShow=$folderToShow"
            )
            currentFolderUri = folderToShow
            folderStack = if (libraryViewMode == LIBRARY_VIEW_MODE_FILES) {
                initialFolderStack(root, folderToShow)
            } else {
                emptyList()
            }

            if (indexAll.isEmpty()) {
                startLoading(sScanning, determinate = false)
                try {
                    runGlobalScan(
                        root = root,
                        folderToShow = folderToShow
                    )
                } finally {
                    stopLoadingNice()
                }
            } else {
                loadFolderEntriesAsync(
                    folderUri = folderToShow,
                    clearVisibleOnMiss = true
                )
            }
        } catch (t: Throwable) {
            effectResult = "crash"
            Log.e("SIG_LIB", "LaunchedEffect CRASH", t)
        } finally {
            initialLoadDone = true
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                LIBRARY_PERF_TRACE_TAG,
                "step=effect_initial_load_end call=$callId durationMs=$durationMs result=$effectResult currentFolderUri=$currentFolderUri entriesSize=${entries.size}"
            )
            Log.i(
                LIB_SMP_TRACE_TAG,
                "step=effect_initial_load_done workspaceVersion=$workspaceVersion workspaceStatus=${workspaceSnapshot.status} currentFolderUri=$currentFolderUri initialLoadDone=$initialLoadDone entriesSize=${entries.size} entries=${summarizeLibraryEntries(entries)}"
            )
        }
    }

// ⚠️ IMPORTANT : on supprime la normalisation DocumentsContract "string-based"
// car elle peut fabriquer un treeUri non exploitable selon le provider.



    // ---------- UI ----------
    val currentFolderName = currentFolderUri?.let { u ->
        if (isPrompterFolderUri(u)) {
            sPrompterFolder
        } else if (isSmpFolderUri(u)) {
            sSmpFolder
        } else if (u.scheme == "file") {
            java.io.File(u.path ?: "").name.ifBlank { "SPL_Music" }
        } else {
            val doc = DocumentFile.fromTreeUri(context, u) ?: DocumentFile.fromSingleUri(context, u)
            doc?.name ?: "SPL_Music"
        }
    } ?: sNoFolderSelected
    val headerFolderUri = if (isSongViewMode && currentFolderUri != null) SMP_FOLDER_URI else currentFolderUri
    val showSelectionBottomBar = selectedSongs.isNotEmpty() && isSongViewMode
    val selectionBottomPadding = if (showSelectionBottomBar) bottomBarHeight else 0.dp
    val isFilesSelectionContext = !isSongViewMode && selectedSongs.isNotEmpty()
    val isSetupDone = workspaceSnapshot.isUsable
    if (!isSetupDone) {
        DarkBlueGradientBackground {
            SetupInstallScreen(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                accent = accent,
                onSetupDone = {
                    BackupFolderPrefs.setDone(context, true)
                    onWorkspaceChanged()
                },
                onImportNow = {
                    importTargetFolderUri = backend.getRootUri()
                    importAudioLauncher.launch(arrayOf("audio/*"))
                },
                onImportLater = { },
                onDemoInstalled = { _ ->
                    LibraryFolderCache.clear()
                }
            )
        }
        return
    }

    DarkBlueGradientBackground {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

            LibraryHeader(
                titleColor = titleColor,
                subtitleColor = subtitleColor,
                currentFolderUri = headerFolderUri,
                canGoBack = !isSongViewMode && folderStack.isNotEmpty(),

                onBack = {
                    val parentUri = folderStack.lastOrNull() ?: backend.getRootUri()
                    val newStack = folderStack.dropLast(1)
                    Log.i(
                        LIB_SMP_TRACE_TAG,
                        "step=navigation_back from=$currentFolderUri to=$parentUri stackBefore=${folderStack.size} stackAfter=${newStack.size}"
                    )
                    currentFolderUri = parentUri
                    if (parentUri == null) {
                        entries = emptyList()
                    } else if (!showCachedEntries(parentUri)) {
                        entries = emptyList()
                    }
                    folderStack = newStack
                    selectedSongs = emptySet()
                },

                onPickRoot = { pickRootFolderLauncher.launch(null) },

                onRescan = {

                    scope.launch {
                        val rootNow = backend.getRootUri() ?: return@launch
                        startLoading(sScanning, determinate = false)
                        try {
                            val syncedArchiveCount = onSyncWorkspaceSmpArchives()
                            Log.i(
                                LIB_SMP_TRACE_TAG,
                                "step=manual_rescan_workspace_sync importedCount=$syncedArchiveCount currentFolderUri=$currentFolderUri viewMode=$libraryViewMode"
                            )
                            songItems = buildLibrarySongItemsAsync()
                            if (isSmpFolderUri(currentFolderUri)) {
                                Log.i(
                                    SMP_VIEW_TRACE_TAG,
                                    "elapsedMs=${SystemClock.elapsedRealtime()} step=manual_smp_rescan_start currentFolderUri=$currentFolderUri"
                                )
                                showSmpEntriesImmediately(refreshSmpEntriesAsync())
                                Log.i(
                                    SMP_VIEW_TRACE_TAG,
                                    "elapsedMs=${SystemClock.elapsedRealtime()} step=manual_smp_rescan_end currentFolderUri=$currentFolderUri entriesSize=${entries.size}"
                                )
                                return@launch
                            }
                            val folderToShow = currentFolderUri
                                ?.takeUnless { isPrompterFolderUri(it) || isSmpFolderUri(it) }
                                ?: if (libraryViewMode == LIBRARY_VIEW_MODE_FILES) {
                                    resolveFilesInitialFolder(rootNow)
                                } else {
                                    rootNow
                                }
                            if (libraryViewMode == LIBRARY_VIEW_MODE_FILES &&
                                currentFolderUri?.toString() != folderToShow.toString()
                            ) {
                                currentFolderUri = folderToShow
                                folderStack = initialFolderStack(rootNow, folderToShow)
                            }
                            runGlobalScan(
                                root = rootNow,
                                folderToShow = folderToShow
                            )
                        } finally {
                            stopLoadingNice()
                        }
                    }
                },

                onForget = {
                    stopQuickPlay()

                    clearPersistedUris(context)
                    BackupFolderPrefs.clear(context)
                    BackupFolderPrefsInternal.clear(context)
                    BackupFolderPrefsSaf.clear(context)
                    LibraryIndexCache.clear(context)
                    StorageModePrefs.set(context, StorageModePrefs.Mode.SAF)
                    currentFolderUri = null
                    entries = emptyList()
                    songItems = emptyList()
                    selectedSongs = emptySet()
                    folderStack = emptyList()
                    LibraryFolderCache.clear()
                    onWorkspaceChanged()
                },

                onImportBackingTracks = {
                    importTargetFolderUri = currentFolderUri?.takeUnless {
                        isPrompterFolderUri(it) || isSmpFolderUri(it)
                    }
                    importAudioLauncher.launch(
                        arrayOf(
                            "audio/*",
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },

                onConvertFolderToSmp = {
                    val folderUri = currentFolderUri?.takeUnless {
                        isPrompterFolderUri(it) || isSmpFolderUri(it)
                    } ?: return@LibraryHeader

                    scope.launch {
                        startLoading(sConvertingSmp, determinate = false)
                        try {
                            val results = withContext(Dispatchers.IO) {
                                smpConverter.convertFolder(folderUri)
                            }

                            if (results.isEmpty()) {
                                Toast.makeText(context, sConvertSmpNoMp3, Toast.LENGTH_SHORT).show()
                            } else {
                                var successCount = 0
                                var failureCount = 0
                                results.forEach { result ->
                                    val importedSong = result.getOrNull()?.let { outputUri ->
                                        onImportGeneratedSmp(outputUri)
                                    }
                                    if (importedSong != null) {
                                        successCount += 1
                                    } else {
                                        failureCount += 1
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.library_convert_smp_folder_summary,
                                        successCount,
                                        failureCount
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } finally {
                            stopLoadingNice()
                        }
                    }
                },

                onImportSmp = {
                    onImportExternalSmp()
                },
                selectionCount = if (isFilesSelectionContext) selectedSongs.size else 0,
                onCopySelection = if (isFilesSelectionContext) {
                    { openCopyBrowserForSelection(selectedSongs) }
                } else {
                    null
                },
                onMoveSelection = if (isFilesSelectionContext) {
                    { openMoveBrowserForSelection(selectedSongs) }
                } else {
                    null
                },
                onDeleteSelection = if (isFilesSelectionContext) {
                    { pendingDeleteSelection = normalizeSelection(selectedSongs) }
                } else {
                    null
                },
                onClearSelection = if (isFilesSelectionContext) {
                    { selectedSongs = emptySet() }
                } else {
                    null
                }
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryViewModeButton(
                    label = sSongsView,
                    selected = isSongViewMode,
                    accent = accent,
                    onClick = {
                        libraryViewMode = LIBRARY_VIEW_MODE_SONGS
                        selectedSongs = emptySet()
                        stopQuickPlay()
                        LibraryFolderCache.clear()
                    }
                )
                LibraryViewModeButton(
                    label = sFilesView,
                    selected = !isSongViewMode,
                    accent = accent,
                    onClick = {
                        val previousFolder = currentFolderUri
                        libraryViewMode = LIBRARY_VIEW_MODE_FILES
                        selectedSongs = emptySet()
                        stopQuickPlay()
                        LibraryFolderCache.clear()
                        val root = backend.getRootUri() ?: return@LibraryViewModeButton
                        val folderToShow = previousFolder
                            ?.takeUnless { isPrompterFolderUri(it) || isSmpFolderUri(it) }
                            ?: resolveFilesInitialFolder(root)
                        currentFolderUri = folderToShow
                        if (previousFolder == null ||
                            isPrompterFolderUri(previousFolder) ||
                            isSmpFolderUri(previousFolder)
                        ) {
                            folderStack = initialFolderStack(root, folderToShow)
                        }
                        scope.launch {
                            loadFolderEntriesAsync(
                                folderUri = folderToShow,
                                forceRefresh = true,
                                clearVisibleOnMiss = true
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

                if (currentFolderUri == null) {
                    Text(
                        text = sNoFolderHint,
                        color = subtitleColor,
                        fontSize = 13.sp
                    )
                } else {

                    Column(modifier = Modifier.fillMaxSize()) {

                        if (isSearchVisible) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .heightIn(min = 44.dp)
                                    .focusRequester(searchFocusRequester),
                                placeholder = { Text(sSearch) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            isSearchVisible = false
                                            focusManager.clearFocus(force = true)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.common_cd_close)
                                        )
                                    }
                                }
                            )

                            Spacer(Modifier.height(8.dp))
                        }

                        if (isSongViewMode) {
                            if (searchableSongItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sSmpEmptyState,
                                        color = subtitleColor,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LibrarySongsList(
                                    songs = filteredSongItems,
                                    currentPlayingSongId = currentPlayingSongId,
                                    cardBg = cardBg,
                                    rowBorder = rowBorder,
                                    accent = accent,
                                    bottomPadding = selectionBottomPadding,
                                    selectedSongs = selectedSongs,
                                    onToggleSelect = { uri ->
                                        toggleSelection(uri)
                                    },
                                    onOpenPlayer = { song ->
                                        stopQuickPlay()
                                        onPlayFromLibrary(song.playbackItem)
                                    },
                                    onAssignOne = { uri ->
                                        selectedSongs = setOf(uri)
                                        showAssignDialog = true
                                    },
                                    onShareOne = { uri ->
                                        shareSmpSong(uri)
                                    },
                                    onRenameOne = { song ->
                                        beginAliasRename(
                                            entry = LibraryEntry(
                                                uri = Uri.parse(song.playbackItem),
                                                name = song.fallbackTitle,
                                                isDirectory = false
                                            ),
                                            initialTitle = song.displayTitle
                                        )
                                    },
                                    onDeleteOne = { uri ->
                                        pendingDeleteSmpUri = uri
                                    }
                                )
                            }
                        } else {
                            val isEmptySmpFolder =
                                currentFolderUri?.let(::isSmpFolderUri) == true && searchableEntries.isEmpty()

                            if (isEmptySmpFolder) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sSmpEmptyState,
                                        color = subtitleColor,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LibraryList(
                                entries = filteredEntries,
                                cardBg = cardBg,
                                rowBorder = rowBorder,
                                accent = accent,
                                bottomPadding = selectionBottomPadding,
                                canImportBackupJson = canImportBackupJsonFromCurrentFolder,
                                selectedSongs = selectedSongs,

                                onToggleSelect = { uri ->
                                    toggleSelection(uri)
                                },

                                onOpenFolder = { entry ->
                                    if (entry.disabled) return@LibraryList
                                    Log.i(
                                        LIB_SMP_TRACE_TAG,
                                        "step=navigation_open_folder from=$currentFolderUri to=${entry.uri} name=${entry.name} stackBefore=${folderStack.size}"
                                    )
                                    currentFolderUri?.let { folderStack = folderStack + it }
                                    currentFolderUri = entry.uri
                                    if (!showCachedEntries(entry.uri)) {
                                        entries = emptyList()
                                    }
                                    searchQuery = ""
                                    selectedSongs = emptySet()
                                },

                                onOpenPlayer = { uri ->
                                    stopQuickPlay()
                                    onPlayFromLibrary(uri.toString())
                                },

                                onQuickPlay = { uri ->
                                    quickPlayToggle(uri)
                                },

                                onImportBackupJson = { uri ->
                                    pendingBackupImportUri = uri
                                },

                                onOpenLrcEditor = { lrcUri ->
                                    stopQuickPlay()

                                    lrcEditorUri = lrcUri
                                    lrcEditorName = runCatching {
                                        DocumentFile.fromSingleUri(context, lrcUri)?.name
                                            ?: DocumentFile.fromTreeUri(context, lrcUri)?.name
                                            ?: "lyrics.lrc"
                                    }.getOrNull() ?: "lyrics.lrc"

                                    lrcEditorText = readTextFromUri(lrcUri) ?: ""
                                    showLrcEditor = true
                                },

                                onConvertOneToSmp = { mp3Uri ->
                                    scope.launch {
                                        Log.i(
                                            "SMP_CONVERT_FLOW",
                                            "step=ui_start sourceUri=$mp3Uri backend=${if (mp3Uri.scheme == "file") "file" else "SAF"}"
                                        )
                                        startLoading(sConvertingSmp, determinate = false)
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                smpConverter.convertSingle(mp3Uri)
                                            }
                                            result.fold(
                                                onSuccess = { outputUri ->
                                                    Log.i(
                                                        "SMP_CONVERT_FLOW",
                                                        "step=conversion_ok sourceUri=$mp3Uri outputUri=$outputUri"
                                                    )
                                                    Log.i(
                                                        "SMP_CONVERT_FLOW",
                                                        "step=auto_import_call sourceUri=$mp3Uri outputUri=$outputUri"
                                                    )
                                                    val importedSong = onImportGeneratedSmp(outputUri)
                                                    if (importedSong != null) {
                                                        Log.i(
                                                            "SMP_CONVERT_FLOW",
                                                            "step=auto_import_ok sourceUri=$mp3Uri outputUri=$outputUri songId=${importedSong.id} title=${importedSong.title}"
                                                        )
                                                    } else {
                                                        Log.e(
                                                            "SMP_CONVERT_FLOW",
                                                            "step=auto_import_failed sourceUri=$mp3Uri outputUri=$outputUri"
                                                        )
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        if (importedSong != null) {
                                                            sConvertSmpSingleSuccess
                                                        } else {
                                                            sConvertSmpSingleFailed
                                                        },
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                onFailure = { error ->
                                                    Log.e(
                                                        "SMP_CONVERT_FLOW",
                                                        "step=conversion_failed_before_import sourceUri=$mp3Uri",
                                                        error
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        sConvertSmpSingleFailed,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        } finally {
                                            stopLoadingNice()
                                        }
                                    }
                                },

                                onAssignOne = { uri ->
                                    selectedSongs = setOf(uri)
                                    showAssignDialog = true
                                },

                                onShareOne = { uri ->
                                    shareSmpSong(uri)
                                },

                                onCopyOne = { uri ->
                                    openCopyBrowserForSelection(listOf(uri))
                                },

                                onMoveOne = { uri ->
                                    openMoveBrowserForSelection(listOf(uri))
                                },

                                onRenameOne = { entry ->
                                    val prompterId = extractPrompterId(entry.uri)
                                    if (prompterId != null) {
                                        val textSong = TextSongRepository.get(context, prompterId)
                                        if (textSong != null) {
                                            editPrompterId = prompterId
                                            editPrompterTitle = textSong.title
                                            editPrompterContent = textSong.content
                                            showEditPrompterDialog = true
                                        }
                                    } else {
                                        beginAliasRename(entry)
                                    }
                                },

                                onDeleteOne = { uri ->
                                    val prompterId = extractPrompterId(uri)
                                    if (prompterId != null) {
                                        if (deletePrompterAndRemoveFromAllPlaylists(context, uri.toString())) {
                                            selectedSongs = selectedSongs - uri
                                            val folder = currentFolderUri
                                            if (folder != null) {
                                                entries = buildEntriesForFolder(folder, useCache = false)
                                            }
                                        }
                                    } else {
                                        val smpSongId = getSmpSongId(uri.toString())
                                        if (smpSongId != null) {
                                            pendingDeleteSmpUri = uri
                                        } else {
                                            scope.launch {
                                                val plan = runCatching {
                                                    backend.planDelete(
                                                        target = uri,
                                                        indexAll = indexAll
                                                    )
                                                }.getOrElse {
                                                    LibraryDeletePlan(
                                                        target = LibraryDeleteItem(
                                                            uri = uri,
                                                            role = LibraryDeleteRole.FILE,
                                                            displayName = uri.lastPathSegment ?: "file"
                                                        ),
                                                        associated = emptyList()
                                                    )
                                                }
                                                pendingDeletePlan = plan
                                                showDeleteConfirmDialog = true
                                            }
                                        }
                                    }
                                }
                            )
                            }
                        }
                    }

                    if (showSelectionBottomBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .zIndex(20f)
                        ) {
                            LibraryBottomBar(
                                bottomBarHeight = bottomBarHeight,
                                selectedCount = selectedSongs.size,
                                onAssign = if (isSongViewMode) {
                                    { showAssignDialog = true }
                                } else {
                                    null
                                },
                                onCopy = if (isSongViewMode) {
                                    null
                                } else {
                                    { openCopyBrowserForSelection(selectedSongs) }
                                },
                                onMove = if (isSongViewMode) {
                                    null
                                } else {
                                    { openMoveBrowserForSelection(selectedSongs) }
                                },
                                onDelete = if (isSongViewMode) {
                                    null
                                } else {
                                    { pendingDeleteSelection = normalizeSelection(selectedSongs) }
                                },
                                onClear = { selectedSongs = emptySet() }
                            )
                        }
                    }
                }

                LibraryLoadingOverlay(isLoading = isLoading, moveProgress = moveProgress, moveLabel = moveLabel)
            }

            // ---------- dialogs ----------
            SmpPreparationNoticeDialog(
                show = pendingDirectImportPlan != null || pendingAssignRequest != null,
                onDismiss = {
                    when {
                        pendingDirectImportPlan != null -> {
                            pendingDirectImportPlan = null
                            importTargetFolderUri = null
                        }

                        pendingAssignRequest != null -> {
                            pendingAssignRequest = null
                        }
                    }
                },
                onContinue = { dontShowAgain ->
                    if (dontShowAgain) {
                        SmpPreparationNoticePrefs.setShouldShow(context, false)
                    }
                    when {
                        pendingDirectImportPlan != null -> {
                            val plan = pendingDirectImportPlan
                            pendingDirectImportPlan = null
                            if (plan != null) {
                                runDirectBatchImport(plan)
                            }
                        }

                        pendingAssignRequest != null -> {
                            val request = pendingAssignRequest
                            pendingAssignRequest = null
                            if (request != null) {
                                runLibraryPlaylistAssignment(request)
                            }
                        }
                    }
                }
            )
            AssignDialog(
                show = showAssignDialog,
                onDismiss = { showAssignDialog = false },
                onPlaylistSelected = { playlistName ->
                    showAssignDialog = false
                    val selection = selectedSongs
                    selectedSongs = emptySet()
                    prepareLibraryPlaylistAssignment(playlistName, selection)
                }
            )
            if (pendingBackupImportUri != null) {
                val importUri = pendingBackupImportUri!!
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (backupImportInProgress) return@AlertDialog
                        pendingBackupImportUri = null
                    },
                    title = {
                        androidx.compose.material3.Text(sBackupImportAction)
                    },
                    text = {
                        androidx.compose.material3.Text(resolveEntryDisplayName(importUri))
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !backupImportInProgress,
                            onClick = {
                                if (backupImportInProgress) return@TextButton
                                scope.launch {
                                    importBackupJson(importUri)
                                }
                            }
                        ) {
                            androidx.compose.material3.Text(sBackupImportAction)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !backupImportInProgress,
                            onClick = {
                                if (backupImportInProgress) return@TextButton
                                pendingBackupImportUri = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
            // ✅ Nouveau dialog suppression : audio seul OU audio + fichiers associes
            if (showDeleteConfirmDialog && pendingDeletePlan != null) {
                val deletePlan = pendingDeletePlan!!
                val hasAssociated = deletePlan.isAudioTarget && deletePlan.hasAssociated
                val isDirectoryTarget = isDirectoryUri(deletePlan.target.uri)

                suspend fun executeDeletion(includeAssociated: Boolean) {
                    if (deleteInProgress) return
                    deleteInProgress = true
                    showDeleteConfirmDialog = false
                    startLoading(sDeleting, determinate = false)
                    try {
                        val result = backend.deleteWithPlan(
                            plan = deletePlan,
                            includeAssociated = includeAssociated
                        )
                        applyDeleteResult(result)

                        val root = backend.getRootUri()
                        val folderUri = currentFolderUri ?: root
                        if (root != null && folderUri != null) {
                            runGlobalScan(root = root, folderToShow = folderUri)
                        }
                    } finally {
                        deleteInProgress = false
                        showDeleteConfirmDialog = false
                        pendingDeletePlan = null
                        stopLoadingNice()
                    }
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (deleteInProgress) return@AlertDialog
                        showDeleteConfirmDialog = false
                        pendingDeletePlan = null
                    },
                    title = {
                        androidx.compose.material3.Text(
                            when {
                                deletePlan.isAudioTarget -> sDeleteBackingTrackTitle
                                isDirectoryTarget -> sDeleteFolderTitle
                                else -> sDeleteFileTitle
                            }
                        )
                    },
                    text = {
                        if (!hasAssociated) {
                            androidx.compose.material3.Text(
                                if (isDirectoryTarget) sDeleteFolderConfirmText else sDeleteConfirmText
                            )
                        }
                    },
                    confirmButton = {
                        Column {
                            if (hasAssociated) {
                                androidx.compose.material3.TextButton(
                                    enabled = !deleteInProgress,
                                    onClick = {
                                        if (deleteInProgress) return@TextButton
                                        scope.launch {
                                            executeDeletion(includeAssociated = true)
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.Text(sDeleteAudioPlusLrc)
                                }
                            }

                            androidx.compose.material3.TextButton(
                                enabled = !deleteInProgress,
                                onClick = {
                                    if (deleteInProgress) return@TextButton
                                    scope.launch {
                                        executeDeletion(includeAssociated = false)
                                    }
                                }
                            ) {
                                androidx.compose.material3.Text(
                                    if (deletePlan.isAudioTarget) sDeleteAudioOnly else sDeletePermanently
                                )
                            }
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteInProgress,
                            onClick = {
                                if (deleteInProgress) return@TextButton
                                showDeleteConfirmDialog = false
                                pendingDeletePlan = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            if (pendingDeleteSelection.isNotEmpty()) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (deleteInProgress) return@AlertDialog
                        pendingDeleteSelection = emptyList()
                    },
                    title = {
                        androidx.compose.material3.Text(sDeleteSelectedTitle)
                    },
                    text = {
                        androidx.compose.material3.Text(
                            context.getString(
                                R.string.library_delete_selected_confirm_text,
                                pendingDeleteSelection.size
                            )
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteInProgress,
                            onClick = {
                                if (deleteInProgress) return@TextButton
                                val selection = pendingDeleteSelection
                                scope.launch {
                                    deleteInProgress = true
                                    startLoading(sDeleting, determinate = false)
                                    try {
                                        val results = selection.flatMap { uri ->
                                            val plan = planDeleteForUri(uri)
                                            backend.deleteWithPlan(
                                                plan = plan,
                                                includeAssociated = false
                                            ).results
                                        }
                                        applyDeleteResult(LibraryDeleteResult(results))

                                        val root = backend.getRootUri()
                                        val folderUri = currentFolderUri ?: root
                                        if (root != null && folderUri != null) {
                                            runGlobalScan(root = root, folderToShow = folderUri)
                                        }
                                    } finally {
                                        deleteInProgress = false
                                        pendingDeleteSelection = emptyList()
                                        stopLoadingNice()
                                    }
                                }
                            }
                        ) {
                            androidx.compose.material3.Text(
                                stringResource(R.string.library_delete_action),
                                color = Color(0xFFFF6464)
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteInProgress,
                            onClick = {
                                if (deleteInProgress) return@TextButton
                                pendingDeleteSelection = emptyList()
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            if (pendingDeleteSmpUri != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        if (deleteSmpInProgress) return@AlertDialog
                        pendingDeleteSmpUri = null
                    },
                    title = {
                        androidx.compose.material3.Text(sDeleteSmpTitle)
                    },
                    text = {
                        androidx.compose.material3.Text(sDeleteSmpConfirmText)
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteSmpInProgress,
                            onClick = {
                                if (deleteSmpInProgress) return@TextButton
                                val deleteUri = pendingDeleteSmpUri ?: return@TextButton
                                val songId = getSmpSongId(deleteUri.toString()) ?: return@TextButton
                                scope.launch {
                                    deleteSmpInProgress = true
                                    startLoading(sDeleting, determinate = false)
                                    try {
                                        val deleted = onDeleteSmpSong(songId)
                                        if (deleted) {
                                            selectedSongs = selectedSongs - deleteUri
                                            LibraryFolderCache.clear()
                                        } else {
                                            Toast.makeText(context, sDeleteSmpFailed, Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        deleteSmpInProgress = false
                                        pendingDeleteSmpUri = null
                                        stopLoadingNice()
                                    }
                                }
                            }
                        ) {
                            androidx.compose.material3.Text(
                                stringResource(R.string.library_delete_action),
                                color = Color(0xFFFF6464)
                            )
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            enabled = !deleteSmpInProgress,
                            onClick = {
                                if (deleteSmpInProgress) return@TextButton
                                pendingDeleteSmpUri = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            val commitAliasRename: () -> Unit = commit@{
                focusManager.clearFocus(force = true)

                val target = renameTarget ?: return@commit
                val newTitle = renameText.trim()
                if (newTitle.isEmpty()) {
                    renameTarget = null
                    return@commit
                }

                if (BuildConfig.DEBUG) {
                    Log.d("ALIAS_RENAME", "commit source=library uri=${target.uri} newTitle='$newTitle'")
                }

                renameTarget = null
                scope.launch {
                    startLoading(sRenaming, determinate = false)
                    try {
                        val saved = withContext(Dispatchers.IO) {
                            TitleAliasesStore.setTitleForTrack(context, target.uri.toString(), newTitle)
                        }
                        if (saved) {
                            PlaylistRepository.clearCustomTitleEverywhere(target.uri.toString())
                        }

                        if (BuildConfig.DEBUG) {
                            Toast.makeText(
                                context,
                                if (saved) "Alias enregistré" else "Alias NON enregistré (voir logs)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        stopLoadingNice()
                    }
                }
            }

            RenameDialog(
                show = renameTarget != null,
                renameText = renameText,
                onRenameText = { renameText = it },
                onCancel = { renameTarget = null },
                enabled = !isLoading,
                onConfirm = commitAliasRename
            )

            if (showEditPrompterDialog && editPrompterId != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        showEditPrompterDialog = false
                        editPrompterId = null
                    },
                    title = {
                        androidx.compose.material3.Text(stringResource(R.string.quickplaylists_edit_prompter_title))
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editPrompterTitle,
                                onValueChange = { editPrompterTitle = it },
                                label = { Text(stringResource(R.string.common_title_label)) },
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editPrompterContent,
                                onValueChange = { editPrompterContent = it },
                                label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                                minLines = 4
                            )
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val id = editPrompterId ?: return@TextButton
                                val title = editPrompterTitle.trim()
                                val content = editPrompterContent.trim()
                                if (title.isBlank()) return@TextButton
                                TextSongRepository.update(
                                    context = context,
                                    id = id,
                                    title = title,
                                    content = content
                                )
                                val folder = currentFolderUri
                                if (folder != null) {
                                    entries = buildEntriesForFolder(folder, useCache = false)
                                }
                                showEditPrompterDialog = false
                                editPrompterId = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_save))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showEditPrompterDialog = false
                                editPrompterId = null
                            }
                        ) {
                            androidx.compose.material3.Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            MoveBrowserDialog(
                show = showMoveBrowser && pendingMoveSelection.isNotEmpty(),
                indexAll = indexAll,
                root = backend.getRootUri(),
                moveBrowserFolder = moveBrowserFolder,
                moveBrowserStack = moveBrowserStack,
                titleText = stringResource(R.string.library_move_browser_title),
                actionText = stringResource(R.string.library_move_browser_here),
                otherFolderText = stringResource(R.string.library_move_browser_other_folder),
                onGoUp = {
                    val root = backend.getRootUri()
                    val newStack = moveBrowserStack.dropLast(1)
                    val parent = newStack.lastOrNull() ?: root
                    moveBrowserStack = newStack
                    moveBrowserFolder = parent
                },
                onEnterFolder = { folderUri ->
                    val root = backend.getRootUri()
                    val from = moveBrowserFolder ?: root ?: folderUri
                    moveBrowserStack = moveBrowserStack + from
                    moveBrowserFolder = folderUri
                },
                onMoveHere = {
                    val rootTree = backend.getRootUri() ?: return@MoveBrowserDialog
                    val dest = moveBrowserFolder ?: rootTree
                    val sources = pendingMoveSelection
                    if (sources.isEmpty()) return@MoveBrowserDialog

                    showMoveBrowser = false

                    scope.launch {
                        startLoading(sMoving, determinate = true)
                        try {
                            val movedCount = runSelectionTransfer(
                                label = sMoving,
                                sources = sources,
                                dest = dest
                            ) { srcUri, targetUri, onProgress ->
                                backend.move(
                                    mainHandler = mainHandler,
                                    srcUri = srcUri,
                                    destUri = targetUri,
                                    indexAll = indexAll,
                                    onProgress = onProgress
                                ).also(::libraryLogMove)
                            }

                            if (movedCount > 0) {
                                runGlobalScan(
                                    root = rootTree,
                                    folderToShow = currentFolderUri ?: dest
                                )
                                selectedSongs = emptySet()
                            }
                        } finally {
                            pendingMoveSelection = emptyList()
                            showMoveBrowser = false
                            stopLoadingNice()
                        }
                    }
                },
                onDismiss = {
                    showMoveBrowser = false
                    pendingMoveSelection = emptyList()
                },
                onOtherFolder = {
                    showMoveBrowser = false
                    moveToFolderLauncher.launch(null)
                }
            )

            MoveBrowserDialog(
                show = showCopyBrowser && pendingCopySelection.isNotEmpty(),
                indexAll = indexAll,
                root = backend.getRootUri(),
                moveBrowserFolder = copyBrowserFolder,
                moveBrowserStack = copyBrowserStack,
                titleText = stringResource(R.string.library_copy_browser_title),
                actionText = stringResource(R.string.library_copy_browser_here),
                otherFolderText = stringResource(R.string.library_move_browser_other_folder),
                onGoUp = {
                    val root = backend.getRootUri()
                    val newStack = copyBrowserStack.dropLast(1)
                    val parent = newStack.lastOrNull() ?: root
                    copyBrowserStack = newStack
                    copyBrowserFolder = parent
                },
                onEnterFolder = { folderUri ->
                    val root = backend.getRootUri()
                    val from = copyBrowserFolder ?: root ?: folderUri
                    copyBrowserStack = copyBrowserStack + from
                    copyBrowserFolder = folderUri
                },
                onMoveHere = {
                    val rootTree = backend.getRootUri() ?: return@MoveBrowserDialog
                    val dest = copyBrowserFolder ?: rootTree
                    val sources = pendingCopySelection
                    if (sources.isEmpty()) return@MoveBrowserDialog

                    showCopyBrowser = false

                    scope.launch {
                        startLoading(sCopying, determinate = true)
                        try {
                            val copiedCount = runSelectionTransfer(
                                label = sCopying,
                                sources = sources,
                                dest = dest
                            ) { srcUri, targetUri, onProgress ->
                                backend.copyFile(
                                    mainHandler = mainHandler,
                                    srcUri = srcUri,
                                    destUri = targetUri,
                                    indexAll = indexAll,
                                    onProgress = onProgress
                                )
                            }

                            if (copiedCount > 0) {
                                runGlobalScan(
                                    root = rootTree,
                                    folderToShow = currentFolderUri ?: dest
                                )
                            }
                            if (copiedCount < sources.size) {
                                Toast.makeText(context, sCopyFailed, Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            pendingCopySelection = emptyList()
                            showCopyBrowser = false
                            stopLoadingNice()
                        }
                    }
                },
                onDismiss = {
                    showCopyBrowser = false
                    pendingCopySelection = emptyList()
                },
                onOtherFolder = {
                    showCopyBrowser = false
                    copyToFolderLauncher.launch(null)
                }
            )

            // ---------------- LRC EDITOR DIALOG ----------------
            if (showLrcEditor) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showLrcEditor = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.98f)
                            .fillMaxHeight(0.92f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.library_lrc_editing_title, lrcEditorName),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            androidx.compose.material3.OutlinedTextField(
                                value = lrcEditorText,
                                onValueChange = { lrcEditorText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                singleLine = false
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = { showLrcEditor = false }
                                ) { androidx.compose.material3.Text(stringResource(R.string.common_cancel)) }

                                Spacer(Modifier.width(8.dp))

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        val uri = lrcEditorUri ?: return@TextButton
                                        val ok = writeTextToUri(uri, lrcEditorText)
                                        if (ok) showLrcEditor = false
                                        else Log.e("LRC", "Échec écriture sur $uri")
                                    }
                                ) { androidx.compose.material3.Text(stringResource(R.string.common_save)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
