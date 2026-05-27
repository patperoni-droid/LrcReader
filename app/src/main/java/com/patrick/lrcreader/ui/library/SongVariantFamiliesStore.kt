package com.patrick.lrcreader.ui.library

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SongVariantFamily(
    val id: String,
    val title: String,
    val songIds: Set<String>,
    val parentSongId: String? = null,
    val activeSongId: String? = null
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
                        val parentSongId = obj.optString("parentSongId").trim().takeIf { it in songIds }
                        val activeSongId = obj.optString("activeSongId").trim().takeIf { it in songIds }
                        add(
                            SongVariantFamily(
                                id = id,
                                title = title,
                                songIds = songIds,
                                parentSongId = parentSongId,
                                activeSongId = activeSongId
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun createOrReplaceFamily(
        context: Context,
        title: String,
        songIds: Set<String>,
        parentSongId: String? = null
    ) {
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanSongIds = songIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        if (cleanSongIds.isEmpty()) return
        val cleanParentSongId = parentSongId?.trim()?.takeIf { it in cleanSongIds }
            ?: cleanSongIds.first()

        val normalizedTitle = normalizeFamilyTitle(cleanTitle)
        val next = load(context)
            .filterNot { normalizeFamilyTitle(it.title) == normalizedTitle }
            .filterNot { family -> family.songIds.any { it in cleanSongIds } } +
            SongVariantFamily(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                songIds = cleanSongIds,
                parentSongId = cleanParentSongId,
                activeSongId = cleanParentSongId
            )

        save(context, next)
    }

    fun setActiveSongId(context: Context, familyId: String, songId: String) {
        val cleanFamilyId = familyId.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return
        val families = load(context)
        val next = families.map { family ->
            if (family.id == cleanFamilyId && cleanSongId in family.songIds) {
                family.copy(activeSongId = cleanSongId)
            } else {
                family
            }
        }
        if (next != families) {
            save(context, next)
        }
    }

    fun upsertFamily(context: Context, family: SongVariantFamily) {
        val cleanId = family.id.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanTitle = family.title.trim().takeIf { it.isNotEmpty() } ?: return
        val cleanSongIds = family.songIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        if (cleanSongIds.isEmpty()) return
        val cleanFamily = family.copy(
            id = cleanId,
            title = cleanTitle,
            songIds = cleanSongIds,
            parentSongId = family.parentSongId?.takeIf { it in cleanSongIds } ?: cleanSongIds.first(),
            activeSongId = family.activeSongId?.takeIf { it in cleanSongIds }
                ?: family.parentSongId?.takeIf { it in cleanSongIds }
                ?: cleanSongIds.first()
        )
        val normalizedTitle = normalizeFamilyTitle(cleanTitle)
        val next = load(context)
            .filterNot { it.id == cleanId || normalizeFamilyTitle(it.title) == normalizedTitle }
            .filterNot { existing -> existing.songIds.any { it in cleanSongIds } } +
            cleanFamily
        save(context, next)
    }

    private fun save(context: Context, families: List<SongVariantFamily>) {
        val array = JSONArray()
        families.forEach { family ->
            array.put(
                JSONObject()
                    .put("id", family.id)
                    .put("title", family.title)
                    .put("parentSongId", family.parentSongId ?: JSONObject.NULL)
                    .put("activeSongId", family.activeSongId ?: family.parentSongId ?: JSONObject.NULL)
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
