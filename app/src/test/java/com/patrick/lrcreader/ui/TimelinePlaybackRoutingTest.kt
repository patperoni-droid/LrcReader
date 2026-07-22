package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePlaybackRoutingTest {

    @Test
    fun stoppingLoop_doesNotHideTransportWhileStructureStillPlays() {
        assertTrue(
            isTimelineSecondaryPlaybackActive(
                structurePlaybackActive = true,
                arrangementLoopPreviewActive = false
            )
        )
        assertFalse(
            isTimelineSecondaryPlaybackActive(
                structurePlaybackActive = false,
                arrangementLoopPreviewActive = false
            )
        )
    }

    @Test
    fun transportOverride_remainsActiveForAnySecondaryPlayback() {
        assertTrue(
            shouldUseTimelinePlaybackOverride(
                segmentTargeted = false,
                secondaryPlaybackActive = true
            )
        )
        assertFalse(
            shouldUseTimelinePlaybackOverride(
                segmentTargeted = false,
                secondaryPlaybackActive = false
            )
        )
        assertTrue(
            shouldUseTimelinePlaybackOverride(
                segmentTargeted = true,
                secondaryPlaybackActive = false
            )
        )
    }
}
