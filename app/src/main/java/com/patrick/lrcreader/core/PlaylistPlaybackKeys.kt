package com.patrick.lrcreader.core

fun canonicalPlaylistPlaybackKey(
    playlistItemKey: String?,
    playbackUri: String
): String {
    val cleanPlaylistItemKey = playlistItemKey?.trim().orEmpty()
    if (cleanPlaylistItemKey.isNotEmpty()) return cleanPlaylistItemKey
    return playbackUri.trim()
}
