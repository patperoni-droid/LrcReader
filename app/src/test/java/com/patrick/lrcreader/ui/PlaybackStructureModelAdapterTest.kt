package com.patrick.lrcreader.ui

import androidx.compose.ui.graphics.Color
import com.patrick.lrcreader.smp.LiveArrangementOccurrence
import com.patrick.lrcreader.smp.LiveArrangementPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStructureModelAdapterTest {

    @Test
    fun `groups live repetitions like the arrangement window`() {
        val plan = LiveArrangementPlan(
            occurrences = listOf(
                LiveArrangementOccurrence("variant:0:0", "A", 1_000L, "red"),
                LiveArrangementOccurrence("variant:1:0", "Refrain", 2_000L, "blue"),
                LiveArrangementOccurrence("variant:1:1", "Refrain", 2_000L, "blue")
            )
        )

        val model = requireNotNull(PlaybackStructureModelAdapter.from(plan))

        assertEquals(
            listOf("variant:0:0", "variant:1:0"),
            model.segments.map { it.key }
        )
        assertEquals(listOf("A", "Refrain ×2"), model.segments.map { it.label })
        assertEquals(
            listOf(Color(0xFF6D2A2A), Color(0xFF244A73)),
            model.segments.map { it.color }
        )
        assertEquals(0.2f, model.segments[0].fraction, 0.0001f)
        assertEquals(0.8f, model.segments[1].fraction, 0.0001f)
    }

    @Test
    fun `returns no graphical model for an empty live plan`() {
        assertNull(
            PlaybackStructureModelAdapter.from(
                LiveArrangementPlan(occurrences = emptyList())
            )
        )
    }

    @Test
    fun `keeps the real proportion of very short and very long occurrences`() {
        val plan = LiveArrangementPlan(
            occurrences = listOf(
                LiveArrangementOccurrence("variant:0:0", "Court", 1L),
                LiveArrangementOccurrence("variant:1:0", "Long", 99_999L)
            )
        )

        val model = requireNotNull(PlaybackStructureModelAdapter.from(plan))

        assertEquals(0.00001f, model.segments[0].fraction, 0.000001f)
        assertEquals(0.99999f, model.segments[1].fraction, 0.000001f)
    }

    @Test
    fun `does not merge distinct arrangement entries with the same presentation`() {
        val plan = LiveArrangementPlan(
            occurrences = listOf(
                LiveArrangementOccurrence("variant:0:0", "A", 1_000L, "red"),
                LiveArrangementOccurrence("variant:1:0", "A", 1_000L, "red")
            )
        )

        val model = requireNotNull(PlaybackStructureModelAdapter.from(plan))

        assertEquals(2, model.segments.size)
        assertEquals(listOf("A", "A"), model.segments.map { it.label })
    }

    @Test
    fun `does not infer grouping from malformed occurrence keys`() {
        val plan = LiveArrangementPlan(
            occurrences = listOf(
                LiveArrangementOccurrence("legacy-key", "A", 1_000L),
                LiveArrangementOccurrence("legacy-key", "A", 1_000L)
            )
        )

        val model = requireNotNull(PlaybackStructureModelAdapter.from(plan))

        assertEquals(2, model.segments.size)
    }
}
