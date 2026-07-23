package com.patrick.lrcreader.core.waveform

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveformPeaksCache {
    private const val PERF_TAG = "WAVEFORM_PERF"
    private const val CACHE_DIR_NAME = "waveforms"
    private const val MAX_CACHE_FILES = 200
    private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L

    suspend fun getOrCompute(
        context: Context,
        uri: Uri,
        targetPoints: Int,
        compute: suspend () -> List<Float>
    ): List<Float> = getOrCompute(
        context = context,
        uri = uri,
        targetPoints = targetPoints,
        durationMs = null,
        compute = compute
    )

    suspend fun getOrCompute(
        context: Context,
        uri: Uri,
        targetPoints: Int,
        durationMs: Int? = null,
        cacheVariant: String? = null,
        compute: suspend () -> List<Float>
    ): List<Float> {
        val startedAtMs = SystemClock.elapsedRealtime()
        val cacheFile = cacheFile(context, uri, targetPoints, durationMs, cacheVariant)
        readPeaks(cacheFile)?.let { peaks ->
            Log.i(
                PERF_TAG,
                "cache_hit uri=$uri targetPoints=$targetPoints variant=$cacheVariant points=${peaks.size} " +
                    "durationMs=${SystemClock.elapsedRealtime() - startedAtMs}"
            )
            return peaks
        }

        Log.i(
            PERF_TAG,
            "cache_miss uri=$uri targetPoints=$targetPoints variant=$cacheVariant"
        )
        val computed = compute()
        withContext(Dispatchers.IO) {
            writePeaks(cacheFile, computed)
            pruneIfNeeded(cacheFile.parentFile)
        }
        Log.i(
            PERF_TAG,
            "cache_store uri=$uri targetPoints=$targetPoints variant=$cacheVariant points=${computed.size} " +
                "durationMs=${SystemClock.elapsedRealtime() - startedAtMs}"
        )
        return computed
    }

    fun invalidate(
        context: Context,
        uri: Uri,
        targetPoints: Int
    ) = invalidate(
        context = context,
        uri = uri,
        targetPoints = targetPoints,
        durationMs = null
    )

    fun invalidate(
        context: Context,
        uri: Uri,
        targetPoints: Int,
        durationMs: Int? = null
    ) {
        val file = cacheFile(context, uri, targetPoints, durationMs, cacheVariant = null)
        runCatching { if (file.exists()) file.delete() }
    }

    private fun cacheFile(
        context: Context,
        uri: Uri,
        targetPoints: Int,
        durationMs: Int?,
        cacheVariant: String?
    ): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val baseKey = "${uri}|${targetPoints}|${durationMs ?: -1}"
        val key = if (cacheVariant.isNullOrBlank()) {
            baseKey
        } else {
            "$baseKey|$cacheVariant"
        }
        val hash = sha1Hex(key)
        return File(dir, "$hash.bin")
    }

    private suspend fun readPeaks(file: File): List<Float>? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 4L) return@withContext null
        runCatching {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int
            if (count < 0) throw IllegalStateException("Negative peak count")
            val expectedSize = 4 + count * 4
            if (bytes.size != expectedSize) throw IllegalStateException("Corrupted cache size")
            val peaks = ArrayList<Float>(count)
            repeat(count) { peaks += buffer.float }
            peaks
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
    }

    private fun writePeaks(file: File, peaks: List<Float>) {
        runCatching {
            file.parentFile?.mkdirs()
            val buffer = ByteBuffer
                .allocate(4 + peaks.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(peaks.size)
            peaks.forEach { buffer.putFloat(it) }
            file.writeBytes(buffer.array())
        }
    }

    private fun pruneIfNeeded(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles()?.filter { it.isFile }?.toMutableList() ?: return
        if (files.isEmpty()) return

        var totalBytes = files.sumOf { it.length().coerceAtLeast(0L) }
        var remainingFiles = files.size
        if (remainingFiles <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) return

        files.sortBy { it.lastModified() }
        for (file in files) {
            if (remainingFiles <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) break
            val len = file.length().coerceAtLeast(0L)
            if (file.delete()) {
                totalBytes = (totalBytes - len).coerceAtLeast(0L)
                remainingFiles = (remainingFiles - 1).coerceAtLeast(0)
            }
        }
    }

    private fun sha1Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
