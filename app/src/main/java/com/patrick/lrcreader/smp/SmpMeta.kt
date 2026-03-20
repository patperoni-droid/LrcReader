package com.patrick.lrcreader.smp

import org.json.JSONObject
import java.io.File
import java.util.Locale

data class SmpMeta(
    val version: Int = 2,
    val title: String? = null,
    val artist: String? = null,
    val audioFile: String = "audio.mp3",
    val lyricsFile: String? = "lyrics.lrc",
    val chordsFile: String? = "chords.lrc",
    val waveformFile: String? = "waveform.json",
    val midiCuesFile: String? = "midi_cues.json",
    val annotationsFile: String? = "annotations.json",
    val dmxFile: String? = "dmx.json",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            title?.let { put("title", it) }
            artist?.let { put("artist", it) }
            put("audioFile", audioFile)
            put("lyricsFile", lyricsFile)
            put("chordsFile", chordsFile)
            put("waveformFile", waveformFile)
            put("midiCuesFile", midiCuesFile)
            put("annotationsFile", annotationsFile)
            put("dmxFile", dmxFile)
            put("updatedAt", updatedAt)
        }
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces)
    }

    companion object {
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "wav", "wave", "flac", "m4a", "aac", "ogg")

        fun fromSongUnit(songUnit: SongUnit): SmpMeta {
            return SmpMeta(
                title = songUnit.title.takeIf { it.isNotBlank() },
                audioFile = resolveAudioFileName(songUnit.audioPath),
                lyricsFile = resolveFixedFileName(songUnit.lyricsPath, "lyrics.lrc"),
                chordsFile = resolveFixedFileName(songUnit.chordsPath, "chords.lrc"),
                waveformFile = resolveFixedFileName(songUnit.waveformPath, "waveform.json"),
                midiCuesFile = resolveFixedFileName(songUnit.midiPath, "midi_cues.json"),
                annotationsFile = resolveFixedFileName(songUnit.annotationsPath, "annotations.json"),
                dmxFile = resolveFixedFileName(songUnit.dmxPath, "dmx.json")
            )
        }

        fun fromJsonOrNull(rawJson: String?): SmpMeta? {
            if (rawJson.isNullOrBlank()) {
                return null
            }

            return runCatching {
                val json = JSONObject(rawJson)
                SmpMeta(
                    version = json.optInt("version", 2),
                    title = json.optString("title").trim().ifBlank { null },
                    artist = json.optString("artist").trim().ifBlank { null },
                    audioFile = json.optString("audioFile").trim().ifBlank { "audio.mp3" },
                    lyricsFile = json.optOptionalString("lyricsFile") ?: "lyrics.lrc",
                    chordsFile = json.optOptionalString("chordsFile") ?: "chords.lrc",
                    waveformFile = json.optOptionalString("waveformFile") ?: "waveform.json",
                    midiCuesFile = json.optOptionalString("midiCuesFile") ?: "midi_cues.json",
                    annotationsFile = json.optOptionalString("annotationsFile") ?: "annotations.json",
                    dmxFile = json.optOptionalString("dmxFile") ?: "dmx.json",
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                )
            }.getOrNull()
        }

        private fun JSONObject.optOptionalString(key: String): String? {
            if (!has(key) || isNull(key)) {
                return null
            }
            return optString(key).trim().ifBlank { null }
        }

        private fun resolveFixedFileName(path: String?, expectedFileName: String): String? {
            return path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { expectedFileName }
        }

        private fun resolveAudioFileName(path: String?): String {
            val audioFile = path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?: return "audio.mp3"

            val extension = audioFile.extension.trim().lowercase(Locale.ROOT)
            return if (extension in SUPPORTED_AUDIO_EXTENSIONS) {
                "audio.$extension"
            } else {
                "audio.mp3"
            }
        }
    }
}
