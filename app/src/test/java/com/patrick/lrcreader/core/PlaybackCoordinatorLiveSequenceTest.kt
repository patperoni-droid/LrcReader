package com.patrick.lrcreader.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackCoordinatorLiveSequenceTest {

    @Before
    fun setUp() {
        resetCoordinator()
    }

    @After
    fun tearDown() {
        resetCoordinator()
    }

    @Test
    fun rapidSourceSwitch_preservesMutualExclusionAndStopCallbacks() {
        var stopPlayerCount = 0
        var stopDjCount = 0
        var stopFillerCount = 0

        PlaybackCoordinator.stopPlayer = { stopPlayerCount++ }
        PlaybackCoordinator.stopDj = { stopDjCount++ }
        PlaybackCoordinator.stopFiller = { stopFillerCount++ }

        PlaybackCoordinator.requestStartPlayer()
        assertTrue(PlaybackCoordinator.isMainPlaying)
        assertEquals(0, stopPlayerCount)
        assertEquals(0, stopDjCount)
        assertEquals(0, stopFillerCount)

        PlaybackCoordinator.requestStartDj()
        assertTrue(PlaybackCoordinator.isMainPlaying)
        assertEquals(1, stopPlayerCount)
        assertEquals(0, stopDjCount)
        assertEquals(0, stopFillerCount)

        PlaybackCoordinator.requestStartFiller()
        assertFalse(PlaybackCoordinator.isMainPlaying)
        assertEquals(1, stopPlayerCount)
        assertEquals(1, stopDjCount)
        assertEquals(0, stopFillerCount)

        PlaybackCoordinator.requestStartPlayer()
        assertTrue(PlaybackCoordinator.isMainPlaying)
        assertEquals(1, stopPlayerCount)
        assertEquals(1, stopDjCount)
        assertEquals(1, stopFillerCount)
    }

    @Test
    fun repeatedRequestSameSource_doesNotTriggerExtraStops() {
        var stopPlayerCount = 0
        var stopDjCount = 0
        var stopFillerCount = 0

        PlaybackCoordinator.stopPlayer = { stopPlayerCount++ }
        PlaybackCoordinator.stopDj = { stopDjCount++ }
        PlaybackCoordinator.stopFiller = { stopFillerCount++ }

        PlaybackCoordinator.requestStartPlayer()
        PlaybackCoordinator.requestStartPlayer()
        PlaybackCoordinator.requestStartPlayer()

        assertTrue(PlaybackCoordinator.isMainPlaying)
        assertEquals(0, stopPlayerCount)
        assertEquals(0, stopDjCount)
        assertEquals(0, stopFillerCount)

        PlaybackCoordinator.requestStartFiller()
        PlaybackCoordinator.requestStartFiller()

        assertFalse(PlaybackCoordinator.isMainPlaying)
        assertEquals(1, stopPlayerCount)
        assertEquals(0, stopDjCount)
        assertEquals(0, stopFillerCount)
    }

    private fun resetCoordinator() {
        PlaybackCoordinator.stopPlayer = null
        PlaybackCoordinator.stopDj = null
        PlaybackCoordinator.stopFiller = null
        PlaybackCoordinator.clearNextTrack(reason = "test-reset")
        PlaybackCoordinator.requestStartPlayer()
        PlaybackCoordinator.onPlayerStop()
    }
}
