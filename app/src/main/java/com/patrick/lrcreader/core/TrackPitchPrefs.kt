package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.smp.SmpConfig
import java.io.File

/**
 * Sauvegarde / lecture de la tonalite (en demi-tons) par titre.
 *
 * Exemple : 0 = normal, +2 = +2 demi-tons, -3 = -3 demi-tons.
 */
object TrackPitchPrefs {

    private const val PREFS_NAME = "track_pitch_prefs"
    private const val KEY_PREFIX = "pitch_"
    private const val TAG = "TrackPitchPrefs"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val SMP_CONFIG_FILE_NAME = "config.json"

    fun getSemi(context: Context, uri: String): Int? {
        resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            readSmpPitchSemi(configFile)?.let { return it }
        }

        val fromJson = TrackSettingsStore.getPitchSemiByUri(context, uri)
        if (fromJson != null) return fromJson

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + uri
        if (!prefs.contains(key)) return null
        return prefs.getInt(key, 0)
    }

    fun saveSemi(context: Context, uri: String, semi: Int) {
        val smpSaved = resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            writeSmpPitchSemi(configFile, semi)
        }
        if (smpSaved == true) {
            return
        }

        val jsonOk = TrackSettingsStore.savePitchSemiByUri(context, uri, semi)
        if (!jsonOk) {
            Log.w(TAG, "saveSemi: JSON write skipped/failed, fallback prefs only")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_PREFIX + uri, semi)
            .apply()
    }

    fun clear(context: Context, uri: String) {
        val jsonOk = TrackSettingsStore.clearPitchSemiByUri(context, uri)
        if (!jsonOk) {
            Log.w(TAG, "clear: JSON clear skipped/failed, fallback prefs only")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PREFIX + uri)
            .apply()
    }

    private fun readSmpPitchSemi(configFile: File): Int? {
        if (!configFile.isFile) {
            return null
        }

        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?.playback
                ?.pitchSemi
        }.getOrElse { error ->
            Log.w(TAG, "getSemi: SMP config read failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun writeSmpPitchSemi(configFile: File, semi: Int): Boolean {
        val songDir = configFile.parentFile ?: return false
        val tmpFile = File(songDir, "$SMP_CONFIG_FILE_NAME.tmp")

        return runCatching {
            val currentConfig = SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?: return false
            val nextPlayback = SmpConfig.PlaybackConfig.fromStoredValues(
                startMs = currentConfig.playback?.trimStartMs,
                endMs = currentConfig.playback?.trimEndMs,
                tempo = currentConfig.playback?.tempo,
                pitchSemi = semi,
                volumeDb = currentConfig.playback?.volumeDb,
                volumeSource = currentConfig.playback?.volumeSource
            )
            val nextConfig = currentConfig.copy(playback = nextPlayback)
            val rawJson = nextConfig.toJsonString()

            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (configFile.exists() && !configFile.delete()) {
                Log.w(TAG, "saveSemi: SMP config delete failed path=${configFile.absolutePath}")
            }
            if (!tmpFile.renameTo(configFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                configFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "saveSemi: SMP config write failed path=${configFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun resolveInternalSmpConfigFile(context: Context, uriString: String): File? {
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
