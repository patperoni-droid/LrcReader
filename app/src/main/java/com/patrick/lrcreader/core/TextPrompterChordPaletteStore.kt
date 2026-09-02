package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray

object TextPrompterChordPaletteStore {

    private const val PREFS_NAME = "text_prompter_chord_palette_prefs"
    private const val TEXT_SONG_PREFIX = "text:"
    private const val LEGACY_NOTE_PREFIX = "note:"

    @JvmInline
    value class Key internal constructor(internal val storageKey: String)

    fun textSongKey(textSongId: String): Key? =
        textSongId.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { Key(TEXT_SONG_PREFIX + it) }

    fun legacyNoteKey(noteId: Long): Key = Key(LEGACY_NOTE_PREFIX + noteId)

    fun get(context: Context, key: Key): List<String> {
        val raw = preferences(context).getString(key.storageKey, null) ?: return emptyList()
        return normalize(decode(raw))
    }

    fun save(context: Context, key: Key, chords: List<String>) {
        val normalized = normalize(chords)
        if (normalized.isEmpty()) {
            delete(context, key)
            return
        }

        val encoded = JSONArray().apply {
            normalized.forEach(::put)
        }.toString()
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

    private fun normalize(chords: List<String>): List<String> = buildList {
        chords.forEach { chord ->
            val normalized = chord.trim()
            if (normalized.isNotEmpty() && normalized !in this) {
                add(normalized)
            }
        }
    }

    private fun decode(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val chord = array.opt(index) as? String ?: continue
                add(chord)
            }
        }
    }.getOrDefault(emptyList())
}
