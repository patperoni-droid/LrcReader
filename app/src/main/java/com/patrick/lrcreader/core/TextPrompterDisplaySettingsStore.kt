package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONObject

enum class TextPrompterAlignment {
    START,
    CENTER
}

data class TextPrompterDisplaySettings(
    val alignment: TextPrompterAlignment = TextPrompterAlignment.START,
    val transposeSemitones: Int = 0
)

object TextPrompterDisplaySettingsStore {

    private const val PREFS_NAME = "text_prompter_display_settings_prefs"
    private const val TEXT_SONG_PREFIX = "text:"
    private const val AUDIO_LYRICS_PREFIX = "audio-lyrics:"
    private const val LEGACY_NOTE_PREFIX = "note:"
    private const val JSON_KEY_ALIGNMENT = "alignment"
    private const val JSON_KEY_TRANSPOSE_SEMITONES = "transposeSemitones"
    private const val MIN_TRANSPOSE_SEMITONES = -11
    private const val MAX_TRANSPOSE_SEMITONES = 11

    @JvmInline
    value class Key internal constructor(internal val storageKey: String)

    fun textSongKey(textSongId: String): Key? =
        textSongId.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { Key(TEXT_SONG_PREFIX + it) }

    fun audioLyricsSongKey(songId: String): Key? =
        songId.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { Key(AUDIO_LYRICS_PREFIX + it) }

    fun legacyNoteKey(noteId: Long): Key = Key(LEGACY_NOTE_PREFIX + noteId)

    fun get(context: Context, key: Key): TextPrompterDisplaySettings {
        val raw = preferences(context).getString(key.storageKey, null)
            ?: return TextPrompterDisplaySettings()
        return decode(raw)
    }

    fun save(context: Context, key: Key, settings: TextPrompterDisplaySettings) {
        val normalized = settings.copy(
            transposeSemitones = normalizeTransposeSemitones(settings.transposeSemitones)
        )
        if (normalized == TextPrompterDisplaySettings()) {
            delete(context, key)
            return
        }

        val encoded = JSONObject()
            .put(JSON_KEY_ALIGNMENT, normalized.alignment.name)
            .put(JSON_KEY_TRANSPOSE_SEMITONES, normalized.transposeSemitones)
            .toString()
        preferences(context).edit()
            .putString(key.storageKey, encoded)
            .apply()
    }

    fun saveAlignment(context: Context, key: Key, alignment: TextPrompterAlignment) {
        save(context, key, get(context, key).copy(alignment = alignment))
    }

    fun saveTransposeSemitones(context: Context, key: Key, transposeSemitones: Int) {
        save(
            context,
            key,
            get(context, key).copy(transposeSemitones = transposeSemitones)
        )
    }

    fun delete(context: Context, key: Key) {
        preferences(context).edit()
            .remove(key.storageKey)
            .apply()
    }

    fun clearAll(context: Context) {
        preferences(context).edit()
            .clear()
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun decode(raw: String): TextPrompterDisplaySettings = runCatching {
        val json = JSONObject(raw)
        val alignmentName = json.optString(JSON_KEY_ALIGNMENT)
        TextPrompterDisplaySettings(
            alignment = TextPrompterAlignment.entries
                .firstOrNull { it.name == alignmentName }
                ?: TextPrompterAlignment.START,
            transposeSemitones = normalizeTransposeSemitones(
                json.optInt(JSON_KEY_TRANSPOSE_SEMITONES, 0)
            )
        )
    }.getOrDefault(TextPrompterDisplaySettings())

    private fun normalizeTransposeSemitones(value: Int): Int =
        value.coerceIn(MIN_TRANSPOSE_SEMITONES, MAX_TRANSPOSE_SEMITONES)
}
