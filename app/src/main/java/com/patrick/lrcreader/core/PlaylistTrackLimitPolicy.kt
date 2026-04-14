package com.patrick.lrcreader.core

import android.content.Context
import android.provider.Settings
import com.patrick.lrcreader.exo.BuildConfig

object PlaylistTrackLimitPolicy {
    const val MAX_TRACKS = 10

    private val developerAndroidIds = setOf(
        "aa22b4916d83b7d8"
    )

    fun isUnlimitedEdition(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.lowercase().orEmpty()
        return androidId.isNotEmpty() && developerAndroidIds.contains(androidId)
    }

    fun countLimitedTrackItems(items: List<String>): Int {
        return items.count { item -> isPlayableAudioItem(item) }
    }

    fun canAddTracks(
        context: Context,
        playlistName: String,
        additionalTrackCount: Int
    ): Boolean {
        if (additionalTrackCount <= 0) return true
        if (isUnlimitedEdition(context)) return true
        val currentCount = countLimitedTrackItems(PlaylistRepository.getAllSongsRaw(playlistName))
        return currentCount + additionalTrackCount <= MAX_TRACKS
    }
}
