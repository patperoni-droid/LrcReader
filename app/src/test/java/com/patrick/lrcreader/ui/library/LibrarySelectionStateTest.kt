package com.patrick.lrcreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionStateTest {

    @Test
    fun selectionOperations_keepOnlyExplicitVisibleKeys() {
        var state = LibrarySelectionState<String>()

        state = state.toggle("A")
        assertTrue(state.isActive)
        assertEquals(setOf("A"), state.selectedKeys)

        state = state.selectAll(listOf("B", "C"))
        assertEquals(setOf("B", "C"), state.selectedKeys)

        state = state.retainOnly(listOf("C", "D"))
        assertEquals(setOf("C"), state.selectedKeys)

        state = state.toggle("C")
        assertFalse(state.isActive)

        state = state.selectAll(listOf("A", "B")).clear()
        assertTrue(state.selectedKeys.isEmpty())
    }
}
