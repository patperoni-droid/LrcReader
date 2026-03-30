package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPlaybackKeysTest {

    @Test
    fun keepsPlaylistItemKeyForSmpItems() {
        assertEquals(
            "smp://abc123",
            canonicalPlaylistPlaybackKey(
                playlistItemKey = "smp://abc123",
                playbackUri = "file:///storage/emulated/0/Music/abc123.mp3"
            )
        )
    }

    @Test
    fun fallsBackToPlaybackUriWhenPlaylistKeyMissing() {
        assertEquals(
            "file:///storage/emulated/0/Music/live.mp3",
            canonicalPlaylistPlaybackKey(
                playlistItemKey = null,
                playbackUri = "file:///storage/emulated/0/Music/live.mp3"
            )
        )
    }

    @Test
    fun trimsPlaylistItemKey() {
        assertEquals(
            "smp://trimmed",
            canonicalPlaylistPlaybackKey(
                playlistItemKey = "  smp://trimmed  ",
                playbackUri = "file:///ignored.mp3"
            )
        )
    }
}
