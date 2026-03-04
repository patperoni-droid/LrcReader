package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import org.junit.Assert.assertEquals
import org.junit.Test

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

        assertEquals(listOf(headerA, "t3", "t1", "t2", endA, headerB, endB), items)
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
}
