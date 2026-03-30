package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

data class SmpSecureImportResult(
    val importedSong: SongUnit? = null,
    val durableArchiveUri: Uri? = null,
    val failureReason: String? = null
) {
    val isSuccess: Boolean
        get() = importedSong != null && durableArchiveUri != null
}

class SmpSecureImportPipeline(private val context: Context) {

    companion object {
        private const val TAG = "SMP_SECURE_IMPORT"
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

    fun import(
        uri: Uri,
        importer: SmpImporter = SmpImporter(context)
    ): SmpSecureImportResult {
        val preImportSongIds = smpLibraryScanner.listSongs().map { it.id }.toSet()
        val predictedSongId = SmpArchiveSongIdResolver.readStableSongId(context, uri)
        val rollbackPreparation = prepareRuntimeRollback(predictedSongId)
        val rollbackSnapshot = rollbackPreparation.snapshot

        try {
            if (rollbackPreparation.failureReason != null) {
                return SmpSecureImportResult(failureReason = rollbackPreparation.failureReason)
            }

            val importedSong = importer.importSmp(uri)
                ?: return SmpSecureImportResult(
                    failureReason = importer.lastFailureReason ?: "import SMP impossible"
                )

            val archivePersistResult = SmpWorkspaceArchiveStore.persistNormalizedArchive(context, importedSong)
            if (archivePersistResult.archiveUri != null) {
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
            return SmpSecureImportResult(failureReason = failureReason)
        } finally {
            cleanupRollbackSnapshot(rollbackSnapshot)
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
