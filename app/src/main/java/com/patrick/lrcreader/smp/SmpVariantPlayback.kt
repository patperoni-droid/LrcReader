package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import com.patrick.lrcreader.core.EditSoundPrefs
import com.patrick.lrcreader.core.TrackPitchPrefs
import com.patrick.lrcreader.core.TrackTempoPrefs
import com.patrick.lrcreader.core.TrackVolumePrefs
import com.patrick.lrcreader.core.buildSmpItem
import org.json.JSONObject
import java.io.File

internal object SmpVariantPlayback {
    private const val MIN_TEMPO = 0.5f
    private const val MAX_TEMPO = 2.0f
    private const val MIN_PITCH_SEMI = -6
    private const val MAX_PITCH_SEMI = 6
    private const val MIN_VOLUME_DB = -24
    private const val MAX_VOLUME_DB = 6

    fun initialProfileFromParent(context: Context, parent: SongUnit): SmpConfig.PlaybackConfig {
        return completeProfile(SmpConfig.fromSongUnit(context, parent).playback)
    }

    fun resolveProfile(
        context: Context,
        variant: SongUnit,
        parent: SongUnit
    ): SmpConfig.PlaybackConfig? {
        readExplicitProfile(variant)?.let { return completeProfile(it) }

        val identity = buildSmpItem(variant.id)
        val legacyTrim = EditSoundPrefs.get(context, Uri.parse(identity))
        val legacyTempo = TrackTempoPrefs.getTempo(context, identity)
        val legacyPitch = TrackPitchPrefs.getSemi(context, identity)
        val legacyVolume = TrackVolumePrefs.getDb(context, identity)
        if (legacyTrim == null && legacyTempo == null && legacyPitch == null && legacyVolume == null) {
            return null
        }

        val parentProfile = initialProfileFromParent(context, parent)
        return sanitize(
            SmpConfig.PlaybackConfig(
                trimStartMs = legacyTrim?.startMs?.toLong() ?: parentProfile.trimStartMs,
                trimEndMs = legacyTrim?.endMs?.toLong() ?: parentProfile.trimEndMs,
                tempo = legacyTempo ?: parentProfile.tempo,
                pitchSemi = legacyPitch ?: parentProfile.pitchSemi,
                volumeDb = legacyVolume ?: parentProfile.volumeDb,
                volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
            )
        )
    }

