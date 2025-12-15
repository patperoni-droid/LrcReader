package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache disque ultra simple pour affichage instantané.
 * On stocke uri + name + isDirectory.
 */
object LibraryIndexCache {
    private const val FILE_NAME = "library_index.json"

    data class CachedEntry(
        val uriString: String,
        val name: String,
        val isDirectory: Boolean
    )

    fun save(context: Context, items: List<CachedEntry>) {
        val arr = JSONArray()
        items.forEach { e ->
            val o = JSONObject()
            o.put("uri", e.uriString)
            o.put("name", e.name)
            o.put("dir", e.isDirectory)
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
                out.add(
                    CachedEntry(
                        uriString = o.optString("uri", ""),
                        name = o.optString("name", ""),
                        isDirectory = o.optBoolean("dir", false)
                    )
                )
            }

            out.filter { it.uriString.isNotBlank() }
        } catch (_: Exception) {
            // Cache corrompu → on le supprime pour éviter crash au prochain lancement
            try { f.delete() } catch (_: Exception) {}
            null
        }
    }
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

}

