package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONObject

enum class TextPrompterAlignment {
    START,
    CENTER
}

data class TextPrompterDisplaySettings(
    val alignment: TextPrompterAlignment = TextPrompterAlignment.START
)

object TextPrompterDisplaySettingsStore {

    private const val PREFS_NAME = "text_prompter_display_settings_prefs"
    private const val TEXT_SONG_PREFIX = "text:"
    private const val LEGACY_NOTE_PREFIX = "note:"
    private const val JSON_KEY_ALIGNMENT = "alignment"

    @JvmInline
    value class Key internal constructor(internal val storageKey: String)

    fun textSongKey(textSongId: String): Key? =
        textSongId.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { Key(TEXT_SONG_PREFIX + it) }

    fun legacyNoteKey(noteId: Long): Key = Key(LEGACY_NOTE_PREFIX + noteId)

    fun get(context: Context, key: Key): TextPrompterDisplaySettings {
        val raw = preferences(context).getString(key.storageKey, null)
            ?: return TextPrompterDisplaySettings()
        return decode(raw)
    }

    fun save(context: Context, key: Key, settings: TextPrompterDisplaySettings) {
        if (settings == TextPrompterDisplaySettings()) {
            delete(context, key)
            return
        }

        val encoded = JSONObject()
            .put(JSON_KEY_ALIGNMENT, settings.alignment.name)
            .toString()
        preferences(context).edit()
            .putString(key.storageKey, encoded)
            .apply()
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
        val alignmentName = JSONObject(raw).optString(JSON_KEY_ALIGNMENT)
        TextPrompterDisplaySettings(
            alignment = TextPrompterAlignment.entries
                .firstOrNull { it.name == alignmentName }
                ?: TextPrompterAlignment.START
        )
    }.getOrDefault(TextPrompterDisplaySettings())
}
