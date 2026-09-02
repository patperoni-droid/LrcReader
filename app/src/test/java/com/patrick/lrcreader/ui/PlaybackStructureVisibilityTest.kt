package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStructureVisibilityTest {

    private val structureMode = PlaybackProgressMode.Structure(
        model = PlaybackStructureModel(emptyList())
    )

    @Test
    fun variantArrangementEditor_hidesOnlyThePlaybackStructureTrack() {
        assertFalse(
            shouldShowPlaybackProgressTrack(
                playbackProgressMode = structureMode,
                isArrangementEditorVisible = true
            )
        )
    }

    @Test
    fun variantReadingView_keepsThePlaybackStructureTrack() {
        assertTrue(
            shouldShowPlaybackProgressTrack(
                playbackProgressMode = structureMode,
                isArrangementEditorVisible = false
            )
        )
    }

    @Test
    fun variantArrangementToReadingView_restoresThePlaybackStructureTrack() {
        val visibility = listOf(true, false).map { isArrangementEditorVisible ->
            shouldShowPlaybackProgressTrack(structureMode, isArrangementEditorVisible)
        }

        assertFalse(visibility[0])
        assertTrue(visibility[1])
    }

    @Test
    fun variantReadingViewToArrangement_hidesThePlaybackStructureTrack() {
        val visibility = listOf(false, true).map { isArrangementEditorVisible ->
            shouldShowPlaybackProgressTrack(structureMode, isArrangementEditorVisible)
        }

        assertTrue(visibility[0])
        assertFalse(visibility[1])
    }

    @Test
    fun nonArrangementTimelineEditor_keepsTheVariantPlaybackStructureTrack() {
        assertTrue(
            shouldShowPlaybackProgressTrack(
                playbackProgressMode = structureMode,
                isArrangementEditorVisible = false
            )
        )
    }

    @Test
    fun normalOrParentArrangementEditor_keepsLinearTransport() {
        assertTrue(
            shouldShowPlaybackProgressTrack(
                playbackProgressMode = PlaybackProgressMode.Linear,
                isArrangementEditorVisible = true
            )
        )
    }

    @Test
    fun phoneAndTabletShareTheSameTypedEditorVisibilityRule() {
        val phoneVisibility = shouldShowPlaybackProgressTrack(
            playbackProgressMode = structureMode,
            isArrangementEditorVisible = true
        )
        val tabletVisibility = shouldShowPlaybackProgressTrack(
            playbackProgressMode = structureMode,
            isArrangementEditorVisible = true
        )

        assertFalse(phoneVisibility)
        assertFalse(tabletVisibility)
    }
}
