package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpVariantPlayback
import java.io.File

object TrackTempoPrefs {

    private const val PREF = "track_tempo_prefs"
    private const val TAG = "TrackTempoPrefs"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val SMP_CONFIG_FILE_NAME = "config.json"

    fun getTempo(context: Context, uri: String): Float? {
        resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            readSmpTempo(configFile)?.let { return it }
        }

        val fromJson = TrackSettingsStore.getTempoByUri(context, uri)
        if (fromJson != null) return fromJson

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getFloat(uri, -1f)
        return if (raw < 0f) null else raw
    }

    fun saveTempo(context: Context, uri: String, tempo: Float) {
        val smpSaved = resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            writeSmpTempo(context, configFile, tempo)
        }
        if (smpSaved == true) {
            return
        }

        val jsonOk = TrackSettingsStore.saveTempoByUri(context, uri, tempo)
        if (!jsonOk) {
            Log.w(TAG, "saveTempo: JSON write skipped/failed, fallback prefs only")
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putFloat(uri, tempo)
            .apply()
    }

    private fun readSmpTempo(configFile: File): Float? {
        if (!configFile.isFile) {
            return null
        }

        return runCatching {
            SmpVariantPlayback.readExplicitProfile(configFile)?.tempo?.let { return@runCatching it }
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?.playback
                ?.tempo
        }.getOrElse { error ->
            Log.w(TAG, "getTempo: SMP config read failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun writeSmpTempo(context: Context, configFile: File, tempo: Float): Boolean {
        val songDir = configFile.parentFile ?: return false
        val tmpFile = File(songDir, "$SMP_CONFIG_FILE_NAME.tmp")

        return runCatching {
            val rawJson = SmpVariantPlayback.mergeProfileUpdate(context, configFile) { current ->
                current?.copy(tempo = tempo) ?: SmpConfig.PlaybackConfig.fromStoredValues(
                startMs = null,
                endMs = null,
                tempo = tempo,
                pitchSemi = null,
                volumeDb = null
                )
            } ?: return false

            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (configFile.exists() && !configFile.delete()) {
                Log.w(TAG, "saveTempo: SMP config delete failed path=${configFile.absolutePath}")
            }
            if (!tmpFile.renameTo(configFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                configFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "saveTempo: SMP config write failed path=${configFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun resolveInternalSmpConfigFile(context: Context, uriString: String): File? {
        getSmpSongId(uriString)?.let { songId ->
            return File(File(File(context.filesDir, TRACKS_DIR_NAME), songId), SMP_CONFIG_FILE_NAME)
                .takeIf(File::isFile)
        }
        val trackUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (trackUri.scheme != "file") return null

        val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return null
        val audioFile = File(audioPath)
        if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
            return null
        }

        val songDir = runCatching { audioFile.parentFile?.canonicalFile }.getOrNull() ?: return null
        val tracksRoot = runCatching {
            File(context.filesDir, TRACKS_DIR_NAME).canonicalFile
        }.getOrNull() ?: return null
        if (songDir.parentFile?.canonicalFile != tracksRoot) {
            return null
        }

        return File(songDir, SMP_CONFIG_FILE_NAME).takeIf { it.isFile }
    }
}
