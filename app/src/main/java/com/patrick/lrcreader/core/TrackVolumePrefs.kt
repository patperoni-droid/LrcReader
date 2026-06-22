package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.smp.SmpConfig
import java.io.File

object TrackVolumePrefs {
    private const val PREF = "track_volume_prefs"
    private const val TAG = "TrackVolumePrefs"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val SMP_CONFIG_FILE_NAME = "config.json"
    private const val MIN_DB = -24
    private const val MAX_DB = 6

    fun saveDb(
        context: Context,
        uri: String,
        db: Int,
        source: String = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
    ) {
        val safeDb = db.coerceIn(MIN_DB, MAX_DB)
        val smpSaved = resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            writeSmpVolumeDb(configFile, safeDb, source)
        }
        if (smpSaved == true) {
            return
        }

        val jsonOk = TrackSettingsStore.saveVolumeDbByUri(context, uri, safeDb)
        if (!jsonOk) {
            Log.w(TAG, "saveDb: JSON write skipped/failed, fallback prefs only")
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(uri, safeDb)
            .apply()
    }

    fun saveLufsDb(
        context: Context,
        uri: String,
        db: Int,
        measuredLufs: Float,
        targetLufs: Float,
        autoDb: Float,
        manualDb: Int
    ) {
        val safeDb = db.coerceIn(MIN_DB, MAX_DB)
        val smpSaved = resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            writeSmpVolumeDb(
                configFile = configFile,
                db = safeDb,
                source = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS,
                lufsMeasured = measuredLufs,
                lufsTarget = targetLufs,
                lufsAutoDb = autoDb,
                lufsManualDb = manualDb
            )
        }
        if (smpSaved == true) {
            return
        }

        saveDb(
            context = context,
            uri = uri,
            db = safeDb,
            source = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS
        )
    }

    fun getDb(context: Context, uri: String): Int? {
        resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            readSmpVolumeDb(configFile)?.let { return it.coerceIn(MIN_DB, MAX_DB) }
        }

        val fromJson = TrackSettingsStore.getVolumeDbByUri(context, uri)
        if (fromJson != null) return fromJson.coerceIn(MIN_DB, MAX_DB)

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return if (sp.contains(uri)) sp.getInt(uri, 0).coerceIn(MIN_DB, MAX_DB) else null
    }

    fun getSource(context: Context, uri: String): String {
        resolveInternalSmpConfigFile(context, uri)?.let { configFile ->
            readSmpVolumeSource(configFile)?.let { return it }
        }
        return SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
    }

    fun getPlaybackConfig(context: Context, uri: String): SmpConfig.PlaybackConfig? {
        val configFile = resolveInternalSmpConfigFile(context, uri) ?: return null
        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))?.playback
        }.getOrElse { error ->
            Log.w(TAG, "getPlaybackConfig: SMP config read failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun readSmpVolumeDb(configFile: File): Int? {
        if (!configFile.isFile) {
            return null
        }

        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?.playback
                ?.volumeDb
        }.getOrElse { error ->
            Log.w(TAG, "getDb: SMP config read failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun readSmpVolumeSource(configFile: File): String? {
        if (!configFile.isFile) {
            return null
        }

        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?.playback
                ?.volumeSource
                ?: SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
        }.getOrElse { error ->
            Log.w(TAG, "getSource: SMP config read failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun writeSmpVolumeDb(
        configFile: File,
        db: Int,
        source: String,
        lufsMeasured: Float? = null,
        lufsTarget: Float? = null,
        lufsAutoDb: Float? = null,
        lufsManualDb: Int? = null
    ): Boolean {
        val songDir = configFile.parentFile ?: return false
        val tmpFile = File(songDir, "$SMP_CONFIG_FILE_NAME.tmp")

        return runCatching {
            val currentConfig = SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?: return false
            val nextPlayback = SmpConfig.PlaybackConfig.fromStoredValues(
                startMs = currentConfig.playback?.trimStartMs,
                endMs = currentConfig.playback?.trimEndMs,
                tempo = currentConfig.playback?.tempo,
                pitchSemi = currentConfig.playback?.pitchSemi,
                volumeDb = db,
                volumeSource = source,
                lufsMeasured = lufsMeasured ?: currentConfig.playback?.lufsMeasured,
                lufsTarget = lufsTarget ?: currentConfig.playback?.lufsTarget,
                lufsAutoDb = lufsAutoDb ?: currentConfig.playback?.lufsAutoDb,
                lufsManualDb = lufsManualDb ?: currentConfig.playback?.lufsManualDb
            )
            val nextConfig = currentConfig.copy(playback = nextPlayback)
            val rawJson = nextConfig.toJsonString()

            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (configFile.exists() && !configFile.delete()) {
                Log.w(TAG, "saveDb: SMP config delete failed path=${configFile.absolutePath}")
            }
            if (!tmpFile.renameTo(configFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                configFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "saveDb: SMP config write failed path=${configFile.absolutePath}", error)
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
