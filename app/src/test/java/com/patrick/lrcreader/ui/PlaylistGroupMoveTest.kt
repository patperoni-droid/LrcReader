package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupHeader
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistGroupMoveTest {

    @Test
    fun moveTrackIntoFirstGroup_placesTrackAfterHeader() {
        val headerA = buildGroupHeader("A")
        val headerB = buildGroupHeader("B")
        val items = mutableListOf(headerA, "t1", "t2", headerB, "t3")

        moveItemIntoGroup(items, fromIndex = 4, headerIndex = 0, mode = "TOP")

        assertEquals(listOf(headerA, "t3", "t1", "t2", headerB), items)
    }

    @Test
    fun moveTrackIntoSecondGroup_placesTrackInsideThatGroup() {
        val headerA = buildGroupHeader("A")
        val headerB = buildGroupHeader("B")
        val items = mutableListOf(headerA, "t1", "t2", headerB, "t3")

        moveItemIntoGroup(items, fromIndex = 2, headerIndex = 3, mode = "TOP")

        assertEquals(listOf(headerA, "t1", headerB, "t2", "t3"), items)
    }

    @Test
    fun moveHeaderIntoHeader_isIgnored() {
        val headerA = buildGroupHeader("A")
        val headerB = buildGroupHeader("B")
        val items = mutableListOf(headerA, "t1", "t2", headerB, "t3")
        val before = items.toList()

        moveItemIntoGroup(items, fromIndex = 3, headerIndex = 0, mode = "TOP")

        assertEquals(before, items)
    }
}
