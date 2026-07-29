package com.patrick.lrcreader.ui

import com.patrick.lrcreader.smp.LiveArrangementOccurrence
import com.patrick.lrcreader.smp.LiveArrangementPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStructureModelAdapterTest {

    @Test
    fun `maps live occurrence order labels keys and duration proportions`() {
        val plan = LiveArrangementPlan(
            occurrences = listOf(
                LiveArrangementOccurrence("variant:0:0", "A", 1_000L),
                LiveArrangementOccurrence("variant:1:0", "Refrain", 2_000L),
                LiveArrangementOccurrence("variant:1:1", "Refrain", 2_000L)
            )
        )

        val model = requireNotNull(PlaybackStructureModelAdapter.from(plan))

        assertEquals(
            listOf("variant:0:0", "variant:1:0", "variant:1:1"),
            model.segments.map { it.key }
        )
        assertEquals(listOf("A", "Refrain", "Refrain"), model.segments.map { it.label })
        assertEquals(0.2f, model.segments[0].fraction, 0.0001f)
        assertEquals(0.4f, model.segments[1].fraction, 0.0001f)
        assertEquals(0.4f, model.segments[2].fraction, 0.0001f)
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
}
