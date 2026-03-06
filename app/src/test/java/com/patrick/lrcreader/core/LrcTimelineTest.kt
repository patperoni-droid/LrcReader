package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcTimelineTest {

    @Test
    fun findActiveLrcIndex_returnsLastTaggedBeforePosition() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "A"),
            LrcLine(timeMs = 2_000L, text = "B"),
            LrcLine(timeMs = 3_000L, text = "C")
        )

        assertEquals(1, findActiveLrcIndex(lines, positionMs = 2_500L))
    }

    @Test
    fun findActiveLrcIndex_returnsMinusOneBeforeFirstTag() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "A"),
            LrcLine(timeMs = 2_000L, text = "B")
        )

        assertEquals(-1, findActiveLrcIndex(lines, positionMs = 800L))
    }

    @Test
    fun findActiveLrcIndex_returnsZeroWhenNoTags() {
        val lines = listOf(
            LrcLine(timeMs = 0L, text = "A"),
            LrcLine(timeMs = 0L, text = "B")
        )

        assertEquals(0, findActiveLrcIndex(lines, positionMs = 5_000L))
    }

    @Test
    fun buildChordsWindow_returnsExpectedSlices() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "Am"),
            LrcLine(timeMs = 2_000L, text = "F"),
            LrcLine(timeMs = 3_000L, text = "C"),
            LrcLine(timeMs = 4_000L, text = "G")
        )

        val window = buildChordsWindow(lines, activeIndex = 1, nextCount = 3)

        assertEquals("Am", window.previous?.text)
        assertEquals("F", window.current?.text)
        assertEquals(listOf("C", "G"), window.next.map { it.text })
    }

    @Test
    fun buildChordsWindow_handlesEmptyList() {
        val window = buildChordsWindow(emptyList(), activeIndex = 0, nextCount = 3)
        assertNull(window.previous)
        assertNull(window.current)
        assertEquals(emptyList<LrcLine>(), window.next)
    }
}
