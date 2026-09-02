package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ArrangementNavigationVisibilityTest {

    @Test
    fun normalSongWithArrangement_hidesArrangementNavigationAndKeepsLinearTransport() {
        val playbackProgressMode = PlaybackProgressMode.Linear

        assertFalse(shouldShowArrangementNavigationRow(playbackProgressMode))
        assertSame(PlaybackProgressMode.Linear, playbackProgressMode)
    }

    @Test
    fun parentWithVariants_hidesArrangementNavigationAndKeepsLinearTransport() {
        val playbackProgressMode = PlaybackProgressMode.Linear

        assertFalse(shouldShowArrangementNavigationRow(playbackProgressMode))
        assertSame(PlaybackProgressMode.Linear, playbackProgressMode)
    }

    @Test
    fun songWithoutArrangement_keepsTheSameLinearTransportWithoutExtraRow() {
        val playbackProgressMode = PlaybackProgressMode.Linear

        assertFalse(shouldShowArrangementNavigationRow(playbackProgressMode))
        assertSame(PlaybackProgressMode.Linear, playbackProgressMode)
    }

    @Test
    fun variantKeepsItsStructureTrackWithoutASecondArrangementRow() {
        val structureModel = PlaybackStructureModel(emptyList())
        val playbackProgressMode = PlaybackProgressMode.Structure(model = structureModel)

        assertFalse(shouldShowArrangementNavigationRow(playbackProgressMode))
        assertSame(structureModel, playbackProgressMode.model)
    }

    @Test
    fun hiddenArrangementNavigationDoesNotReserveAPlayerLayoutRow() {
        assertFalse(
            shouldShowArrangementNavigationRow(PlaybackProgressMode.Linear)
        )
    }
}
