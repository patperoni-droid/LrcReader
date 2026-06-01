package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.WorkspaceResolver
import java.io.File

data class SmpSecureImportResult(
    val importedSong: SongUnit? = null,
    val durableArchiveUri: Uri? = null,
    val failureReason: String? = null
) {
    val isSuccess: Boolean
        get() = importedSong != null && durableArchiveUri != null
}

data class SmpRuntimeReadyImportResult(
    val importedSong: SongUnit? = null,
    val archiveRequestId: String? = null,
    val archiveState: SmpArchivePersistState? = null,
    val archiveFailureReason: String? = null,
    val failureReason: String? = null
) {
    val isRuntimeReadySuccess: Boolean
        get() = importedSong != null
}

class SmpSecureImportPipeline(private val context: Context) {

    companion object {
        private const val TAG = "SMP_SECURE_IMPORT"
        private const val IMPORT_TRACE_TAG = "IMPORT_TRACE"
    }

    private data class RuntimeRollbackSnapshot(
        val originalDir: File,
        val snapshotDir: File
    )

    private data class RuntimeRollbackPreparation(
        val snapshot: RuntimeRollbackSnapshot? = null,
        val failureReason: String? = null
    )

    private val smpLibraryScanner by lazy(LazyThreadSafetyMode.NONE) { SmpLibraryScanner(context) }

    fun importRuntimeReady(
        uri: Uri,
        importer: SmpImporter = SmpImporter(context)
    ): SmpRuntimeReadyImportResult {
        val importStartMs = SystemClock.elapsedRealtime()
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=$importStartMs step=secure_runtime_ready_start uri=$uri"
        )
        val workspaceSnapshot = WorkspaceResolver.resolve(context)
        if (!workspaceSnapshot.isUsable || workspaceSnapshot.workspaceRootUri == null) {
            Log.e(
                TAG,
                "Import SMP runtime-ready refusé uri=$uri workspaceStatus=${workspaceSnapshot.status} workspaceRoot=${workspaceSnapshot.workspaceRootUri}"
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=workspace_unavailable"
            )
            return SmpRuntimeReadyImportResult(
                failureReason = "workspace durable indisponible pour l'import SMP"
            )
        }

