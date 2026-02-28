package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.patrick.lrcreader.core.config.ConfigJsonAtomicFileIo
import org.json.JSONObject

/**
 * Stocke, pour chaque fichier audio, les points d'entrée/sortie choisis
 * dans l'écran d’édition.
 *
 * On garde ça dans un seul SharedPreferences sous forme de JSON.
 */
object EditSoundPrefs {

    private const val TAG = "EditSoundPrefs"
    private const val PREFS_NAME = "edit_sound_prefs"
    private const val KEY_JSON = "edits_json"
    private const val CONFIG_FILE_NAME = "trim_settings.json"
    private const val JSON_SCHEMA_VERSION = 1
    private const val JSON_KEY_SCHEMA_VERSION = "schemaVersion"
    private const val JSON_KEY_EDITS = "edits"
    private val cacheLock = Any()
    @Volatile
    private var cachedEdits: Map<String, EditInfo>? = null

    data class EditInfo(
        val startMs: Int,
        val endMs: Int
    )

    data class ResolvedEdit(
        val key: String,
        val info: EditInfo
    )

    fun ensureInitialized(context: Context): Boolean {
        return ConfigJsonAtomicFileIo.ensureInitialized(
            context = context,
            fileName = CONFIG_FILE_NAME,
            defaultRawJson = emptyConfigJson(),
            tag = TAG
        )
    }

    // Appeler depuis un contexte IO si possible (lit JSON + SharedPreferences).
    fun warmCache(context: Context): Map<String, EditInfo> {
        val loaded = loadAllFromStorage(context)
        val snapshot = LinkedHashMap(loaded)
        synchronized(cacheLock) {
            cachedEdits = snapshot
        }
        return snapshot
    }

    fun trimKeyForUri(uri: Uri): String {
        return runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrElse {
            uri.toString()
        }
    }

    /**
     * Sauvegarde un réglage pour un fichier.
     */
    fun save(context: Context, uri: Uri, startMs: Int, endMs: Int) {
        val map = getAll(context).toMutableMap()
        val key = trimKeyForUri(uri)
        map[key] = EditInfo(startMs, endMs)
        val legacyKey = uri.toString()
        if (legacyKey != key) {
            map.remove(legacyKey)
        }
        persistPrefs(context, map)
        persistJson(context, map)
        updateCache(map)
    }

    /**
     * Récupère le réglage d’un fichier (ou null s’il n’y en a pas).
     */
    fun get(context: Context, uri: Uri): EditInfo? {
        return resolve(context, uri)?.info
    }

    fun resolve(context: Context, uri: Uri): ResolvedEdit? {
        val all = getAll(context)
        return resolveFromMap(all, uri)
    }

    fun resolveCached(uri: Uri): ResolvedEdit? {
        val all = synchronized(cacheLock) { cachedEdits } ?: return null
        return resolveFromMap(all, uri)
    }

    private fun resolveFromMap(all: Map<String, EditInfo>, uri: Uri): ResolvedEdit? {
        val key = trimKeyForUri(uri)
        all[key]?.let { return ResolvedEdit(key = key, info = it) }

        val legacyKey = uri.toString()
        if (legacyKey != key) {
            all[legacyKey]?.let { return ResolvedEdit(key = legacyKey, info = it) }
        }
        return null
    }

    /**
     * Supprime seulement l’édition de ce fichier.
     */
    fun clearOne(context: Context, uri: Uri) {
        val map = getAll(context).toMutableMap()
        var removed = false
        val key = trimKeyForUri(uri)
        if (map.remove(key) != null) removed = true
        val legacyKey = uri.toString()
        if (legacyKey != key && map.remove(legacyKey) != null) removed = true
        if (removed) {
            persistPrefs(context, map)
            persistJson(context, map)
            updateCache(map)
        }
    }

    /**
     * Renvoie toutes les éditions sous forme Map<String, EditInfo>
     * (pratique pour la sauvegarde globale).
     */
    fun getAll(context: Context): Map<String, EditInfo> {
        synchronized(cacheLock) { cachedEdits }?.let { return it }
        return warmCache(context)
    }

    private fun loadAllFromStorage(context: Context): Map<String, EditInfo> {
        val fromJson = readJsonAll(context)
        val fromPrefs = readPrefsAll(context)
        if (fromJson.isEmpty()) return fromPrefs
        if (fromPrefs.isEmpty()) return fromJson

        val merged = LinkedHashMap<String, EditInfo>(fromPrefs.size + fromJson.size)
        merged.putAll(fromPrefs)
        merged.putAll(fromJson)
        return merged
    }

    private fun updateCache(map: Map<String, EditInfo>) {
        synchronized(cacheLock) {
            cachedEdits = LinkedHashMap(map)
        }
    }

    private fun readPrefsAll(context: Context): Map<String, EditInfo> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_JSON, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, EditInfo>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.getJSONObject(k)
                val start = obj.optInt("startMs", 0)
                val end = obj.optInt("endMs", 0)
                result[k] = EditInfo(start, end)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ───────────────── interne ─────────────────

    private fun persistPrefs(context: Context, map: Map<String, EditInfo>) {
        val root = JSONObject()
        map.forEach { (uriStr, info) ->
            val o = JSONObject()
            o.put("startMs", info.startMs)
            o.put("endMs", info.endMs)
            root.put(uriStr, o)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, root.toString())
            .apply()
    }

    private fun readJsonAll(context: Context): Map<String, EditInfo> {
        val raw = ConfigJsonAtomicFileIo.readRaw(
            context = context,
            fileName = CONFIG_FILE_NAME,
            tag = TAG,
            defaultRawJson = emptyConfigJson()
        ) ?: return emptyMap()

        return try {
            val root = JSONObject(raw)
            val edits = root.optJSONObject(JSON_KEY_EDITS) ?: root
            val out = linkedMapOf<String, EditInfo>()
            val keys = edits.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == JSON_KEY_SCHEMA_VERSION) continue
                val obj = edits.optJSONObject(k) ?: continue
                out[k] = EditInfo(
                    startMs = obj.optInt("startMs", 0),
                    endMs = obj.optInt("endMs", 0)
                )
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "readJsonAll parse failed", t)
            emptyMap()
        }
    }

    private fun persistJson(context: Context, map: Map<String, EditInfo>) {
        val edits = JSONObject()
        map.forEach { (key, info) ->
            edits.put(
                key,
                JSONObject().apply {
                    put("startMs", info.startMs)
                    put("endMs", info.endMs)
                }
            )
        }

        val root = JSONObject().apply {
            put(JSON_KEY_SCHEMA_VERSION, JSON_SCHEMA_VERSION)
            put(JSON_KEY_EDITS, edits)
        }

        val ok = ConfigJsonAtomicFileIo.writeRawAtomic(
            context = context,
            fileName = CONFIG_FILE_NAME,
            rawJson = root.toString(2),
            tag = TAG,
            defaultRawJson = emptyConfigJson()
        )
        if (!ok) {
            Log.w(TAG, "persistJson skipped/failed file=$CONFIG_FILE_NAME")
        }
    }

    private fun emptyConfigJson(): String {
        return JSONObject()
            .put(JSON_KEY_SCHEMA_VERSION, JSON_SCHEMA_VERSION)
            .put(JSON_KEY_EDITS, JSONObject())
            .toString(2)
    }
}
