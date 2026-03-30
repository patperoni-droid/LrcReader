package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.config.TitleAliasesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SmpAutoMigrationResult(
    val song: SongUnit,
    val trackUriString: String
)

class SmpAutoMigration(private val context: Context) {

    companion object {
        private const val TAG = "SMP_AUTO_MIGRATE"
    }

    private val converter by lazy(LazyThreadSafetyMode.NONE) { SmpConverter(context) }
    private val secureImportPipeline by lazy(LazyThreadSafetyMode.NONE) { SmpSecureImportPipeline(context) }

    suspend fun migrateLegacyTrack(trackUriString: String): SmpAutoMigrationResult? =
        migrateLegacyTrackInternal(trackUriString, isolatedDocument = false)

    suspend fun migrateLegacyTrackFromIsolatedDocument(trackUriString: String): SmpAutoMigrationResult? =
        migrateLegacyTrackInternal(trackUriString, isolatedDocument = true)

    fun isInternalSmpTrackUri(trackUriString: String): Boolean {
        return isInternalSmpTrack(trackUriString)
    }

    private suspend fun migrateLegacyTrackInternal(
        trackUriString: String,
        isolatedDocument: Boolean
    ): SmpAutoMigrationResult? = withContext(Dispatchers.IO) {
        if (trackUriString.isBlank()) {
            return@withContext null
        }
        if (isInternalSmpTrack(trackUriString)) {
            return@withContext null
        }

        val sourceUri = runCatching { Uri.parse(trackUriString) }.getOrNull()
            ?: return@withContext null
        if (sourceUri.scheme != "file" && sourceUri.scheme != "content") {
            Log.w(TAG, "Migration SMP ignorée: URI non supportée trackUri=$trackUriString")
            return@withContext null
        }

        val tempArchiveFile = if (isolatedDocument) {
            converter.convertSingleToTempArchive(sourceUri).getOrNull()
        } else {
            null
        }
        val archiveUri = if (isolatedDocument) {
            tempArchiveFile?.let { Uri.fromFile(it) }
        } else {
            converter.convertSingle(sourceUri).getOrNull()
        }
        if (archiveUri == null) {
            Log.w(TAG, "Migration SMP échouée à la conversion trackUri=$trackUriString isolated=$isolatedDocument")
            return@withContext null
        }

        try {
            val importResult = secureImportPipeline.import(archiveUri)
            val importedSong = importResult.importedSong
            if (importedSong == null) {
                Log.w(
                    TAG,
                    "Migration SMP échouée à l'import trackUri=$trackUriString reason=${importResult.failureReason} isolated=$isolatedDocument"
                )
                return@withContext null
            }

            val audioPath = importedSong.audioPath?.takeIf { it.isNotBlank() }
            if (audioPath == null) {
                Log.w(TAG, "Migration SMP sans audio résolu songId=${importedSong.id} trackUri=$trackUriString")
                return@withContext null
            }

            val resolvedTrackUri = Uri.fromFile(File(audioPath)).toString()
            runCatching {
                TitleAliasesStore.setTitleForTrack(context, resolvedTrackUri, importedSong.title)
            }.onFailure { error ->
                Log.w(
                    TAG,
                    "Alias titre SMP non enregistré songId=${importedSong.id} uri=$resolvedTrackUri",
                    error
                )
            }

            Log.i(
                TAG,
                "Migration SMP réussie trackUri=$trackUriString songId=${importedSong.id} uri=$resolvedTrackUri isolated=$isolatedDocument"
            )

            SmpAutoMigrationResult(
                song = importedSong,
                trackUriString = resolvedTrackUri
            )
        } finally {
            tempArchiveFile?.delete()
        }
    }

    private fun isInternalSmpTrack(trackUriString: String): Boolean {
        val trackUri = runCatching { Uri.parse(trackUriString) }.getOrNull() ?: return false
        if (trackUri.scheme != "file") return false

        val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return false
        val audioFile = File(audioPath)
        if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
            return false
        }

        val songDir = runCatching { audioFile.parentFile?.canonicalFile }.getOrNull() ?: return false
        val tracksRoot = runCatching { File(context.filesDir, "tracks").canonicalFile }.getOrNull() ?: return false
        if (songDir.parentFile?.canonicalFile != tracksRoot) {
            return false
        }

        return File(songDir, "config.json").isFile
    }
}
