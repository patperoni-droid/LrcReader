package com.patrick.lrcreader.ui.library

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SongVariantFamily(
    val id: String,
    val title: String,
    val songIds: Set<String>
)

object SongVariantFamiliesStore {
    private const val PREFS_NAME = "song_variant_families"
    private const val KEY_FAMILIES = "families_json"

    val version = mutableIntStateOf(0)

    fun load(context: Context): List<SongVariantFamily> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAMILIES, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString("id").trim().takeIf { it.isNotEmpty() } ?: continue
                    val title = obj.optString("title").trim().takeIf { it.isNotEmpty() } ?: continue
                    val ids = obj.optJSONArray("songIds") ?: JSONArray()
                    val songIds = buildSet {
                        for (songIndex in 0 until ids.length()) {
                            ids.optString(songIndex).trim().takeIf { it.isNotEmpty() }?.let(::add)
                        }
                    }
                    if (songIds.isNotEmpty()) {
                        add(SongVariantFamily(id = id, title = title, songIds = songIds))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun createOrReplaceFamily(context: Context, title: String, songIds: Set<String>) {
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanSongIds = songIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        if (cleanSongIds.isEmpty()) return

        val normalizedTitle = normalizeFamilyTitle(cleanTitle)
        val next = load(context)
            .filterNot { normalizeFamilyTitle(it.title) == normalizedTitle }
            .filterNot { family -> family.songIds.any { it in cleanSongIds } } +
            SongVariantFamily(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                songIds = cleanSongIds
            )

        save(context, next)
    }

    private fun save(context: Context, families: List<SongVariantFamily>) {
        val array = JSONArray()
        families.forEach { family ->
            array.put(
                JSONObject()
                    .put("id", family.id)
                    .put("title", family.title)
                    .put("songIds", JSONArray().also { ids ->
                        family.songIds.sorted().forEach(ids::put)
                    })
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAMILIES, array.toString())
            .apply()
        version.intValue++
    }

    private fun normalizeFamilyTitle(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")
}