        val runtimeImportStartMs = SystemClock.elapsedRealtime()
        val importedSong = importer.importSmp(uri)
            ?: run {
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_runtime_done durationMs=${SystemClock.elapsedRealtime() - runtimeImportStartMs} uri=$uri result=runtime_import_failed failureReason=${importer.lastFailureReason}"
                )
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=runtime_import_failed failureReason=${importer.lastFailureReason}"
                )
                return SmpRuntimeReadyImportResult(
                    failureReason = importer.lastFailureReason ?: "import SMP impossible"
                )
            }
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_runtime_done durationMs=${SystemClock.elapsedRealtime() - runtimeImportStartMs} uri=$uri songId=${importedSong.id}"
        )

        val nowMs = System.currentTimeMillis()
        val requestId = "${importedSong.id}_$nowMs"
        val pendingRecord = SmpArchiveFinalizeRecord(
            songId = importedSong.id,
            requestId = requestId,
            state = SmpArchivePersistState.PENDING,
            mode = workspaceSnapshot.mode,
            workspaceRootUri = workspaceSnapshot.workspaceRootUri.toString(),
            setupTreeUri = workspaceSnapshot.setupTreeUri?.toString(),
            requestedAtMs = nowMs,
            updatedAtMs = nowMs
        )

        val enqueueResult = runCatching {
            SmpArchiveFinalizeStore.savePending(context, pendingRecord)
            SmpArchiveFinalizeScheduler.schedule(context, pendingRecord)
        }
        val enqueueError = enqueueResult.exceptionOrNull()
        if (enqueueError != null) {
            val failureReason = enqueueError.message ?: "planification persistance archive impossible"
            SmpArchiveFinalizeStore.markFailed(
                context = context,
                songId = importedSong.id,
                requestId = requestId,
                reason = failureReason
            )
            Log.e(
                TAG,
                "Import SMP runtime-ready: planification archive impossible songId=${importedSong.id} uri=$uri",
                enqueueError
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=runtime_ready_archive_enqueue_failed songId=${importedSong.id} failureReason=$failureReason"
            )
            return SmpRuntimeReadyImportResult(
                importedSong = importedSong,
                archiveRequestId = requestId,
                archiveState = SmpArchivePersistState.FAILED,
                archiveFailureReason = failureReason
            )
        }

        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_archive_enqueued uri=$uri songId=${importedSong.id} requestId=$requestId"
        )
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_runtime_ready_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=runtime_ready_success songId=${importedSong.id} requestId=$requestId"
        )
        return SmpRuntimeReadyImportResult(
            importedSong = importedSong,
            archiveRequestId = requestId,
            archiveState = SmpArchivePersistState.PENDING
        )
    }

    fun import(
        uri: Uri,
        importer: SmpImporter = SmpImporter(context),
        preserveExistingLyricsOnReplace: Boolean = true
    ): SmpSecureImportResult {
        val importStartMs = SystemClock.elapsedRealtime()
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=$importStartMs step=secure_import_start uri=$uri"
        )
        val workspaceSnapshot = WorkspaceResolver.resolve(context)
        if (!workspaceSnapshot.isUsable || workspaceSnapshot.workspaceRootUri == null) {
            Log.e(
                TAG,
                "Import SMP sécurisé refusé uri=$uri workspaceStatus=${workspaceSnapshot.status} workspaceRoot=${workspaceSnapshot.workspaceRootUri}"
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=workspace_unavailable"
            )
            return SmpSecureImportResult(
                failureReason = "workspace durable indisponible pour l'import SMP sécurisé"
            )
        }

        val preScanStartMs = SystemClock.elapsedRealtime()
        val preImportSongIds = smpLibraryScanner.listSongs().map { it.id }.toSet()
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_pre_scan_done durationMs=${SystemClock.elapsedRealtime() - preScanStartMs} uri=$uri preImportCount=${preImportSongIds.size}"
        )
        val predictedSongId = SmpArchiveSongIdResolver.readStableSongId(context, uri)
        val rollbackStartMs = SystemClock.elapsedRealtime()
        val rollbackPreparation = prepareRuntimeRollback(predictedSongId)
        val rollbackSnapshot = rollbackPreparation.snapshot
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_rollback_prep_done durationMs=${SystemClock.elapsedRealtime() - rollbackStartMs} uri=$uri predictedSongId=$predictedSongId hasSnapshot=${rollbackSnapshot != null} failureReason=${rollbackPreparation.failureReason}"
        )

        try {
            if (rollbackPreparation.failureReason != null) {
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=rollback_prep_failed failureReason=${rollbackPreparation.failureReason}"
                )
                return SmpSecureImportResult(failureReason = rollbackPreparation.failureReason)
            }

            val runtimeImportStartMs = SystemClock.elapsedRealtime()
            val importedSong = importer.importSmp(
                uri = uri,
                preserveExistingLyricsOnReplace = preserveExistingLyricsOnReplace
            )
                ?: run {
                    Log.i(
                        IMPORT_TRACE_TAG,
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_runtime_done durationMs=${SystemClock.elapsedRealtime() - runtimeImportStartMs} uri=$uri result=runtime_import_failed failureReason=${importer.lastFailureReason}"
                    )
                    Log.i(
                        IMPORT_TRACE_TAG,
                        "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=runtime_import_failed failureReason=${importer.lastFailureReason}"
                    )
                    return SmpSecureImportResult(
                        failureReason = importer.lastFailureReason ?: "import SMP impossible"
                    )
                }
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_runtime_done durationMs=${SystemClock.elapsedRealtime() - runtimeImportStartMs} uri=$uri songId=${importedSong.id}"
            )

            val archivePersistStartMs = SystemClock.elapsedRealtime()
            val archivePersistResult = SmpWorkspaceArchiveStore.persistNormalizedArchive(
                context = context,
                songUnit = importedSong,
                snapshotOverride = workspaceSnapshot
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_archive_persist_done durationMs=${SystemClock.elapsedRealtime() - archivePersistStartMs} uri=$uri songId=${importedSong.id} archiveUri=${archivePersistResult.archiveUri} failureReason=${archivePersistResult.failureReason}"
            )
            if (archivePersistResult.archiveUri != null) {
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=success songId=${importedSong.id}"
                )
                return SmpSecureImportResult(
                    importedSong = importedSong,
                    durableArchiveUri = archivePersistResult.archiveUri
                )
            }

            val archiveFailureReason = archivePersistResult.failureReason
                ?: "écriture de l'archive durable impossible"

            val rollbackSucceeded = when {
                rollbackSnapshot != null -> restoreRuntimeFromSnapshot(
                    snapshot = rollbackSnapshot,
                    importedSong = importedSong
                )

                importedSong.id !in preImportSongIds -> deleteImportedRuntimeSong(importedSong)
                else -> false
            }

            val failureReason = when {
                rollbackSnapshot != null && rollbackSucceeded ->
                    "archive durable impossible, import annulé et morceau précédent restauré"

                importedSong.id !in preImportSongIds && rollbackSucceeded ->
                    "archive durable impossible, import annulé"

                else ->
                    "archive durable impossible après import runtime, changement runtime conservé"
            }

            Log.e(
                TAG,
                "Import SMP incomplet uri=$uri songId=${importedSong.id} archiveReason=$archiveFailureReason rollbackSucceeded=$rollbackSucceeded"
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=archive_failure songId=${importedSong.id} rollbackSucceeded=$rollbackSucceeded failureReason=$failureReason"
            )
            return SmpSecureImportResult(failureReason = failureReason)
        } finally {
            cleanupRollbackSnapshot(rollbackSnapshot)
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=secure_import_cleanup_done durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri hasSnapshot=${rollbackSnapshot != null}"
            )
        }
    }

    private fun prepareRuntimeRollback(songId: String?): RuntimeRollbackPreparation {
        val cleanSongId = songId?.trim().orEmpty()
        if (cleanSongId.isEmpty()) {
            return RuntimeRollbackPreparation()
        }

        val tracksRoot = File(context.filesDir, "tracks")
        val originalDir = File(tracksRoot, cleanSongId)
        if (!originalDir.isDirectory) {
            return RuntimeRollbackPreparation()
        }

        val snapshotsRoot = File(context.cacheDir, "smp_runtime_snapshots")
        if (!snapshotsRoot.exists() && !snapshotsRoot.mkdirs()) {
            return RuntimeRollbackPreparation(
                failureReason = "préservation du morceau existant impossible"
            )
        }

        val snapshotDir = File(
            snapshotsRoot,
            "${cleanSongId}_${System.currentTimeMillis()}"
        )

        return runCatching {
            originalDir.copyRecursively(snapshotDir, overwrite = true)
            RuntimeRollbackPreparation(
                snapshot = RuntimeRollbackSnapshot(
                    originalDir = originalDir,
                    snapshotDir = snapshotDir
                )
            )
        }.getOrElse { error ->
            Log.e(
                TAG,
                "Préservation runtime impossible songId=$cleanSongId dir=${originalDir.absolutePath}",
                error
            )
            RuntimeRollbackPreparation(
                failureReason = "préservation du morceau existant impossible"
            )
        }
    }

    private fun restoreRuntimeFromSnapshot(
        snapshot: RuntimeRollbackSnapshot,
        importedSong: SongUnit
    ): Boolean {
        val importedDir = importedSong.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)

        if (
            importedDir != null &&
            importedDir.exists() &&
            importedDir.absolutePath != snapshot.originalDir.absolutePath &&
            !importedDir.deleteRecursively()
        ) {
            Log.e(TAG, "Rollback SMP impossible: suppression nouvel import ${importedDir.absolutePath}")
            return false
        }

        if (snapshot.originalDir.exists() && !snapshot.originalDir.deleteRecursively()) {
            Log.e(TAG, "Rollback SMP impossible: suppression runtime courant ${snapshot.originalDir.absolutePath}")
            return false
        }

        if (snapshot.snapshotDir.renameTo(snapshot.originalDir)) {
            return true
        }

        val restored = runCatching {
            snapshot.snapshotDir.copyRecursively(snapshot.originalDir, overwrite = true)
        }.isSuccess

        if (!restored) {
            Log.e(TAG, "Rollback SMP impossible: restauration snapshot ${snapshot.snapshotDir.absolutePath}")
            return false
        }

        if (snapshot.snapshotDir.exists() && !snapshot.snapshotDir.deleteRecursively()) {
            Log.w(TAG, "Suppression du snapshot après rollback impossible: ${snapshot.snapshotDir.absolutePath}")
        }
        return true
    }

    private fun deleteImportedRuntimeSong(importedSong: SongUnit): Boolean {
        val runtimeDir = importedSong.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return false

        if (!runtimeDir.exists()) {
            return true
        }

        if (!runtimeDir.deleteRecursively()) {
            Log.e(TAG, "Rollback SMP impossible: suppression dossier runtime ${runtimeDir.absolutePath}")
            return false
        }

        return true
    }

    private fun cleanupRollbackSnapshot(snapshot: RuntimeRollbackSnapshot?) {
        val snapshotDir = snapshot?.snapshotDir ?: return
        if (snapshotDir.exists() && !snapshotDir.deleteRecursively()) {
            Log.w(TAG, "Suppression du snapshot SMP impossible: ${snapshotDir.absolutePath}")
        }
    }
}
