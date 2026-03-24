package com.patrick.lrcreader.core.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.smp.SmpImporter
import java.io.File
import java.io.InputStream

data class BackupBundleImportedSong(
    val bundleSongId: String,
    val importedSongId: String,
    val storageFolder: String? = null
)

sealed interface BackupBundleSmpImportResult {
    data class Success(
        val importedSong: BackupBundleImportedSong
    ) : BackupBundleSmpImportResult

    data class Failure(
        val reason: String? = null
    ) : BackupBundleSmpImportResult
}

sealed interface BackupBundleImportResult {
    data object NotBundle : BackupBundleImportResult
    data object InvalidBundle : BackupBundleImportResult

    data class Success(
        val importedSongs: List<BackupBundleImportedSong>,
        val stateJson: String
    ) : BackupBundleImportResult {
        val importedSongIds: List<String>
            get() = importedSongs.map { it.importedSongId }

        val songIdRemap: Map<String, String>
            get() = importedSongs.associate { it.bundleSongId to it.importedSongId }
    }

    data class SmpImportFailed(
        val songId: String,
        val reason: String? = null
    ) : BackupBundleImportResult
}

object BackupBundleImporter {

    private const val TAG = "BACKUP_IMPORT"

    fun isBundleFileName(fileName: String?): Boolean {
        val trimmed = fileName?.trim().orEmpty()
        return trimmed.endsWith(BACKUP_BUNDLE_EXTENSION, ignoreCase = true)
    }

    fun importBundleIfApplicable(
        context: Context,
        fileName: String?,
        openInputStream: () -> InputStream?
    ): BackupBundleImportResult {
        return importBundleIfApplicable(
            fileName = fileName,
            readPayload = {
                openInputStream()?.use { input ->
                    BackupBundleIo.readOrNull(input)
                }
            },
            importSmpFile = { smpFile ->
                importSmpFile(context, smpFile)
            },
            rollbackImportedSong = { importedSong ->
                rollbackImportedSong(importedSong)
            }
        )
    }

    fun importBundleIfApplicable(
        fileName: String?,
        readPayload: () -> BackupBundlePayload?,
        importSmpFile: (BackupBundleSmpFile) -> BackupBundleSmpImportResult,
        rollbackImportedSong: (BackupBundleImportedSong) -> Unit = {}
    ): BackupBundleImportResult {
        if (!isBundleFileName(fileName)) return BackupBundleImportResult.NotBundle

        val payload = runCatching { readPayload() }.getOrNull()
            ?: return BackupBundleImportResult.InvalidBundle

        val importedSongs = mutableListOf<BackupBundleImportedSong>()

        payload.smpFiles.forEach { smpFile ->
            when (val stepResult = runCatching { importSmpFile(smpFile) }.getOrElse {
                BackupBundleSmpImportResult.Failure(it.message)
            }) {
                is BackupBundleSmpImportResult.Success -> {
                    importedSongs += stepResult.importedSong
                }

                is BackupBundleSmpImportResult.Failure -> {
                    importedSongs.asReversed().forEach { importedSong ->
                        runCatching { rollbackImportedSong(importedSong) }
                    }
                    return BackupBundleImportResult.SmpImportFailed(
                        songId = smpFile.songId,
                        reason = stepResult.reason
                    )
                }
            }
        }

        return BackupBundleImportResult.Success(
            importedSongs = importedSongs,
            stateJson = payload.stateJson
        )
    }

    private fun importSmpFile(
        context: Context,
        smpFile: BackupBundleSmpFile
    ): BackupBundleSmpImportResult {
        val tempFile = createTempSmpFile(context, smpFile.songId) ?: return BackupBundleSmpImportResult.Failure(
            reason = "création du fichier temporaire impossible"
        )

        return try {
            tempFile.writeBytes(smpFile.bytes)
            val importer = SmpImporter(context)
            val importedSong = importer.importSmp(Uri.fromFile(tempFile))
                ?: return BackupBundleSmpImportResult.Failure(
                    reason = importer.lastFailureReason
                )

            BackupBundleSmpImportResult.Success(
                importedSong = BackupBundleImportedSong(
                    bundleSongId = smpFile.songId,
                    importedSongId = importedSong.id,
                    storageFolder = importedSong.storageFolder
                )
            )
        } catch (t: Throwable) {
            BackupBundleSmpImportResult.Failure(
                reason = t.message
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun createTempSmpFile(
        context: Context,
        songId: String
    ): File? {
        return runCatching {
            File.createTempFile(
                "bundle_${songId.ifBlank { "song" }}_",
                ".smp",
                context.cacheDir
            )
        }.getOrNull()
    }

    private fun rollbackImportedSong(importedSong: BackupBundleImportedSong) {
        val storageFolder = importedSong.storageFolder ?: return
        val targetDir = File(storageFolder)
        if (!targetDir.exists()) return
        val deleted = runCatching { targetDir.deleteRecursively() }.getOrDefault(false)
        if (!deleted) {
            Log.w(
                TAG,
                "BUNDLE_IMPORT rollback_failed bundleSongId=${importedSong.bundleSongId} importedSongId=${importedSong.importedSongId} dir=$storageFolder"
            )
        }
    }
}
