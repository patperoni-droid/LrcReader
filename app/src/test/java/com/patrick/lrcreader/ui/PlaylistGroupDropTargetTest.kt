package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistGroupDropTargetTest {

    @Test
    fun findHeaderDropTargetKey_returnsHeaderWhenDragOverHeader() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val songs = listOf(headerA, "t1", "t2", endA, "x1")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "t2", start = 112, endExclusive = 168),
            ListViewportItem(key = "x1", start = 168, endExclusive = 224)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 20f,
            draggedItemKey = "x1"
        )

        assertEquals(headerA, target)
    }

    @Test
    fun findHeaderDropTargetKey_returnsHeaderWithinPaddingZone() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val songs = listOf(headerA, "t1", "t2", endA, "x1")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "t2", start = 112, endExclusive = 168),
            ListViewportItem(key = "x1", start = 168, endExclusive = 224)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 64f,
            draggedItemKey = "x1",
            headerPaddingPx = 12f
        )

        assertEquals(headerA, target)
    }

    @Test
    fun findHeaderDropTargetKey_returnsNullWhenOverTrackOutsideGroup() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val songs = listOf(headerA, "t1", endA, "x1")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "x1", start = 112, endExclusive = 168)
        )

        val target = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 140f,
            draggedItemKey = "t1",
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

    @Test
    fun dragFromBelow_hoverGroupedItem_dropAddsIntoSameGroup() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val songs = mutableListOf(headerA, "t1", "t2", endA, "x1", "x2")
        val viewport = listOf(
            ListViewportItem(key = headerA, start = 0, endExclusive = 56),
            ListViewportItem(key = "t1", start = 56, endExclusive = 112),
            ListViewportItem(key = "t2", start = 112, endExclusive = 168),
            ListViewportItem(key = "x1", start = 168, endExclusive = 224),
            ListViewportItem(key = "x2", start = 224, endExclusive = 280)
        )

        val targetHeader = findHeaderDropTargetKey(
            songs = songs,
            viewportItems = viewport,
            dragY = 130f,
            draggedItemKey = "x2"
        )
        val fromIndex = songs.indexOf("x2")
        val headerIndex = songs.indexOf(targetHeader)
        moveItemIntoGroup(
            items = songs,
            fromIndex = fromIndex,
            headerIndex = headerIndex,
            mode = "BOTTOM"
        )

        assertEquals(headerA, targetHeader)
        assertEquals(listOf(headerA, "t1", "t2", "x2", endA, "x1"), songs)
    }
}
