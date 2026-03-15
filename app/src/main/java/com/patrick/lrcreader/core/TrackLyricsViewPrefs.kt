package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object TrackLyricsViewPrefs {
    private const val PREFS_NAME = "track_lyrics_view_prefs"
    private const val KEY_PREFIX = "view_track_"
    private const val LEGACY_KEY_PREFIX = "view_"

    fun get(context: Context, trackUriString: String): LyricsViewMode? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stableKey = preferenceKey(context, trackUriString)
        val value = prefs.getString(stableKey, null)
            ?: prefs.getString(LEGACY_KEY_PREFIX + trackUriString, null)?.also { legacyValue ->
                prefs.edit().putString(stableKey, legacyValue).apply()
            }
            ?: return null
        return runCatching { LyricsViewMode.valueOf(value) }.getOrNull()
    }

    fun save(context: Context, trackUriString: String, mode: LyricsViewMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(preferenceKey(context, trackUriString), mode.name)
            .apply()
    }

    private fun preferenceKey(context: Context, trackUriString: String): String {
        val displayName = resolveDisplayName(context, trackUriString)
        return buildPreferenceKey(trackUriString, displayName)
    }

    private fun resolveDisplayName(context: Context, trackUriString: String): String? {
        val trackUri = runCatching { Uri.parse(trackUriString) }.getOrNull() ?: return null
        return runCatching {
            context.contentResolver.query(
                trackUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn == -1 || !cursor.moveToFirst()) {
                    null
                } else {
                    cursor.getString(nameColumn)
                }
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    internal fun buildPreferenceKey(trackUriString: String, displayName: String?): String {
        val stableName = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTrackFileName(trackUriString)
        return KEY_PREFIX + normalizeKeyPart(stableName)
    }

    internal fun fallbackTrackFileName(trackUriString: String): String {
        val uri = runCatching { Uri.parse(trackUriString) }.getOrNull()
        val last = uri?.lastPathSegment ?: trackUriString
        val clean = last.substringAfterLast('/').substringAfterLast(':').trim()
        return if (clean.isBlank()) "track" else clean
    }

    private fun normalizeKeyPart(raw: String): String {
        val normalized = raw.trim().lowercase()
        return if (normalized.isBlank()) "track" else normalized
    }
}
