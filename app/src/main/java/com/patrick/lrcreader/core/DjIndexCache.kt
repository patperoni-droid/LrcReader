package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache séparé pour le Mode DJ.
 * On ne mélange pas avec la bibliothèque principale.
 */
object DjIndexCache {

    private const val PREF = "dj_index_cache"
    private const val KEY = "dj_index_all"

    data class Entry(
        val uriString: String,
        val name: String,
        val isDirectory: Boolean,
        val parentUriString: String
    )

    fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("u", e.uriString)
            o.put("n", e.name)
            o.put("d", e.isDirectory)
            o.put("p", e.parentUriString)
            arr.put(o)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun load(context: Context): List<Entry>? {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null

        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            uriString = o.getString("u"),
                            name = o.getString("n"),
                            isDirectory = o.getBoolean("d"),
                            parentUriString = o.getString("p")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    fun childrenOf(all: List<Entry>, parentUri: android.net.Uri): List<Entry> {
        val p = parentUri.toString()
        return all.filter { it.parentUriString == p }
            .sortedWith(
                compareByDescending<Entry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
    }
}