package com.patrick.lrcreader.core.backup

import android.content.Context
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SongUnit
import java.io.File

sealed interface BackupBundleExportBuildResult {
    data class Success(
        val payload: BackupBundlePayload
    ) : BackupBundleExportBuildResult

    data class MissingReferencedSongs(
        val songIds: List<String>
    ) : BackupBundleExportBuildResult

    data class SongExportFailed(
        val songIds: List<String>
    ) : BackupBundleExportBuildResult
}

object BackupBundleExporter {

    fun buildManualBundlePayload(
        stateJson: String,
        preflight: BackupBundleSmpExportPreflight,
        exportSongToSmpBytes: (SongUnit) -> ByteArray?
    ): BackupBundleExportBuildResult {
        if (!preflight.isExportAllowed) {
            return BackupBundleExportBuildResult.MissingReferencedSongs(
                songIds = preflight.missingSongIds
            )
        }

        val smpFiles = mutableListOf<BackupBundleSmpFile>()
        val failedSongIds = mutableListOf<String>()

        preflight.resolvedSongs.forEach { song ->
            val smpBytes = exportSongToSmpBytes(song)
            if (smpBytes == null || smpBytes.isEmpty()) {
                failedSongIds += song.id
            } else {
                smpFiles += BackupBundleSmpFile(
                    songId = song.id,
                    entryName = "$BACKUP_BUNDLE_DEFAULT_SMP_DIR/${song.id}.smp",
                    bytes = smpBytes
                )
            }
        }

        if (failedSongIds.isNotEmpty()) {
            return BackupBundleExportBuildResult.SongExportFailed(
                songIds = failedSongIds
            )
        }

        return BackupBundleExportBuildResult.Success(
            payload = BackupBundlePayload(
                stateJson = stateJson,
                smpFiles = smpFiles
            )
        )
    }

    fun buildManualBundlePayload(
        context: Context,
        lastPlayer: BackupManager.LastPlayed?,
        libraryFolders: List<String>
    ): BackupBundleExportBuildResult {
        val playlists = PlaylistRepository.getPlaylists().associateWith { playlistName ->
            PlaylistRepository.getAllSongsRaw(playlistName)
        }
        val preflight = BackupBundlePlanner.buildSmpExportPreflight(context, playlists)
        val stateJson = BackupManager.exportState(
            context = context,
            lastPlayer = lastPlayer,
            libraryFolders = libraryFolders
        )

        return buildManualBundlePayload(
            stateJson = stateJson,
            preflight = preflight,
            exportSongToSmpBytes = { song ->
                exportSongUnitToBundleBytes(context, song)
            }
        )
    }

    private fun exportSongUnitToBundleBytes(
        context: Context,
        song: SongUnit
    ): ByteArray? {
        val cacheSmpFile = SmpExporter.exportSongUnitToCacheSmp(context, song) ?: return null
        return runCatching {
            cacheSmpFile.readBytes()
        }.getOrNull().also {
            runCatching { cacheSmpFile.delete() }
            deletePartFileIfPresent(cacheSmpFile)
        }
    }

    private fun deletePartFileIfPresent(cacheSmpFile: File) {
        val partFile = File(cacheSmpFile.parentFile, "${cacheSmpFile.name}.part")
        if (partFile.exists()) {
            runCatching { partFile.delete() }
        }
    }
}
