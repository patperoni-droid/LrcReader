package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

/**
 * Bloc-notes multi-notes.
 *
 * Stockage simple en JSON dans SharedPreferences :
 *  id -> { title, content, updatedAt }
 */
object NotesRepository {

    private const val PREFS_NAME = "notes_repo"
    private const val KEY_JSON = "notes"

    data class Note(
        val id: String,
        val title: String,
        val content: String,
        val updatedAt: Long
    )

    // Cache en mémoire : id -> Note
    private var cache: MutableMap<String, Note>? = null

    // ------------------------ interne ------------------------

    private fun ensureLoaded(context: Context) {
        if (cache != null) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_JSON, null)
        val map = mutableMapOf<String, Note>()

        if (!jsonString.isNullOrBlank()) {
            try {
                val root = JSONObject(jsonString)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val obj = root.getJSONObject(id)
                    val title = obj.optString("title", "")
                    val content = obj.optString("content", "")
                    val updatedAt = obj.optLong("updatedAt", 0L)

                    map[id] = Note(
                        id = id,
                        title = title,
                        content = content,
                        updatedAt = updatedAt
                    )
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

        map.forEach { (id, note) ->
            val obj = JSONObject().apply {
                put("title", note.title)
                put("content", note.content)
                put("updatedAt", note.updatedAt)
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

    // ------------------------ API publique ------------------------

    /** Liste de toutes les notes, triées de la plus récente à la plus ancienne. */
    fun getAll(context: Context): List<Note> {
        ensureLoaded(context)
        return cache!!.values
            .sortedByDescending { it.updatedAt }
    }

    /** Récupère une note par id. */
    fun get(context: Context, id: String): Note? {
        ensureLoaded(context)
        return cache!![id]
    }

    /** Crée une nouvelle note. */
    fun create(context: Context, title: String, content: String): Note {
        ensureLoaded(context)
        val id = genId()
        val note = Note(
            id = id,
            title = title.trim().ifEmpty { "Sans titre" },
            content = content,
            updatedAt = System.currentTimeMillis()
        )
        cache!![id] = note
        persist(context)
        return note
    }

    /** Met à jour une note existante. Renvoie la note mise à jour ou null si inconnue. */
    fun update(context: Context, id: String, title: String, content: String): Note? {
        ensureLoaded(context)
        val old = cache!![id] ?: return null
        val updated = old.copy(
            title = title.trim().ifEmpty { "Sans titre" },
            content = content,
            updatedAt = System.currentTimeMillis()
        )
        cache!![id] = updated
        persist(context)
        return updated
    }

    /** Supprime une note. */
    fun delete(context: Context, id: String) {
        ensureLoaded(context)
        cache!!.remove(id)
        persist(context)
    }

    /** Pour plus tard si tu veux un bouton "Tout effacer". */
    fun clearAll(context: Context) {
        cache = mutableMapOf()
        persist(context)
    }
}