package com.patrick.lrcreader.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStructureProgressTest {

    private val repeatedStructure = PlaybackStructureModel(
        segments = listOf(
            PlaybackStructureSegment("variant:0:0", "A ×2", 0.50f, Color.Red),
            PlaybackStructureSegment("variant:1:0", "B", 0.50f, Color.Blue)
        )
    )

    @Test
    fun `keeps the grouped repeated segment active throughout its total duration`() {
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0f))
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.249f))
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.25f))
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.499f))
        assertEquals(1, findActivePlaybackStructureSegmentIndex(repeatedStructure, 0.50f))
        assertEquals(1, findActivePlaybackStructureSegmentIndex(repeatedStructure, 1f))
    }

    @Test
    fun `clamps seeks outside the progress range`() {
        assertEquals(0, findActivePlaybackStructureSegmentIndex(repeatedStructure, -1f))
        assertEquals(1, findActivePlaybackStructureSegmentIndex(repeatedStructure, 2f))
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

    @Test
    fun `keeps current proportions when every segment is already touchable`() {
        val widths = playbackStructureSegmentWidthsDp(
            model = repeatedStructure,
            viewportWidthDp = 400f,
            minimumSegmentWidthDp = 48f
        )

        assertEquals(listOf(200f, 200f), widths)
    }

    @Test
    fun `expands the track instead of compressing small segments`() {
        val model = PlaybackStructureModel(
            segments = (0 until 10).map { index ->
                PlaybackStructureSegment(
                    key = index.toString(),
                    label = index.toString(),
                    fraction = 0.1f,
                    color = Color.Gray
                )
            }
        )

        val widths = playbackStructureSegmentWidthsDp(
            model = model,
            viewportWidthDp = 320f,
            minimumSegmentWidthDp = 48f
        )

        assertTrue(widths.all { width -> width == 48f })
        assertEquals(480f, widths.sum(), 0f)
    }

    @Test
    fun `maps the playhead through expanded segment geometry`() {
        val model = PlaybackStructureModel(
            segments = listOf(
                PlaybackStructureSegment("a", "A", 0.8f, Color.Gray),
                PlaybackStructureSegment("b", "B", 0.1f, Color.Gray),
                PlaybackStructureSegment("c", "C", 0.1f, Color.Gray)
            )
        )
        val widths = playbackStructureSegmentWidthsDp(
            model = model,
            viewportWidthDp = 100f,
            minimumSegmentWidthDp = 48f
        )

        assertEquals(104f, playbackStructurePlayheadOffsetDp(model, widths, 0.85f), 0.001f)
        assertEquals(152f, playbackStructurePlayheadOffsetDp(model, widths, 0.95f), 0.001f)
    }
}
