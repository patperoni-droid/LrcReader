package com.patrick.lrcreader.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguagePrefs {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun getSavedLanguageTag(context: Context): String? {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setSavedLanguageTag(context: Context, languageTag: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (languageTag.isNullOrBlank() || languageTag == "auto") {
            editor.remove(KEY_LANGUAGE_TAG)
        } else {
            editor.putString(KEY_LANGUAGE_TAG, languageTag)
        }
        editor.apply()
    }

    fun applySavedLanguage(context: Context) {
        val tag = getSavedLanguageTag(context)
        val locales = if (tag == null || tag == "auto") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
