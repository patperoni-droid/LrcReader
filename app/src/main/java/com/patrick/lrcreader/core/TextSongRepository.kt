package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.ConfigJsonAtomicFileIo
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

    private const val TAG = "TextSongRepository"
    private const val PREFS_NAME = "text_song_repo"
    private const val KEY_JSON = "text_songs"
    private const val CONFIG_FILE_NAME = "text_songs.json"
    private const val JSON_VERSION = 1
    private const val JSON_KEY_VERSION = "version"
    private const val JSON_KEY_ITEMS = "items"
    private const val JSON_KEY_TITLE = "title"
    private const val JSON_KEY_TEXT = "text"
    private const val JSON_KEY_CONTENT_LEGACY = "content"

    data class TextSongData(
        val title: String,
        val content: String
    )

    // Cache en mémoire
    private var cache: MutableMap<String, TextSongData>? = null

    fun ensureInitialized(context: Context): Boolean {
        return ConfigJsonAtomicFileIo.ensureInitialized(
            context = context,
            fileName = CONFIG_FILE_NAME,
            defaultRawJson = emptyConfigJson(),
            tag = TAG
        )
    }

    private fun ensureLoaded(context: Context) {
        if (cache != null) return

        val fromJson = readPortableJson(context)
        val fromPrefs = readLegacyPrefs(context)

        val merged = linkedMapOf<String, TextSongData>()
        if (fromPrefs.isNotEmpty()) merged.putAll(fromPrefs)
        if (fromJson.isNotEmpty()) merged.putAll(fromJson)

        cache = merged
    }

    private fun persist(context: Context) {
        val map = cache ?: return
        persistLegacyPrefs(context, map)
        persistPortableJson(context, map)
    }

    private fun persistLegacyPrefs(context: Context, map: Map<String, TextSongData>) {
        val root = JSONObject()
        map.forEach { (id, data) ->
            val obj = JSONObject().apply {
                put(JSON_KEY_TITLE, data.title)
                put(JSON_KEY_CONTENT_LEGACY, data.content)
            }
            root.put(id, obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JSON, root.toString())
            .apply()
    }

    private fun persistPortableJson(context: Context, map: Map<String, TextSongData>) {
        val items = JSONObject()
        map.forEach { (id, data) ->
            items.put(
                id,
                JSONObject().apply {
                    put(JSON_KEY_TITLE, data.title)
                    put(JSON_KEY_TEXT, data.content)
                }
            )
        }

        val root = JSONObject().apply {
            put(JSON_KEY_VERSION, JSON_VERSION)
            put(JSON_KEY_ITEMS, items)
        }

        val ok = ConfigJsonAtomicFileIo.writeRawAtomic(
            context = context,
            fileName = CONFIG_FILE_NAME,
            rawJson = root.toString(2),
            tag = TAG,
            defaultRawJson = emptyConfigJson()
        )
        if (!ok) {
            Log.w(TAG, "persistPortableJson skipped/failed file=$CONFIG_FILE_NAME")
        }
    }

    private fun readPortableJson(context: Context): MutableMap<String, TextSongData> {
        val raw = ConfigJsonAtomicFileIo.readRaw(
            context = context,
            fileName = CONFIG_FILE_NAME,
            tag = TAG,
            defaultRawJson = emptyConfigJson()
        ) ?: return mutableMapOf()

        return try {
            val root = JSONObject(raw)
            val items = root.optJSONObject(JSON_KEY_ITEMS) ?: root
            val map = linkedMapOf<String, TextSongData>()
            val keys = items.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                if (id == JSON_KEY_VERSION || id == JSON_KEY_ITEMS) continue
                val obj = items.optJSONObject(id) ?: continue
                val title = obj.optString(JSON_KEY_TITLE, "")
                val text = when {
                    obj.has(JSON_KEY_TEXT) -> obj.optString(JSON_KEY_TEXT, "")
                    obj.has(JSON_KEY_CONTENT_LEGACY) -> obj.optString(JSON_KEY_CONTENT_LEGACY, "")
                    else -> ""
                }
                map[id] = TextSongData(title, text)
            }
            map.toMutableMap()
        } catch (t: Throwable) {
            Log.w(TAG, "readPortableJson parse failed", t)
            mutableMapOf()
        }
    }

    private fun readLegacyPrefs(context: Context): MutableMap<String, TextSongData> {
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
                    val title = obj.optString(JSON_KEY_TITLE, "")
                    val content = obj.optString(JSON_KEY_CONTENT_LEGACY, "")
                    map[id] = TextSongData(title, content)
                }
            } catch (_: Exception) {
                // si ça plante, on repart sur un cache vide
            }
        }

        return map
    }

    private fun emptyConfigJson(): String {
        return JSONObject()
            .put(JSON_KEY_VERSION, JSON_VERSION)
            .put(JSON_KEY_ITEMS, JSONObject())
            .toString(2)
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
