package com.patrick.lrcreader.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppEdition {
    LITE,
    PRO
}

object EditionConfig {
    private const val PREFS_NAME = "edition_config_prefs"
    private const val KEY_CURRENT_EDITION = "current_edition"
    private const val BETA_PRO_CODE = "SMP-PRO-2026"
    private val defaultEdition: AppEdition = AppEdition.PRO

    private var currentState by mutableStateOf(defaultEdition)

    val current: AppEdition
        get() = currentState

    const val isDmxUiEnabled: Boolean = false

    val isLite: Boolean
        get() = current == AppEdition.LITE

    val isPro: Boolean
        get() = current == AppEdition.PRO

    fun initialize(context: Context) {
        val savedName = prefs(context).getString(KEY_CURRENT_EDITION, defaultEdition.name)
        currentState = AppEdition.entries.firstOrNull { it.name == savedName } ?: defaultEdition
    }

    fun tryEnablePro(context: Context, code: String): Boolean {
        if (code.trim() != BETA_PRO_CODE) return false
        setEdition(context, AppEdition.PRO)
        return true
    }

    fun revertToLite(context: Context) {
        setEdition(context, AppEdition.LITE)
    }

    private fun setEdition(context: Context, edition: AppEdition) {
        currentState = edition
        prefs(context)
            .edit()
            .putString(KEY_CURRENT_EDITION, edition.name)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
