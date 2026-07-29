package com.patrick.lrcreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStructureProgressTest {

    private val repeatedStructure = PlaybackStructureModel(
        segments = listOf(
            PlaybackStructureSegment("variant:0:0", "A", 0.25f),
            PlaybackStructureSegment("variant:0:1", "A", 0.25f),
            PlaybackStructureSegment("variant:1:0", "B", 0.50f)
        )
    )

    @Test
    fun `selects each repeated occurrence from the global progress`() {
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0f))
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.249f))
        assertEquals(1, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.25f))
        assertEquals(1, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.499f))
        assertEquals(2, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.50f))
        assertEquals(2, findActivePlaybackStructureSegmentIndex(repeatedStructure, 1f))
    }

    @Test
    fun `clamps seeks outside the progress range`() {
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, -1f))
        assertEquals(2, findActivePlaybackStructureSegmentIndex(repeatedStructure, 2f))
    }

    @Test
    fun `returns no active segment for an empty structure`() {
        assertEquals(
            -1,
            findActivePlaybackStructureSegmentIndex(
                PlaybackStructureModel(emptyList()),
                0.5f
            )
        )
    }
}
