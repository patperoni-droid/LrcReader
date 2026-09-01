package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementNavigationVisibilityTest {

    @Test
    fun lyricsWithLinearPlayback_showsArrangementNavigation() {
        assertTrue(
            shouldShowArrangementNavigationRow(
                isLyricsView = true,
                playbackProgressMode = PlaybackProgressMode.Linear
            )
        )
    }

    @Test
    fun lyricsWithNativeStructure_hidesArrangementNavigation() {
        assertFalse(
            shouldShowArrangementNavigationRow(
                isLyricsView = true,
                playbackProgressMode = PlaybackProgressMode.Structure(
                    model = PlaybackStructureModel(emptyList())
                )
            )
        )
    }

    @Test
    fun nonLyricsView_hidesArrangementNavigation() {
        assertFalse(
            shouldShowArrangementNavigationRow(
                isLyricsView = false,
                playbackProgressMode = PlaybackProgressMode.Linear
            )
        )
    }
}
