package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.patrick.lrcreader.core.config.SongIdKeyResolver

object TrackLyricsViewPrefs {
    private const val PREFS_NAME = "track_lyrics_view_prefs"
    private const val KEY_PREFIX = "view_track_"
    private const val LEGACY_KEY_PREFIX = "view_"

    private data class PreferenceKeys(
        val songScopedKey: String?,
        val stableKey: String,
        val legacyUriKey: String
    )

    fun get(context: Context, trackUriString: String): LyricsViewMode? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = resolvePreferenceKeys(context, trackUriString)
        val value = keys.songScopedKey
            ?.let { songKey ->
                prefs.getString(songKey, null)
                    ?: prefs.getString(keys.stableKey, null)?.also { stableValue ->
                        prefs.edit()
                            .putString(songKey, stableValue)
                            .remove(keys.stableKey)
                            .remove(keys.legacyUriKey)
                            .apply()
                    }
                    ?: prefs.getString(keys.legacyUriKey, null)?.also { legacyValue ->
                        prefs.edit()
                            .putString(songKey, legacyValue)
                            .remove(keys.stableKey)
                            .remove(keys.legacyUriKey)
                            .apply()
                    }
            }
            ?: prefs.getString(keys.stableKey, null)
            ?: prefs.getString(keys.legacyUriKey, null)?.also { legacyValue ->
                prefs.edit().putString(keys.stableKey, legacyValue).apply()
            }
            ?: return null
        return runCatching { LyricsViewMode.valueOf(value) }.getOrNull()
    }

    fun save(context: Context, trackUriString: String, mode: LyricsViewMode) {
        val keys = resolvePreferenceKeys(context, trackUriString)
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()

        if (keys.songScopedKey != null) {
            editor.putString(keys.songScopedKey, mode.name)
            editor.remove(keys.stableKey)
            editor.remove(keys.legacyUriKey)
        } else {
            editor.putString(keys.stableKey, mode.name)
        }

        editor.apply()
    }

    private fun preferenceKey(context: Context, trackUriString: String): String {
        val displayName = resolveDisplayName(context, trackUriString)
        return buildPreferenceKey(trackUriString, displayName)
    }

    private fun resolvePreferenceKeys(context: Context, trackUriString: String): PreferenceKeys {
        val cleanTrackUri = trackUriString.trim()
        val stableKey = preferenceKey(context, cleanTrackUri)
        val songScopedKey = buildSongScopedPreferenceKey(
            SongIdKeyResolver.resolveSongIdFromUri(context, cleanTrackUri)
        )
        return PreferenceKeys(
            songScopedKey = songScopedKey,
            stableKey = stableKey,
            legacyUriKey = LEGACY_KEY_PREFIX + cleanTrackUri
        )
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

    internal fun buildSongScopedPreferenceKey(songId: String?): String? {
        val songScopedKey = SongIdKeyResolver.songScopedKey(songId) ?: return null
        return KEY_PREFIX + songScopedKey
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
