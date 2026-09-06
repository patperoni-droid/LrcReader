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
            minimumSegmentWidthDp = PlaybackProgressBarDefaults.StructureSegmentMinWidth.value
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
            minimumSegmentWidthDp = PlaybackProgressBarDefaults.StructureSegmentMinWidth.value
        )

        assertTrue(widths.all { width -> width == arrangementTrackBlockWidthDp(null) })
        assertEquals(1_680f, widths.sum(), 0f)
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
            minimumSegmentWidthDp = PlaybackProgressBarDefaults.StructureSegmentMinWidth.value
        )

        assertEquals(252f, playbackStructurePlayheadOffsetDp(model, widths, 0.85f), 0.001f)
        assertEquals(420f, playbackStructurePlayheadOffsetDp(model, widths, 0.95f), 0.001f)
    }

    @Test
    fun `minimum touch width still leaves the label visible inside the card`() {
        val visualWidthDp = PlaybackProgressBarDefaults.StructureSegmentMinWidth.value -
            2f * ArrangementTrackSegmentVisualInset.value

        assertTrue(shouldShowPlaybackStructureSegmentLabel(visualWidthDp))
    }

    @Test
    fun `active and queued backgrounds keep their emphasis above normal`() {
        assertEquals(
            Color.Red.copy(alpha = 1f),
            arrangementTrackOccurrenceContainerColor(
                color = Color.Red,
                isMuted = false,
                isActive = true,
                isQueued = false
            )
        )
        assertEquals(
            Color.Red.copy(alpha = 0.90f),
            arrangementTrackOccurrenceContainerColor(
                color = Color.Red,
                isMuted = false,
                isActive = false,
                isQueued = true
            )
        )
    }

    @Test
    fun `normal structure segment uses the shared colored border`() {
        val border = arrangementTrackSegmentBorder(
            color = Color.Red,
            isQueued = false,
            isLooped = false
        )

        assertEquals(ArrangementTrackSegmentNormalBorderWidth, border.width)
        assertEquals(Color.Red.copy(alpha = 0.82f), border.color)
    }

    @Test
    fun `queued border replaces the normal border`() {
        val border = arrangementTrackSegmentBorder(
            color = Color.Red,
            isQueued = true,
            isLooped = false
        )

        assertEquals(ArrangementTrackSegmentStateBorderWidth, border.width)
        assertEquals(ArrangementTrackQueuedBorderColor, border.color)
    }

    @Test
    fun `loop border has priority over queued border`() {
        val border = arrangementTrackSegmentBorder(
            color = Color.Red,
            isQueued = true,
            isLooped = true
        )

        assertEquals(ArrangementTrackSegmentStateBorderWidth, border.width)
        assertEquals(
            androidx.compose.ui.graphics.lerp(Color.Red, Color.White, 0.42f),
            border.color
        )
    }
}