    fun baseProfileForWrite(context: Context, configFile: File): SmpConfig.PlaybackConfig? {
        readExplicitProfile(configFile)?.let { return completeProfile(it) }
        val configJson = runCatching {
            JSONObject(configFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null
        val sourceSongId = configJson.optJSONObject("arrangementVariant")
            ?.optString("sourceSongId")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return SmpConfig.fromJsonOrNull(configJson.toString())?.playback
        val variantId = configJson.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val scanner = SmpLibraryScanner(context)
        val variant = scanner.findSongById(variantId) ?: return null
        val parent = scanner.findSongById(sourceSongId) ?: return null
        return resolveProfile(context, variant, parent) ?: initialProfileFromParent(context, parent)
    }

    fun mergeProfileUpdate(
        context: Context,
        configFile: File,
        update: (SmpConfig.PlaybackConfig?) -> SmpConfig.PlaybackConfig?
    ): String? {
        return runCatching {
            val configJson = JSONObject(configFile.readText(Charsets.UTF_8))
            val isVariant = configJson.optJSONObject("arrangementVariant") != null
            val currentConfig = SmpConfig.fromJsonOrNull(configJson.toString())
                ?: return null
            val current = if (isVariant) {
                baseProfileForWrite(context, configFile) ?: return null
            } else {
                currentConfig.playback
            }
            val next = update(current)
            if (isVariant) {
                configJson.put("playback", encode(next ?: completeProfile(null)))
            } else {
                val normalized = next?.let { profile ->
                    SmpConfig.PlaybackConfig.fromStoredValues(
                        startMs = profile.trimStartMs,
                        endMs = profile.trimEndMs,
                        tempo = profile.tempo,
                        pitchSemi = profile.pitchSemi,
                        volumeDb = profile.volumeDb,
                        volumeSource = profile.volumeSource,
                        lufsMeasured = profile.lufsMeasured,
                        lufsTarget = profile.lufsTarget,
                        lufsAutoDb = profile.lufsAutoDb,
                        lufsManualDb = profile.lufsManualDb
                    )
                }
                return@runCatching currentConfig.copy(playback = normalized).toJsonString()
            }
            configJson.toString(2)
        }.getOrNull()
    }

    fun isVariantConfig(configFile: File): Boolean {
        if (!configFile.isFile) return false
        return runCatching {
            JSONObject(configFile.readText(Charsets.UTF_8))
                .optJSONObject("arrangementVariant") != null
        }.getOrDefault(false)
    }

    fun readExplicitProfile(songUnit: SongUnit): SmpConfig.PlaybackConfig? {
        val configFile = songUnit.storageFolder
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.let { File(it, "config.json") }
            ?: return null
        return readExplicitProfile(configFile)
    }

    fun readExplicitProfile(configFile: File): SmpConfig.PlaybackConfig? {
        if (!configFile.isFile) return null
        return runCatching read@{
            val configJson = JSONObject(configFile.readText(Charsets.UTF_8))
            if (configJson.optJSONObject("arrangementVariant") == null) return@read null
            val playbackJson = configJson.optJSONObject("playback") ?: return@read null
            decode(playbackJson)
        }.getOrNull()
    }

    fun encode(profile: SmpConfig.PlaybackConfig): JSONObject {
        val safe = completeProfile(profile)
        return JSONObject()
            .put("trimStartMs", safe.trimStartMs ?: 0L)
            .apply {
                safe.trimEndMs?.let { put("trimEndMs", it) }
            }
            .put("tempo", (safe.tempo ?: 1f).toDouble())
            .put("pitchSemi", safe.pitchSemi ?: 0)
            .put("volumeDb", safe.volumeDb ?: 0)
    }

    fun decode(json: JSONObject): SmpConfig.PlaybackConfig {
        val trimStartMs = json.optLongValue("trimStartMs")?.coerceAtLeast(0L) ?: 0L
        val trimEndMs = json.optLongValue("trimEndMs")
            ?.coerceAtLeast(0L)
            ?.takeIf { it > 0L }
        val tempo = json.optFloatValue("tempo")?.coerceIn(MIN_TEMPO, MAX_TEMPO) ?: 1f
        val pitchSemi = json.optIntValue("pitchSemi")
            ?.coerceIn(MIN_PITCH_SEMI, MAX_PITCH_SEMI)
            ?: 0
        val volumeDb = json.optIntValue("volumeDb")
            ?.coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB)
            ?: 0
        return SmpConfig.PlaybackConfig(
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            tempo = tempo,
            pitchSemi = pitchSemi,
            volumeDb = volumeDb,
            volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
        )
    }

    private fun completeProfile(profile: SmpConfig.PlaybackConfig?): SmpConfig.PlaybackConfig {
        return sanitize(
            SmpConfig.PlaybackConfig(
                trimStartMs = profile?.trimStartMs ?: 0L,
                trimEndMs = profile?.trimEndMs,
                tempo = profile?.tempo ?: 1f,
                pitchSemi = profile?.pitchSemi ?: 0,
                volumeDb = profile?.volumeDb ?: 0,
                volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
            )
        )
    }

    private fun sanitize(profile: SmpConfig.PlaybackConfig): SmpConfig.PlaybackConfig {
        return profile.copy(
            trimStartMs = profile.trimStartMs?.coerceAtLeast(0L) ?: 0L,
            trimEndMs = profile.trimEndMs?.coerceAtLeast(0L)?.takeIf { it > 0L },
            tempo = (profile.tempo ?: 1f).coerceIn(MIN_TEMPO, MAX_TEMPO),
            pitchSemi = (profile.pitchSemi ?: 0).coerceIn(MIN_PITCH_SEMI, MAX_PITCH_SEMI),
            volumeDb = (profile.volumeDb ?: 0).coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB),
            volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
            lufsMeasured = null,
            lufsTarget = null,
            lufsAutoDb = null,
            lufsManualDb = null
        )
    }

    private fun JSONObject.optLongValue(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optIntValue(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optFloatValue(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }
}
