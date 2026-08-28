package com.patrick.lrcreader.core

import androidx.media3.common.Player

internal fun shouldIncludeCurrentTrackInNewLiveList(
    createdNow: Boolean,
    isPlaying: Boolean,
    playbackState: Int
): Boolean {
    if (!createdNow) return false
    return isPlaying ||
        playbackState == Player.STATE_BUFFERING ||
        playbackState == Player.STATE_READY
}

internal fun stopDeletedLiveChainIfOwned(
    chainPlaylist: String?,
    liveGroupHeaderKey: String?,
    deletedPlaylist: String,
    deletedGroupHeaderKey: String,
    stopChain: () -> Unit
): Boolean {
    val ownsDeletedLiveGroup =
        chainPlaylist == deletedPlaylist && liveGroupHeaderKey == deletedGroupHeaderKey
    if (ownsDeletedLiveGroup) {
        stopChain()
    }
    return ownsDeletedLiveGroup
}
