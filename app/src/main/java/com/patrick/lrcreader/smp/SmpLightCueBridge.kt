package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.light.LightCue
import java.io.File
import java.util.Locale

object SmpLightCueBridge {

    private const val TRACKS_DIR_NAME = "tracks"
    private const val TRACE_TAG = "LIGHT_CUE_TRACE"

    private val cacheLock = Any()
    private val lightCuesBySongDir: MutableMap<String, List<LightCue>> = mutableMapOf()

    fun getRuntimeCues(
        context: Context,
        trackUriString: String?
    ): List<LightCue>? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val cues = loadLightCues(track).sortedBy { cue -> cue.timeMs }
        Log.d(
            TRACE_TAG,
            "BRIDGE_RUNTIME_CUES track=${track.songDir.name} count=${cues.size}"
        )
        return cues
    }

    fun getCueAtTime(
        context: Context,
        trackUriString: String?,
        timeMs: Long
    ): LightCue? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        return loadLightCues(track)
            .firstOrNull { cue -> cue.timeMs == timeMs.coerceAtLeast(0L) }
    }

    fun upsertCueAtTime(
        context: Context,
        trackUriString: String?,
        cue: LightCue
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val targetTimeMs = cue.timeMs.coerceAtLeast(0L)
        val updated = loadLightCues(track)
            .filterNot { existing -> existing.timeMs == targetTimeMs } + cue.copy(timeMs = targetTimeMs)
        return saveLightCues(track, updated)
    }

    fun deleteCueAtTime(
        context: Context,
        trackUriString: String?,
        timeMs: Long
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val targetTimeMs = timeMs.coerceAtLeast(0L)
        val updated = loadLightCues(track)
            .filterNot { cue -> cue.timeMs == targetTimeMs }
        return saveLightCues(track, updated)
    }

    fun saveCuesBatch(
        context: Context,
        trackUriString: String?,
        cues: List<LightCue>,
        replaceExisting: Boolean
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val normalizedGenerated = cues
            .asSequence()
            .map { cue ->
                cue.copy(
                    timeMs = cue.timeMs.coerceAtLeast(0L),
                    intensity = cue.intensity.coerceIn(0f, 1f),
                    fadeMs = cue.fadeMs.coerceAtLeast(0L)
                )
            }
            .sortedBy { cue -> cue.timeMs }
            .distinctBy { cue -> cue.timeMs }
            .toList()

        val finalCues = if (replaceExisting) {
            normalizedGenerated
        } else {
            val existing = loadLightCues(track)
            val existingTimeMs = existing
                .asSequence()
                .map { cue -> cue.timeMs.coerceAtLeast(0L) }
                .toSet()
            existing + normalizedGenerated.filterNot { cue -> cue.timeMs in existingTimeMs }
        }
        return saveLightCues(track, finalCues)
    }

    fun invalidateSongDir(songDir: File?) {
        val key = runCatching { songDir?.canonicalFile?.absolutePath }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return
        synchronized(cacheLock) {
            lightCuesBySongDir.remove(key)
        }
        Log.d(TRACE_TAG, "BRIDGE_INVALIDATE songDir=$key")
    }

    private fun loadLightCues(track: SmpTrack): List<LightCue> {
        synchronized(cacheLock) {
            lightCuesBySongDir[track.songDir.absolutePath]?.let { cached ->
                return cached
            }
        }

        val loaded = SmpLightCueStore.read(track.songDir)
        synchronized(cacheLock) {
            lightCuesBySongDir[track.songDir.absolutePath] = loaded
        }
        return loaded
    }

    private fun saveLightCues(track: SmpTrack, cues: List<LightCue>): Boolean {
        val normalized = cues.sortedBy { cue -> cue.timeMs }
        val saved = SmpLightCueStore.write(track.songDir, normalized)
        if (saved) {
            synchronized(cacheLock) {
                lightCuesBySongDir[track.songDir.absolutePath] = normalized
            }
        }
        return saved
    }

    private fun resolveSmpTrack(context: Context, trackUriString: String?): SmpTrack? {
        val rawUri = trackUriString?.trim().orEmpty()
        if (rawUri.isBlank()) {
            return null
        }

        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) {
            return null
        }

        val audioFile = uri.path?.let(::File)?.canonicalFile ?: return null
        val parentDir = audioFile.parentFile ?: return null
        val fileName = audioFile.name.lowercase(Locale.ROOT)
        if (!parentDir.isDirectory || !fileName.startsWith("audio.")) {
            return null
        }

        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME).canonicalFile
        val songDir = parentDir.canonicalFile
        if (songDir.parentFile?.canonicalFile != tracksDir) {
            return null
        }

        return SmpTrack(songDir = songDir)
    }

    private data class SmpTrack(
        val songDir: File
    )
}
