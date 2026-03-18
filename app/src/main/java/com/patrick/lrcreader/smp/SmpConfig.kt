package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.EditPrefs
import com.patrick.lrcreader.core.EditSoundPrefs
import org.json.JSONObject
import java.io.File

data class SmpConfig(
    val title: String?,
    val id: String?,
    val playback: PlaybackConfig? = null
) {
    data class PlaybackConfig(
        val trimStartMs: Long?,
        val trimEndMs: Long?
    ) {
        companion object {
            fun fromStoredValues(startMs: Long?, endMs: Long?): PlaybackConfig? {
                val trimStartMs = startMs?.takeIf { it > 0L }
                val trimEndMs = endMs?.takeIf { it > 0L }
                if (trimStartMs == null && trimEndMs == null) {
                    return null
                }
                return PlaybackConfig(
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs
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
            if (trimStartMs == null && trimEndMs == null) {
                return null
            }

            return JSONObject().apply {
                trimStartMs?.let { put("trimStartMs", it) }
                trimEndMs?.let { put("trimEndMs", it) }
            }
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
            playback?.toJsonOrNull()?.let { put("playback", it) }
        }
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces)
    }

    companion object {
        private const val TAG = "SmpConfig"

        fun fromSongUnit(context: Context, songUnit: SongUnit): SmpConfig {
            return SmpConfig(
                title = songUnit.title.takeIf { it.isNotBlank() },
                id = songUnit.id.takeIf { it.isNotBlank() },
                playback = resolvePlaybackFromAudioPath(
                    context = context,
                    audioPath = songUnit.audioPath
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
            return PlaybackConfig.fromStoredValues(
                startMs = trimStartMs,
                endMs = trimEndMs
            )
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

        private fun resolvePlaybackFromAudioPath(context: Context, audioPath: String?): PlaybackConfig? {
            val audioFile = audioPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: return null
            val audioUri = Uri.fromFile(audioFile)

            val currentEdit = EditSoundPrefs.get(context, audioUri)
            if (currentEdit != null) {
                return PlaybackConfig.fromWaveformEdit(
                    startMs = currentEdit.startMs,
                    endMs = currentEdit.endMs
                )
            }

            val legacyEdit = EditPrefs.getEdit(context, audioUri.toString()) ?: return null
            return PlaybackConfig.fromStoredValues(
                startMs = legacyEdit.startMs,
                endMs = legacyEdit.endMs
            )
        }
    }
}
