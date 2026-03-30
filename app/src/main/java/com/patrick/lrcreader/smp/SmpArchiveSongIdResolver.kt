package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object SmpArchiveSongIdResolver {

    private const val TAG = "SMP_ARCHIVE_ID"
    private const val TRACE_TAG = "SMP_TRACE"

    fun readStableSongId(context: Context, uri: Uri): String? {
        traceInfo("step=archive_song_id_read_start uri=$uri")
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readStableSongId(input)
            } ?: run {
                traceWarn("step=archive_song_id_read_failed uri=$uri reason=input_stream_null")
                null
            }
        }
        return result.getOrElse { error ->
            Log.w(TAG, "Lecture songId stable impossible pour $uri", error)
            traceWarn("step=archive_song_id_read_failed uri=$uri reason=exception", error)
            null
        }.also { stableSongId ->
            traceInfo(
                "step=archive_song_id_read_done uri=$uri songId=${stableSongId ?: "invalid_or_absent"}"
            )
        }
    }

    internal fun readStableSongId(inputStream: InputStream): String? {
        return ZipInputStream(inputStream).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                try {
                    val entryName = entry.name
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .trim()
                    if (!entry.isDirectory && entryName.equals("config.json", ignoreCase = true)) {
                        val configJson = String(
                            readCurrentZipEntryBytes(zipInputStream),
                            Charsets.UTF_8
                        )
                        val config = SmpConfig.fromJsonOrNull(configJson)
                        val stableSongId = sanitizeSongId(config?.id)
                        traceInfo(
                            "step=archive_config_found rawId=${config?.id ?: "null"} songId=${stableSongId ?: "invalid_or_absent"}"
                        )
                        return stableSongId
                    }
                } finally {
                    runCatching { zipInputStream.closeEntry() }
                }
                entry = zipInputStream.nextEntry
            }
            traceInfo("step=archive_config_missing")
            null
        }
    }

    internal fun sanitizeSongId(rawId: String?): String? {
        return rawId
            ?.trim()
            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            ?.trim('_', '.', '-')
            ?.ifBlank { null }
    }

    private fun readCurrentZipEntryBytes(zipInputStream: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = zipInputStream.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun traceInfo(message: String) {
        runCatching { Log.i(TRACE_TAG, message) }
    }

    private fun traceWarn(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.w(TRACE_TAG, message, error)
            } else {
                Log.w(TRACE_TAG, message)
            }
        }
    }
}
