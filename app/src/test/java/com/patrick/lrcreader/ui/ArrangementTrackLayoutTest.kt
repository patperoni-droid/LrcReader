package com.patrick.lrcreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTrackLayoutTest {

    @Test
    fun phoneDefaultSegmentName_usesCompactAlphabeticSequence() {
        assertEquals("A", arrangementPhoneDefaultSegmentName(1))
        assertEquals("Z", arrangementPhoneDefaultSegmentName(26))
        assertEquals("AA", arrangementPhoneDefaultSegmentName(27))
        assertEquals("AB", arrangementPhoneDefaultSegmentName(28))
    }

    @Test
    fun blockWidth_keepsShortSegmentsTouchable() {
        assertEquals(168f, arrangementTrackBlockWidthDp(null), 0f)
        assertEquals(168f, arrangementTrackBlockWidthDp(2_000L), 0f)
    }

    @Test
    fun blockWidth_reflectsSegmentDurationAtFixedScale() {
        val thirtySeconds = arrangementTrackBlockWidthDp(30_000L)
        val sixtySeconds = arrangementTrackBlockWidthDp(60_000L)

        assertEquals(168f, thirtySeconds, 0f)
        assertEquals(300f, sixtySeconds, 0f)
        assertTrue(sixtySeconds > thirtySeconds)
    }

    @Test
    fun blockWidth_capsPathologicalDurations() {
        assertEquals(600f, arrangementTrackBlockWidthDp(10 * 60_000L), 0f)
    }

    @Test
    fun playheadOffset_mapsTheActiveRepeatInsideItsLogicalBlock() {
        val items = listOf(
            ArrangementListItem(id = "intro", title = "Intro", durationMs = 2_000L),
            ArrangementListItem(
                id = "chorus",
                title = "Refrain",
                durationMs = 60_000L,
                repeatCount = 3
            )
        )

        val offsetDp = arrangementTrackPlayheadOffsetDp(
            items = items,
            playhead = ArrangementTrackPlayhead(
                itemId = "chorus",
                repeatIndex = 1,
                repeatCount = 3,
                segmentProgressFraction = 0.5f
            )
        )

        assertEquals(334f, offsetDp ?: -1f, 0f)
    }

    @Test
    fun playheadOffset_startsAtTheTrackContentPadding() {
        val offsetDp = arrangementTrackPlayheadOffsetDp(
            items = listOf(
                ArrangementListItem(id = "intro", title = "Intro", durationMs = 5_000L)
            ),
            playhead = ArrangementTrackPlayhead(
                itemId = "intro",
                repeatIndex = 0,
                repeatCount = 1,
                segmentProgressFraction = 0f
            )
        )

        assertEquals(8f, offsetDp ?: -1f, 0f)
    }

    @Test
    fun playheadOffset_usesMeasuredAdaptivePhoneWidths() {
        val items = listOf(
            ArrangementListItem(id = "a", title = "A", durationMs = 30_000L),
            ArrangementListItem(id = "refrain", title = "Refrain", durationMs = 30_000L)
        )

        val offsetDp = arrangementTrackPlayheadOffsetDp(
            items = items,
            playhead = ArrangementTrackPlayhead(
                itemId = "refrain",
                repeatIndex = 0,
                repeatCount = 1,
                segmentProgressFraction = 0.5f
            ),
            itemWidthsDp = mapOf("a" to 48f, "refrain" to 80f)
        )

        assertEquals(104f, offsetDp ?: -1f, 0f)
    }

    @Test
    fun boundaryPlayhead_mapsTheEndOfTheTrack() {
        val items = listOf(
            ArrangementListItem(id = "intro", title = "Intro", durationMs = 2_000L),
            ArrangementListItem(id = "verse", title = "Verse", durationMs = 2_000L)
        )

        val playhead = arrangementTrackPlayheadAtBoundary(items, items.size)
        val offsetDp = arrangementTrackPlayheadOffsetDp(items, playhead)

        assertEquals(352f, offsetDp ?: -1f, 0f)
    }

    @Test
    fun nearestBoundary_quantizesTouchBetweenSegments() {
        val items = listOf(
            ArrangementListItem(id = "intro", title = "Intro", durationMs = 2_000L),
            ArrangementListItem(id = "verse", title = "Verse", durationMs = 2_000L)
        )

        assertEquals(0, arrangementTrackNearestBoundaryIndex(items, 20f))
        assertEquals(1, arrangementTrackNearestBoundaryIndex(items, 180f))
        assertEquals(2, arrangementTrackNearestBoundaryIndex(items, 340f))
    }

    @Test
    fun nearestBoundary_usesMeasuredAdaptivePhoneWidths() {
        val items = listOf(
            ArrangementListItem(id = "a", title = "A", durationMs = 30_000L),
            ArrangementListItem(id = "refrain", title = "Refrain", durationMs = 30_000L)
        )
        val widths = mapOf("a" to 48f, "refrain" to 80f)

        assertEquals(0, arrangementTrackNearestBoundaryIndex(items, 20f, widths))
        assertEquals(1, arrangementTrackNearestBoundaryIndex(items, 60f, widths))
        assertEquals(2, arrangementTrackNearestBoundaryIndex(items, 140f, widths))
    }
}
