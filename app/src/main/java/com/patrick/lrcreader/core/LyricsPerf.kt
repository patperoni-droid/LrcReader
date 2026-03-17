package com.patrick.lrcreader.core

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object LyricsPerf {

    private const val TAG = "PERF_LYRICS"

    private data class Trace(
        val id: Long,
        val startedAtMs: Long,
        val source: String,
        val playlistName: String?
    )

    private val nextTraceId = AtomicLong(1L)
    private val traces = ConcurrentHashMap<String, Trace>()

    fun startOpen(trackUriString: String, source: String, playlistName: String? = null) {
        if (trackUriString.isBlank()) return
        traces[trackUriString] = Trace(
            id = nextTraceId.getAndIncrement(),
            startedAtMs = SystemClock.elapsedRealtime(),
            source = source,
            playlistName = playlistName
        )
        log(trackUriString, "open_click", "sourceOverride=$source playlistOverride=$playlistName")
    }

    fun mark(trackUriString: String?, stage: String, extras: String = "") {
        log(trackUriString, stage, extras)
    }

    private fun log(trackUriString: String?, stage: String, extras: String) {
        val safeUri = trackUriString?.takeIf { it.isNotBlank() }
        val trace = safeUri?.let { traces[it] }
        val elapsedMs = trace?.let { SystemClock.elapsedRealtime() - it.startedAtMs } ?: -1L
        val suffix = extras.trim().takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        Log.d(
            TAG,
            "stage=$stage traceId=${trace?.id ?: -1} elapsedMs=$elapsedMs uri=$safeUri source=${trace?.source ?: "na"} playlist=${trace?.playlistName ?: "null"} thread=${Thread.currentThread().name}$suffix"
        )
    }
}
