package com.patrick.lrcreader.core

import android.content.Context

object WaveformSessionPrefs {
    private const val PREFS_NAME = "waveform_session_prefs"
    private const val KEY_LAST_URI = "last_uri"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_ZOOM = "last_zoom"
    private const val KEY_LAST_PLAYHEAD_MS = "last_playhead_ms"
    private const val KEY_LAST_SCROLL_PX = "last_scroll_px"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUri(context: Context, uriString: String) {
        prefs(context).edit().putString(KEY_LAST_URI, uriString).apply()
    }

    fun loadUri(context: Context): String? =
        prefs(context).getString(KEY_LAST_URI, null)

    fun saveTitle(context: Context, title: String) {
        prefs(context).edit().putString(KEY_LAST_TITLE, title).apply()
    }

    fun loadTitle(context: Context): String? =
        prefs(context).getString(KEY_LAST_TITLE, null)

    fun saveZoom(context: Context, zoom: Float) {
        prefs(context).edit().putFloat(KEY_LAST_ZOOM, zoom).apply()
    }

    fun loadZoom(context: Context): Float =
        prefs(context).getFloat(KEY_LAST_ZOOM, 1f)

    fun savePlayhead(context: Context, playheadMs: Int) {
        prefs(context).edit().putInt(KEY_LAST_PLAYHEAD_MS, playheadMs).apply()
    }

    fun loadPlayhead(context: Context): Int =
        prefs(context).getInt(KEY_LAST_PLAYHEAD_MS, 0)

    fun saveScroll(context: Context, scrollPx: Int) {
        prefs(context).edit().putInt(KEY_LAST_SCROLL_PX, scrollPx).apply()
    }

    fun loadScroll(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SCROLL_PX, 0)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
