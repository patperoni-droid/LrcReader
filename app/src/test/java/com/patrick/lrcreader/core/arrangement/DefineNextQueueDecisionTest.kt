package com.patrick.lrcreader.core.arrangement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefineNextQueueDecisionTest {

    @Test
    fun `adds after current item when no next item exists`() {
        assertEquals(
            DefineNextQueueDecision(
                armedOccurrenceIndex = 3,
                insertionIndex = 2,
                operation = DefineNextQueueOperation.ADD
            ),
            decideDefineNextQueue(
                selectedOccurrenceIndex = 3,
                occurrenceCount = 5,
                currentMediaItemIndex = 1,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun `replaces existing next item`() {
        assertEquals(
            DefineNextQueueDecision(
                armedOccurrenceIndex = 4,
                insertionIndex = 2,
                operation = DefineNextQueueOperation.REPLACE
            ),
            decideDefineNextQueue(
                selectedOccurrenceIndex = 4,
                occurrenceCount = 6,
                currentMediaItemIndex = 1,
                mediaItemCount = 4
            )
        )
    }

    @Test
    fun `rejects occurrence outside structure`() {
        assertNull(
            decideDefineNextQueue(
                selectedOccurrenceIndex = 4,
                occurrenceCount = 4,
                currentMediaItemIndex = 0,
                mediaItemCount = 2
            )
        )
    }
}
