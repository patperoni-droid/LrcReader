package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.EditPrefs
import com.patrick.lrcreader.core.EditSoundPrefs
import com.patrick.lrcreader.core.TrackPitchPrefs
import com.patrick.lrcreader.core.TrackTempoPrefs
import com.patrick.lrcreader.core.TrackVolumePrefs
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class SmpConfig(
    val title: String?,
    val id: String?,
    val files: FilesConfig? = null,
    val playback: PlaybackConfig? = null
) {
    data class FilesConfig(
        val audio: String?,
        val lyrics: String?,
        val chords: String?,
        val annotations: String?,
        val midiCues: String?,
        val dmxCues: String?,
        val prompter: String?
    ) {
        companion object {
            fun fromSongUnit(songUnit: SongUnit): FilesConfig? {
                return FilesConfig(
                    audio = resolveAudioTransportName(songUnit.audioPath),
                    lyrics = resolveFixedTransportName(songUnit.lyricsPath, "lyrics.lrc"),
                    chords = resolveFixedTransportName(songUnit.chordsPath, "chords.lrc"),
                    annotations = resolveFixedTransportName(songUnit.annotationsPath, "annotations.json"),
                    midiCues = resolveFixedTransportName(songUnit.midiPath, "midi_cues.json"),
                    dmxCues = resolveFixedTransportName(songUnit.dmxPath, "dmx_cues.json"),
                    prompter = resolvePrompterTransportName(songUnit.prompterPath)
                ).takeIf { it.hasAnyValue() }
            }
        }

        fun toJsonOrNull(): JSONObject? {
            if (!hasAnyValue()) {
                return null
            }

            return JSONObject().apply {
                audio?.let { put("audio", it) }
                lyrics?.let { put("lyrics", it) }
                chords?.let { put("chords", it) }
                annotations?.let { put("annotations", it) }
                midiCues?.let { put("midiCues", it) }
                dmxCues?.let { put("dmxCues", it) }
                prompter?.let { put("prompter", it) }
            }
        }

        private fun hasAnyValue(): Boolean {
            return audio != null ||
                lyrics != null ||
                chords != null ||
                annotations != null ||
                midiCues != null ||
                dmxCues != null ||
                prompter != null
        }
    }

    data class PlaybackConfig(
        val trimStartMs: Long?,
        val trimEndMs: Long?,
        val tempo: Float? = null,
        val pitchSemi: Int? = null,
        val volumeDb: Int? = null
    ) {
        companion object {
            fun fromStoredValues(
                startMs: Long?,
                endMs: Long?,
                tempo: Float? = null,
                pitchSemi: Int? = null,
                volumeDb: Int? = null
            ): PlaybackConfig? {
                val trimStartMs = startMs?.takeIf { it > 0L }
                val trimEndMs = endMs?.takeIf { it > 0L }
                val playbackTempo = tempo
                    ?.coerceIn(0.5f, 2.0f)
                    ?.takeIf { kotlin.math.abs(it - 1f) > 0.0005f }
                val playbackPitchSemi = pitchSemi
                    ?.coerceIn(-6, 6)
                    ?.takeIf { it != 0 }
                val playbackVolumeDb = volumeDb
                if (trimStartMs == null && trimEndMs == null && playbackTempo == null && playbackPitchSemi == null && playbackVolumeDb == null) {
                    return null
                }
                return PlaybackConfig(
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    tempo = playbackTempo,
                    pitchSemi = playbackPitchSemi,
                    volumeDb = playbackVolumeDb
                )
            }

            fun fromWaveformEdit(startMs: Int, endMs: Int): PlaybackConfig? {
                return fromStoredValues(
                    startMs = startMs.toLong(),
                    endMs = endMs.toLong()
                )
            }
        }

        fun toJsonOrNull(): JSONObject? {
            if (trimStartMs == null && trimEndMs == null && tempo == null && pitchSemi == null && volumeDb == null) {
                return null
            }

            return JSONObject().apply {
                trimStartMs?.let { put("trimStartMs", it) }
                trimEndMs?.let { put("trimEndMs", it) }
                tempo?.let { put("tempo", it.toDouble()) }
                pitchSemi?.let { put("pitchSemi", it) }
                volumeDb?.let { put("volumeDb", it) }
            }
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
            files?.toJsonOrNull()?.let { put("files", it) }
            playback?.toJsonOrNull()?.let { put("playback", it) }
        }
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces)
    }

    companion object {
        private const val TAG = "SmpConfig"
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg")

        fun fromSongUnit(context: Context, songUnit: SongUnit): SmpConfig {
            return SmpConfig(
                title = songUnit.title.takeIf { it.isNotBlank() },
                id = songUnit.id.takeIf { it.isNotBlank() },
                files = FilesConfig.fromSongUnit(songUnit),
                playback = resolvePlaybackForSongUnit(
                    context = context,
                    songUnit = songUnit
                )
            )
        }

        fun fromJson(rawJson: String?): SmpConfig {
            return fromJsonOrNull(rawJson) ?: SmpConfig(title = null, id = null)
        }

        fun fromJsonOrNull(rawJson: String?): SmpConfig? {
            if (rawJson.isNullOrBlank()) {
                Log.e(TAG, "config.json absent ou vide")
                return null
            }

            return try {
                val json = JSONObject(rawJson)
                SmpConfig(
                    title = json.optString("title").trim().ifBlank { null },
                    id = json.optString("id").trim().ifBlank { null },
                    files = parseFiles(json),
                    playback = parsePlayback(json)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Impossible de parser config.json", e)
                null
            }
        }

        private fun parsePlayback(json: JSONObject): PlaybackConfig? {
            val playbackJson = json.optJSONObject("playback") ?: return null
            val trimStartMs = playbackJson.optNonNegativeLongOrNull("trimStartMs")
            val trimEndMs = playbackJson.optNonNegativeLongOrNull("trimEndMs")
            val tempo = playbackJson.optFloatOrNull("tempo")
            val pitchSemi = playbackJson.optIntOrNull("pitchSemi")
            val volumeDb = playbackJson.optIntOrNull("volumeDb")
            return PlaybackConfig.fromStoredValues(
                startMs = trimStartMs,
                endMs = trimEndMs,
                tempo = tempo,
                pitchSemi = pitchSemi,
                volumeDb = volumeDb
            )
        }

        private fun parseFiles(json: JSONObject): FilesConfig? {
            val filesJson = json.optJSONObject("files") ?: return null
            return FilesConfig(
                audio = filesJson.optStringOrNull("audio"),
                lyrics = filesJson.optStringOrNull("lyrics"),
                chords = filesJson.optStringOrNull("chords"),
                annotations = filesJson.optStringOrNull("annotations"),
                midiCues = filesJson.optStringOrNull("midiCues"),
                dmxCues = filesJson.optStringOrNull("dmxCues"),
                prompter = filesJson.optStringOrNull("prompter")
            ).takeIf {
                it.audio != null ||
                    it.lyrics != null ||
                    it.chords != null ||
                    it.annotations != null ||
                    it.midiCues != null ||
                    it.dmxCues != null ||
                    it.prompter != null
            }
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) {
                return null
            }

            return optString(key).trim().ifBlank { null }
        }

        private fun JSONObject.optNonNegativeLongOrNull(key: String): Long? {
            if (!has(key) || isNull(key)) {
                return null
            }

            val rawValue = opt(key)
            val parsed = when (rawValue) {
                is Number -> rawValue.toLong()
                is String -> rawValue.toLongOrNull()
                else -> null
            } ?: return null

            return parsed.takeIf { it >= 0L }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? {
            if (!has(key) || isNull(key)) {
                return null
            }

            return when (val rawValue = opt(key)) {
                is Number -> rawValue.toInt()
                is String -> rawValue.toIntOrNull()
                else -> null
            }
        }

        private fun JSONObject.optFloatOrNull(key: String): Float? {
            if (!has(key) || isNull(key)) {
                return null
            }

            return when (val rawValue = opt(key)) {
                is Number -> rawValue.toFloat()
                is String -> rawValue.toFloatOrNull()
                else -> null
            }
        }

        fun readPlaybackFromSongUnit(songUnit: SongUnit): PlaybackConfig? {
            val configFile = songUnit.storageFolder
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isDirectory }
                ?.let { File(it, "config.json") }
                ?.takeIf { it.isFile }
                ?: return null
            return runCatching {
                fromJsonOrNull(configFile.readText(Charsets.UTF_8))?.playback
            }.getOrNull()
        }

        fun writeTrimPlaybackToSongUnit(
            songUnit: SongUnit,
            startMs: Int,
            endMs: Int
        ): Boolean {
            val songDir = songUnit.storageFolder
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isDirectory }
                ?: return false
            val configFile = File(songDir, "config.json")
            if (!configFile.isFile) return false

            val tmpFile = File(songDir, "config.json.tmp")
            return runCatching {
                val currentConfig = fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                    ?: SmpConfig(
                        title = songUnit.title.takeIf { it.isNotBlank() },
                        id = songUnit.id.takeIf { it.isNotBlank() },
                        files = FilesConfig.fromSongUnit(songUnit)
                    )
                val nextPlayback = PlaybackConfig.fromStoredValues(
                    startMs = startMs.toLong(),
                    endMs = endMs.toLong(),
                    tempo = currentConfig.playback?.tempo,
                    pitchSemi = currentConfig.playback?.pitchSemi,
                    volumeDb = currentConfig.playback?.volumeDb
                )
                val nextConfig = currentConfig.copy(
                    title = currentConfig.title ?: songUnit.title.takeIf { it.isNotBlank() },
                    id = currentConfig.id ?: songUnit.id.takeIf { it.isNotBlank() },
                    files = currentConfig.files ?: FilesConfig.fromSongUnit(songUnit),
                    playback = nextPlayback
                )
                val rawJson = nextConfig.toJsonString()
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                if (configFile.exists() && !configFile.delete()) {
                    Log.w(TAG, "writeTrimPlaybackToSongUnit delete failed path=${configFile.absolutePath}")
                }
                if (!tmpFile.renameTo(configFile)) {
                    configFile.writeText(rawJson, Charsets.UTF_8)
                    tmpFile.delete()
                }
                true
            }.getOrElse { error ->
                Log.w(TAG, "writeTrimPlaybackToSongUnit failed path=${configFile.absolutePath}", error)
                runCatching { tmpFile.delete() }
                false
            }
        }

        private fun resolvePlaybackForSongUnit(context: Context, songUnit: SongUnit): PlaybackConfig? {
            val audioFile = songUnit.audioPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: return null
            val audioUri = Uri.fromFile(audioFile)
            val storedTempo = TrackTempoPrefs.getTempo(context, audioUri.toString())
            val storedPitchSemi = TrackPitchPrefs.getSemi(context, audioUri.toString())
            val storedVolumeDb = TrackVolumePrefs.getDb(context, audioUri.toString())
            val configPlayback = readPlaybackFromSongUnit(songUnit)

            val currentEdit = EditSoundPrefs.get(context, audioUri)
            val legacyEdit = EditPrefs.getEdit(context, audioUri.toString())
            return PlaybackConfig.fromStoredValues(
                startMs = configPlayback?.trimStartMs ?: currentEdit?.startMs?.toLong() ?: legacyEdit?.startMs,
                endMs = configPlayback?.trimEndMs ?: currentEdit?.endMs?.toLong() ?: legacyEdit?.endMs,
                tempo = storedTempo ?: configPlayback?.tempo,
                pitchSemi = storedPitchSemi ?: configPlayback?.pitchSemi,
                volumeDb = storedVolumeDb ?: configPlayback?.volumeDb
            )
        }

        private fun resolveFixedTransportName(path: String?, transportName: String): String? {
            return path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { transportName }
        }

        private fun resolveAudioTransportName(path: String?): String? {
            val audioFile = path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?: return null

            val extension = audioFile.extension
                .trim()
                .lowercase(Locale.ROOT)

            if (extension !in SUPPORTED_AUDIO_EXTENSIONS) {
                return null
            }

            return "audio.$extension"
        }

        private fun resolvePrompterTransportName(path: String?): String? {
            val prompterFile = path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?: return null

            return when (prompterFile.extension.trim().lowercase(Locale.ROOT)) {
                "json" -> "prompter.json"
                else -> "prompter.txt"
            }
        }
    }
}
