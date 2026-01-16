package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    // ----- notifications UI -----
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates = _updates.asSharedFlow()

    private const val PREFS = "library_index_cache"
    private const val KEY_VERSION = "version"

    fun readVersion(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_VERSION, 0L)
    }

    /** Public si tu veux l’appeler à la main, mais normalement inutile si tu passes par save/upsert/remove/clear */
    fun bumpVersion(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_VERSION, 0L) + 1L
        prefs.edit().putLong(KEY_VERSION, v).apply()
    }

    // ----- IO -----
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

        // ✅ notif UI
        bumpVersion(context)
        _updates.tryEmit(Unit)
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
                val parent =
                    if (parentAny == null || parentAny == JSONObject.NULL) null
                    else parentAny.toString()

                out += CachedEntry(
                    uriString = uri,
                    name = o.optString("name", ""),
                    isDirectory = o.optBoolean("dir", false),
                    parentUriString = parent
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
        // ✅ notif UI
        bumpVersion(context)
        _updates.tryEmit(Unit)
    }

    // ----- mutations -----

    fun upsert(
        context: Context,
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

        save(context, out) // ✅ ça notifie déjà
        return out
    }

    fun remove(context: Context, uriString: String): List<CachedEntry> {
        val current = load(context).orEmpty()
        val out = current.filterNot { it.uriString == uriString }
        save(context, out) // ✅ ça notifie déjà
        return out
    }

    /** Enfants d’un dossier (depuis une liste déjà chargée). */
    fun childrenOf(all: List<CachedEntry>, parentUri: Uri): List<CachedEntry> {
        val key = parentUri.toString()
        return all
            .filter { it.parentUriString == key }
            .sortedWith(compareBy<CachedEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }
}