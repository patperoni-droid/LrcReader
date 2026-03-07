package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class PlaylistGroupMoveTest {

    @Test
    fun createGroupEmpty_insertsStartAndEndWithoutCapturingTracks() {
        val headerA = buildGroupHeader("A")
        val headerAEnd = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val headerBEnd = buildGroupEnd(getGroupUuid(headerB)!!)
        val items = mutableListOf("x1", "x2", "x3")

        items.add(1, headerA)
        items.add(2, headerAEnd)
        items.add(4, headerB)
        items.add(5, headerBEnd)

        assertEquals(listOf("x1", headerA, headerAEnd, "x2", headerB, headerBEnd, "x3"), items)
    }

    @Test
    fun moveTrackIntoFirstGroup_placesTrackBetweenStartAndEnd() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val endB = buildGroupEnd(getGroupUuid(headerB)!!)
        val items = mutableListOf(headerA, "t1", "t2", endA, headerB, "t3", endB)

        moveItemIntoGroup(items, fromIndex = 5, headerIndex = 0, mode = "TOP")

        assertEquals(listOf(headerA, "t1", "t2", "t3", endA, headerB, endB), items)
    }

    @Test
    fun moveHeaderIntoHeader_isIgnored() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val endB = buildGroupEnd(getGroupUuid(headerB)!!)
        val items = mutableListOf(headerA, "t1", endA, headerB, "t3", endB)
        val before = items.toList()

        moveItemIntoGroup(items, fromIndex = 3, headerIndex = 0, mode = "TOP")

        assertEquals(before, items)
    }

    @Test
    fun moveGroupAsBlock_keepsStartContentEndContiguous() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val endB = buildGroupEnd(getGroupUuid(headerB)!!)

        val items = mutableListOf(headerA, "t1", "t2", endA, "x1", "x2", headerB, "y1", endB)
        val rangeA = findGroupBlockRange(items, 0)
        moveBlock(items, rangeA, 6)

        assertEquals(listOf("x1", "x2", headerA, "t1", "t2", endA, headerB, "y1", endB), items)
    }

    @Test
    fun moveGroupBAboveGroupA_swapsWholeBlocks() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val endB = buildGroupEnd(getGroupUuid(headerB)!!)

        val items = mutableListOf(headerA, "t1", "t2", endA, "x1", "x2", headerB, "y1", endB)
        val rangeB = findGroupBlockRange(items, 6)
        moveBlock(items, rangeB, 0)

        assertEquals(listOf(headerB, "y1", endB, headerA, "t1", "t2", endA, "x1", "x2"), items)
    }

    @Test
    fun isItemInsideGroup_detectsOnlyTracksBetweenStartAndEnd() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val items = listOf(headerA, "t1", "t2", endA, "x1")

        assertFalse(isItemInsideGroup(items, 0))
        assertTrue(isItemInsideGroup(items, 1))
        assertTrue(isItemInsideGroup(items, 2))
        assertFalse(isItemInsideGroup(items, 3))
        assertFalse(isItemInsideGroup(items, 4))
    }

    @Test
    fun dragFromBelowDropOnHeader_keepsEndMarkerBoundariesAndAddsSingleTrack() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val items = mutableListOf(headerA, endA, "x1", "x2", "x3")

        var fromIndex = items.indexOf("x3")
        val firstReorderTarget = findNextTrackReorderIndex(items, fromIndex, -1)
        assertEquals(3, firstReorderTarget)
        Collections.swap(items, fromIndex, firstReorderTarget!!)

        fromIndex = items.indexOf("x3")
        val secondReorderTarget = findNextTrackReorderIndex(items, fromIndex, -1)
        assertEquals(2, secondReorderTarget)
        Collections.swap(items, fromIndex, secondReorderTarget!!)

        fromIndex = items.indexOf("x3")
        val blockedReorderTarget = findNextTrackReorderIndex(items, fromIndex, -1)
        assertNull(blockedReorderTarget)

        moveItemIntoGroup(
            items = items,
            fromIndex = items.indexOf("x3"),
            headerIndex = items.indexOf(headerA),
            mode = "TOP"
        )

        assertEquals(listOf(headerA, "x3", endA, "x1", "x2"), items)
        assertEquals(2, findMatchingGroupEndIndex(items, 0))
        assertTrue(isItemInsideGroup(items, 1))
        assertFalse(isItemInsideGroup(items, 3))
        assertFalse(isItemInsideGroup(items, 4))
    }

    @Test
    fun moveItemOutOfGroup_nominal_placesTrackRightAfterEndMarker() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val items = mutableListOf(headerA, "t1", "t2", endA, "x1", "x2")

        val moved = moveItemOutOfGroup(items, "t1")

        assertTrue(moved)
        assertEquals(listOf(headerA, "t2", endA, "t1", "x1", "x2"), items)
    }

    @Test
    fun moveItemOutOfGroup_trackOutsideGroup_returnsFalseAndKeepsList() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val items = mutableListOf(headerA, "t1", "t2", endA, "x1")
        val before = items.toList()

        val moved = moveItemOutOfGroup(items, "x1")

        assertFalse(moved)
        assertEquals(before, items)
    }

    @Test
    fun moveItemOutOfGroup_singleTrackGroup_becomesEmptyGroup() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val items = mutableListOf(headerA, "t1", endA)

        val moved = moveItemOutOfGroup(items, "t1")

        assertTrue(moved)
        assertEquals(listOf(headerA, endA, "t1"), items)
    }

    @Test
    fun moveItemOutOfGroup_legacyGroupWithoutEnd_usesFallbackWithoutCrash() {
        val headerA = buildGroupHeader("A")
        val items = mutableListOf(headerA, "t1", "x1")

        val moved = moveItemOutOfGroup(items, "t1")

        assertTrue(moved)
        assertEquals(listOf(headerA, "x1", "t1"), items)
    }

    @Test
    fun assignTrackToGroupByHeaderKey_targetsExactHeaderWhenGroupTitlesAreDuplicated() {
        val headerA1 = buildGroupHeader("A")
        val endA1 = buildGroupEnd(getGroupUuid(headerA1)!!)
        val headerA2 = buildGroupHeader("A")
        val endA2 = buildGroupEnd(getGroupUuid(headerA2)!!)
        val items = mutableListOf(headerA1, endA1, "x1", headerA2, endA2, "x2")

        val moved = assignTrackToGroupByHeaderKey(
            items = items,
            trackUri = "x2",
            headerKey = headerA2
        )

        assertTrue(moved)
        assertEquals(listOf(headerA1, endA1, "x1", headerA2, "x2", endA2), items)
    }
}
