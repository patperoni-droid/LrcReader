package com.patrick.lrcreader.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object ArrangementSourceWavCache {
    private const val TAG = "ARR_STRUCTURE_WAV"
    private const val CACHE_DIR_NAME = "arrangement_pcm"

    suspend fun ensureSourceWav(
        context: Context,
        songId: String,
        sourceUri: Uri
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val safeSongId = songId.ifBlank { "song" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val signature = buildSignature(sourceUri)
        val targetFile = File(cacheDir, "${safeSongId}_${signature}.wav")

        cleanupSongCaches(cacheDir, safeSongId, targetFile.name)

        if (targetFile.isFile && targetFile.length() > 44L) {
            Log.d(TAG, "CACHE_HIT path=${targetFile.absolutePath}")
            return@withContext targetFile
        }

        Log.d(TAG, "CACHE_BUILD_START songId=$songId source=$sourceUri")
        runCatching { targetFile.delete() }
        ArrangementWavRenderer.decodeSourceToWav(
            audioPath = requireLocalAudioPath(sourceUri),
            outputFile = targetFile
        )
        Log.d(
            TAG,
            "CACHE_BUILD_OK path=${targetFile.absolutePath} fileSize=${targetFile.length()}"
        )
        targetFile
    }

    private fun cleanupSongCaches(
        cacheDir: File,
        safeSongId: String,
        keepFileName: String
    ) {
        cacheDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("${safeSongId}_") &&
                    file.name.endsWith(".wav") &&
                    file.name != keepFileName
            }
            ?.forEach { staleFile ->
                runCatching { staleFile.delete() }
            }
    }

    private fun buildSignature(sourceUri: Uri): String {
        val localFile = sourceUri.path
            ?.takeIf { sourceUri.scheme == "file" && it.isNotBlank() }
            ?.let(::File)
        val rawSignature = buildString {
            append(sourceUri.toString())
            append('|')
            append(localFile?.length() ?: -1L)
            append('|')
            append(localFile?.lastModified() ?: -1L)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(rawSignature.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(16)
    }

    private fun requireLocalAudioPath(sourceUri: Uri): String {
        return sourceUri.path?.takeIf { sourceUri.scheme == "file" && it.isNotBlank() }
            ?: error("Arrangement WAV cache requires a local file source: $sourceUri")
    }
}
