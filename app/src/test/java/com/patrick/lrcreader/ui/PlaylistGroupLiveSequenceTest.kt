package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGroupLiveSequenceTest {

    @Test
    fun playlistGroup_dragIntoGroup_thenDeleteGroup_preservesPlayableOrder() {
        val header = buildGroupHeader("Live")
        val end = buildGroupEnd(getGroupUuid(header)!!)
        val items = mutableListOf(header, "a1", end, "b1", "b2")

        moveItemIntoGroup(
            items = items,
            fromIndex = items.indexOf("b2"),
            headerIndex = items.indexOf(header),
            mode = "BOTTOM"
        )

        assertEquals(listOf(header, "a1", "b2", end, "b1"), items)

        val removed = removeGroupAtHeader(items, items.indexOf(header))

        assertTrue(removed)
        assertEquals(listOf("a1", "b2", "b1"), items)
        assertFalse(items.any { isGroupHeader(it) })
        assertFalse(items.any { isGroupEnd(it) })
    }

    @Test
    fun removeGroupAtHeader_invalidIndexOrNonHeader_isNoOp() {
        val header = buildGroupHeader("Live")
        val end = buildGroupEnd(getGroupUuid(header)!!)
        val items = mutableListOf(header, "a1", end, "b1")
        val before = items.toList()

        val invalid = removeGroupAtHeader(items, -1)
        val nonHeader = removeGroupAtHeader(items, 1)

        assertFalse(invalid)
        assertFalse(nonHeader)
        assertEquals(before, items)
    }
}
