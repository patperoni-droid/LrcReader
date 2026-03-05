package com.patrick.lrcreader.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MiniTunerVisibilityStore {
    private const val PREFS_NAME = "ui_prefs"
    private const val KEY_MINI_TUNER_VISIBLE = "mini_tuner_visible"

    private val visibleFlow = MutableStateFlow(false)

    @Volatile
    private var initialized = false

    fun state(context: Context): StateFlow<Boolean> {
        ensureInitialized(context.applicationContext)
        return visibleFlow.asStateFlow()
    }

    fun setVisible(context: Context, visible: Boolean) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        if (visibleFlow.value == visible) return

        visibleFlow.value = visible
        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MINI_TUNER_VISIBLE, visible)
            .apply()
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val initialValue = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MINI_TUNER_VISIBLE, false)
            visibleFlow.value = initialValue
            initialized = true
        }
    }
}
