package com.patrick.lrcreader.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsLayoutTest {

    @Test
    fun `define next field requires live console mode`() {
        assertFalse(
            shouldShowNextTrackField(
                availableWidth = 600.dp,
                compact = false,
                liveConsoleMode = false
            )
        )
    }

    @Test
    fun `compact controls keep their existing row below safe width`() {
        assertFalse(
            shouldShowNextTrackField(
                availableWidth = 559.dp,
                compact = true,
                liveConsoleMode = true
            )
        )
        assertTrue(
            shouldShowNextTrackField(
                availableWidth = 560.dp,
                compact = true,
                liveConsoleMode = true
            )
        )
    }

    @Test
    fun `standard controls use their own safe width`() {
        assertFalse(
            shouldShowNextTrackField(
                availableWidth = 509.dp,
                compact = false,
                liveConsoleMode = true
            )
        )
        assertTrue(
            shouldShowNextTrackField(
                availableWidth = 510.dp,
                compact = false,
                liveConsoleMode = true
            )
        )
    }
}
