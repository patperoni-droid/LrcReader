package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGroupCollapseVisibilityTest {

    @Test
    fun isItemHiddenByCollapsedGroup_hidesOnlyBetweenStartAndEnd() {
        val headerA = buildGroupHeader("A")
        val endA = buildGroupEnd(getGroupUuid(headerA)!!)
        val headerB = buildGroupHeader("B")
        val endB = buildGroupEnd(getGroupUuid(headerB)!!)
        val songs = listOf(headerA, "t1", "t2", endA, "outsideA", headerB, "t3", endB)
        val collapsed = setOf(headerA)

        assertTrue(isItemHiddenByCollapsedGroup(songs, 1, collapsed))  // t1 under A
        assertTrue(isItemHiddenByCollapsedGroup(songs, 2, collapsed))  // t2 under A
        assertFalse(isItemHiddenByCollapsedGroup(songs, 0, collapsed)) // H(A)
        assertFalse(isItemHiddenByCollapsedGroup(songs, 3, collapsed)) // END(A)
        assertFalse(isItemHiddenByCollapsedGroup(songs, 4, collapsed)) // outside A bounds
        assertFalse(isItemHiddenByCollapsedGroup(songs, 5, collapsed)) // H(B)
        assertFalse(isItemHiddenByCollapsedGroup(songs, 6, collapsed)) // t3 under B
    }
}
