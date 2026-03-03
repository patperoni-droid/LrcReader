package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistGroupDropTargetTest {

    @Test
    fun findHeaderDropTargetKey_returnsHeaderWhenDragOverHeader() {
        val headerA = buildGroupHeader("A")
        val songs = listOf(headerA, "t1", "t2")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "t2", start = 112, endExclusive = 168)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 20f,
            draggedItemKey = "t2"
        )

        assertEquals(headerA, target)
    }

    @Test
    fun findHeaderDropTargetKey_returnsHeaderWithinPaddingZone() {
        val headerA = buildGroupHeader("A")
        val songs = listOf(headerA, "t1", "t2")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "t2", start = 112, endExclusive = 168)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 64f,
            draggedItemKey = "t2",
            headerPaddingPx = 12f
        )

        assertEquals(headerA, target)
    }

    @Test
    fun findHeaderDropTargetKey_returnsNullWhenOverTrack() {
        val headerA = buildGroupHeader("A")
        val songs = listOf(headerA, "t1", "t2")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 70f,
            draggedItemKey = "t2",
            headerPaddingPx = 12f
        )

        assertNull(target)
    }

    @Test
    fun findHeaderDropTargetKey_returnsNullWhenDraggingHeader() {
        val headerA = buildGroupHeader("A")
        val headerB = buildGroupHeader("B")
        val songs = listOf(headerA, "t1", headerB)
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = headerB, start = 112, endExclusive = 168)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 20f,
            draggedItemKey = headerB
        )

        assertNull(target)
    }
}
