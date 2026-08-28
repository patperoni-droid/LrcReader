package com.patrick.lrcreader.core

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLiveRuntimeTest {

    @Test
    fun newLiveList_includesCurrentTrackWhilePlaybackIsActive() {
        assertTrue(
            shouldIncludeCurrentTrackInNewLiveList(
                createdNow = true,
                isPlaying = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun newLiveList_excludesRememberedTrackAfterPlaybackEnded() {
        assertFalse(
            shouldIncludeCurrentTrackInNewLiveList(
                createdNow = true,
                isPlaying = false,
                playbackState = Player.STATE_ENDED
            )
        )
        assertFalse(
            shouldIncludeCurrentTrackInNewLiveList(
                createdNow = true,
                isPlaying = false,
                playbackState = Player.STATE_IDLE
            )
        )
    }

    @Test
    fun newLiveList_preservesPausedReadyTrackAsCurrentSession() {
        assertTrue(
            shouldIncludeCurrentTrackInNewLiveList(
                createdNow = true,
                isPlaying = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun deletingOwnedLiveGroup_stopsOnlyItsChainAndPreservesDefineNext() {
        var defineNextUri: String? = "smp://defined"
        var chainActive = true
        var stopCount = 0

        val stopped = stopDeletedLiveChainIfOwned(
            chainPlaylist = "Set",
            liveGroupHeaderKey = "live-header",
            deletedPlaylist = "Set",
            deletedGroupHeaderKey = "live-header",
            stopChain = {
                chainActive = false
                stopCount++
            }
        )

        assertTrue(stopped)
        assertFalse(chainActive)
        assertEquals(1, stopCount)
        assertEquals("smp://defined", defineNextUri)
    }

    @Test
    fun deletingOrdinaryOrUnrelatedGroup_doesNotStopLiveChain() {
        var stopCount = 0

        assertFalse(
            stopDeletedLiveChainIfOwned(
                chainPlaylist = "Set",
                liveGroupHeaderKey = "live-header",
                deletedPlaylist = "Set",
                deletedGroupHeaderKey = "ordinary-header",
                stopChain = { stopCount++ }
            )
        )
        assertFalse(
            stopDeletedLiveChainIfOwned(
                chainPlaylist = "Set",
                liveGroupHeaderKey = "live-header",
                deletedPlaylist = "Other set",
                deletedGroupHeaderKey = "live-header",
                stopChain = { stopCount++ }
            )
        )
        assertEquals(0, stopCount)
    }
}
