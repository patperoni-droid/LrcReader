package com.patrick.lrcreader.core

import android.content.Context

object SessionPrefs {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_TAB = "last_tab"
    private const val KEY_QUICK_PLAYLIST = "last_quick_playlist"
    private const val KEY_OPENED_PLAYLIST = "last_opened_playlist"

    fun saveTab(ctx: Context, tabName: String) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAB, tabName)
            .apply()
    }

    fun getTab(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TAB, null)
    }

    fun saveQuickPlaylist(ctx: Context, name: String?) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUICK_PLAYLIST, name)
            .apply()
    }

    fun getQuickPlaylist(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUICK_PLAYLIST, null)
    }

    fun saveOpenedPlaylist(ctx: Context, name: String?) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENED_PLAYLIST, name)
            .apply()
    }

    fun getOpenedPlaylist(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_OPENED_PLAYLIST, null)
    }
}