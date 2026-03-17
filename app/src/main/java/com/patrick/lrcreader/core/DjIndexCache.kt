package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache séparé pour le Mode DJ.
 * On ne mélange pas avec la bibliothèque principale.
 */
object DjIndexCache {

    private const val PREF = "dj_index_cache"
    private const val KEY = "dj_index_all"
    private const val KEY_SCAN_ROOT = "dj_scan_root"
    private const val KEY_SCAN_SIGNATURE = "dj_scan_signature"
    private const val KEY_SCAN_ROOT_LAST_MODIFIED = "dj_scan_root_last_modified"
    private const val KEY_SCAN_ITEM_COUNT = "dj_scan_item_count"
    private const val KEY_SCAN_VERIFIED_AT = "dj_scan_verified_at"

    data class Entry(
        val uriString: String,
        val name: String,
        val isDirectory: Boolean,
        val parentUriString: String,
        val lastModifiedMs: Long = 0L,
        val sizeBytes: Long = 0L
    )

    data class ScanMeta(
        val rootUriString: String,
        val signature: String,
        val rootLastModifiedMs: Long,
        val itemCount: Int,
        val verifiedAtMs: Long
    )

    fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("u", e.uriString)
            o.put("n", e.name)
            o.put("d", e.isDirectory)
            o.put("p", e.parentUriString)
            o.put("lm", e.lastModifiedMs)
            o.put("sz", e.sizeBytes)
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
                            parentUriString = o.getString("p"),
                            lastModifiedMs = o.optLong("lm", 0L),
                            sizeBytes = o.optLong("sz", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveScanMeta(
        context: Context,
        rootUri: Uri,
        signature: String,
        rootLastModifiedMs: Long,
        itemCount: Int,
        verifiedAtMs: Long = System.currentTimeMillis()
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCAN_ROOT, rootUri.toString())
            .putString(KEY_SCAN_SIGNATURE, signature)
            .putLong(KEY_SCAN_ROOT_LAST_MODIFIED, rootLastModifiedMs)
            .putInt(KEY_SCAN_ITEM_COUNT, itemCount)
            .putLong(KEY_SCAN_VERIFIED_AT, verifiedAtMs)
            .apply()
    }

    fun loadScanMeta(context: Context): ScanMeta? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val root = prefs.getString(KEY_SCAN_ROOT, null) ?: return null
        val signature = prefs.getString(KEY_SCAN_SIGNATURE, null) ?: return null

        return ScanMeta(
            rootUriString = root,
            signature = signature,
            rootLastModifiedMs = prefs.getLong(KEY_SCAN_ROOT_LAST_MODIFIED, 0L),
            itemCount = prefs.getInt(KEY_SCAN_ITEM_COUNT, 0),
            verifiedAtMs = prefs.getLong(KEY_SCAN_VERIFIED_AT, 0L)
        )
    }

    fun updateScanVerification(
        context: Context,
        rootUri: Uri,
        rootLastModifiedMs: Long,
        verifiedAtMs: Long = System.currentTimeMillis()
    ) {
        val current = loadScanMeta(context) ?: return
        if (current.rootUriString != rootUri.toString()) return

        saveScanMeta(
            context = context,
            rootUri = rootUri,
            signature = current.signature,
            rootLastModifiedMs = rootLastModifiedMs,
            itemCount = current.itemCount,
            verifiedAtMs = verifiedAtMs
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .remove(KEY_SCAN_ROOT)
            .remove(KEY_SCAN_SIGNATURE)
            .remove(KEY_SCAN_ROOT_LAST_MODIFIED)
            .remove(KEY_SCAN_ITEM_COUNT)
            .remove(KEY_SCAN_VERIFIED_AT)
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
