package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

/**
 * Gestion des titres "texte seul" pour le prompteur sans audio.
 *
 * Chaque titre est identifié par un id, et référencé dans les playlists
 * sous forme d'URI virtuelle :  prompter://<id>
 *
 * Tout est stocké en JSON dans SharedPreferences.
 */
object TextSongRepository {

    private const val PREFS_NAME = "text_song_repo"
    private const val KEY_JSON = "text_songs"

    data class TextSongData(
        val title: String,
        val content: String
    )

    // Cache en mémoire
    private var cache: MutableMap<String, TextSongData>? = null

    private fun ensureLoaded(context: Context) {
        if (cache != null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_JSON, null)
        val map = mutableMapOf<String, TextSongData>()

        if (!jsonString.isNullOrBlank()) {
            try {
                val root = JSONObject(jsonString)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val obj = root.getJSONObject(id)
                    val title = obj.optString("title", "")
                    val content = obj.optString("content", "")
                    map[id] = TextSongData(title, content)
                }
            } catch (_: Exception) {
                // si ça plante, on repart sur un cache vide
            }
        }

        cache = map
    }

    private fun persist(context: Context) {
        val map = cache ?: return
        val root = JSONObject()
        map.forEach { (id, data) ->
            val obj = JSONObject().apply {
                put("title", data.title)
                put("content", data.content)
            }
            root.put(id, obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JSON, root.toString())
            .apply()
    }

    private fun genId(): String {
        val t = System.currentTimeMillis().toString(16)
        val r = Random.nextInt(0, 0xFFFF).toString(16)
        return "${t}_$r"
    }

    /** Crée un nouveau titre texte et renvoie son id. */
    fun create(context: Context, title: String, content: String): String {
        ensureLoaded(context)
        val id = genId()
        val data = TextSongData(
            title = title.trim(),
            content = content.trim()
        )
        cache!![id] = data
        persist(context)
        return id
    }

    /** Récupère un titre texte par id. */
    fun get(context: Context, id: String): TextSongData? {
        ensureLoaded(context)
        return cache!![id]
    }

    /** Met à jour un titre texte existant. */
    fun update(context: Context, id: String, title: String, content: String) {
        ensureLoaded(context)
        if (!cache!!.containsKey(id)) return
        cache!![id] = TextSongData(title.trim(), content.trim())
        persist(context)
    }

    /** Supprime un titre texte. */
    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        cache!!.remove(id)
        persist(context)
    }

    /** Export complet pour BackupManager. */
    fun exportAll(context: Context): Map<String, TextSongData> {
        ensureLoaded(context)
        return HashMap(cache!!)
    }

    /** Clear + import un titre (utilisé par BackupManager). */
    fun clearAll(context: Context) {
        cache = mutableMapOf()
        persist(context)
    }

    fun importOne(context: Context, id: String, title: String, content: String) {
        ensureLoaded(context)
        cache!![id] = TextSongData(title.trim(), content.trim())
        persist(context)
    }
}