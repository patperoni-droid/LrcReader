package com.patrick.lrcreader.core

import android.content.Context

object TimelinePaletteStore {
    private const val PREF = "timeline_palette_prefs"
    private const val KEY = "global_palette"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
        return normalize(raw.lines())
    }

    fun save(context: Context, tags: List<String>): List<String> {
        val normalized = normalize(tags)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, normalized.joinToString("\n"))
            .apply()
        return normalized
    }

    private fun normalize(tags: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val normalized = mutableListOf<String>()

        tags.forEach { rawTag ->
            val tag = rawTag.trim()
            if (tag.isEmpty()) {
                return@forEach
            }

            val canonical = tag.lowercase()
            if (seen.add(canonical)) {
                normalized += tag
            }
        }

        return normalized
    }
}
