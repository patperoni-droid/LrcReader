package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePlaybackRoutingTest {

    @Test
    fun arrangementTransportRouting_isSharedByPhoneAndTabletEditors() {
        assertTrue(
            shouldRouteArrangementPreviewToPlaybackControl(
                tabletArrangementLayout = true,
                startInGridSetup = false
            )
        )
        assertTrue(
            shouldRouteArrangementPreviewToPlaybackControl(
                tabletArrangementLayout = false,
                startInGridSetup = true
            )
        )
        assertFalse(
            shouldRouteArrangementPreviewToPlaybackControl(
                tabletArrangementLayout = false,
                startInGridSetup = false
            )
        )
    }

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

    @Test
    fun arrangementStructurePlayback_usesDirectModeByDefaultOnPhone() {
        assertTrue(
            shouldUseDirectArrangementStructurePlayback(
                tabletArrangementLayout = false,
                phoneCompatibilityModeEnabled = false
            )
        )
    }

    @Test
    fun arrangementStructurePlayback_usesHistoricalPipelineInPhoneCompatibilityMode() {
        assertFalse(
            shouldUseDirectArrangementStructurePlayback(
                tabletArrangementLayout = false,
                phoneCompatibilityModeEnabled = true
            )
        )
    }

    @Test
    fun arrangementStructurePlayback_keepsTabletDirect() {
        assertTrue(
            shouldUseDirectArrangementStructurePlayback(
                tabletArrangementLayout = true,
                phoneCompatibilityModeEnabled = true
            )
        )
    }
}
