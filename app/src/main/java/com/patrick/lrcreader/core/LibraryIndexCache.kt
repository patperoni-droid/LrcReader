package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
/**
 * Index disque pour navigation instantanée.
 * On stocke tout l'arbre en "flat list" avec parentUriString.
 */
object LibraryIndexCache {
    private const val FILE_NAME = "library_index.json"

    data class CachedEntry(
        val uriString: String,
        val name: String,
        val isDirectory: Boolean,
        val parentUriString: String? // null uniquement pour la racine "vue"
    )
    fun upsert(
        context: android.content.Context,
        uriString: String,
        name: String,
        isDirectory: Boolean,
        parentUriString: String
    ): List<CachedEntry> {
        val current = load(context).orEmpty()
        val newEntry = CachedEntry(
            uriString = uriString,
            name = name,
            isDirectory = isDirectory,
            parentUriString = parentUriString
        )

        val out = ArrayList<CachedEntry>(current.size + 1)
        var replaced = false

        for (e in current) {
            if (e.uriString == uriString) {
                out += newEntry
                replaced = true
            } else {
                out += e
            }
        }

        if (!replaced) out += newEntry

        save(context, out)
        return out
    }
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates = _updates.asSharedFlow()
    private const val PREFS = "library_index_cache"
    private const val KEY_VERSION = "version"

    fun readVersion(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_VERSION, 0L)
    }

    fun bumpVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_VERSION, 0L) + 1L
        prefs.edit().putLong(KEY_VERSION, v).apply()
    }

    fun save(context: Context, items: List<CachedEntry>) {
        val arr = JSONArray()
        items.forEach { e ->
            val o = JSONObject()
            o.put("uri", e.uriString)
            o.put("name", e.name)
            o.put("dir", e.isDirectory)
            o.put("parent", e.parentUriString ?: JSONObject.NULL)
            arr.put(o)
        }
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }

    fun load(context: Context): List<CachedEntry>? {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return null

        return try {
            val txt = f.readText()
            if (txt.isBlank()) return null
            val arr = JSONArray(txt)
            val out = ArrayList<CachedEntry>(arr.length())

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val uri = o.optString("uri", "")
                if (uri.isBlank()) continue

                val parentAny = o.opt("parent")
                val parent = if (parentAny == null || parentAny == JSONObject.NULL) null else parentAny.toString()

                out.add(
                    CachedEntry(
                        uriString = uri,
                        name = o.optString("name", ""),
                        isDirectory = o.optBoolean("dir", false),
                        parentUriString = parent
                    )
                )
            }
            out
        } catch (_: Exception) {
            try { f.delete() } catch (_: Exception) {}
            null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /** Petit helper: donne les enfants d’un dossier (depuis une liste chargée). */
    fun childrenOf(all: List<CachedEntry>, parentUri: Uri): List<CachedEntry> {
        val key = parentUri.toString()
        return all.filter { it.parentUriString == key }
            .sortedWith(compareBy<CachedEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

}

