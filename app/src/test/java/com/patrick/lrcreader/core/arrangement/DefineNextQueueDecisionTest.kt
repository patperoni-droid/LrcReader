package com.patrick.lrcreader.core.arrangement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefineNextQueueDecisionTest {

    private val repeatedQueue = listOf("A0", "A1", "A2", "A3", "B0", "C0")

    private fun applyPlan(
        currentQueue: List<String>,
        currentQueueIndex: Int,
        originalOccurrences: List<String>,
        plan: GroupedDefineNextQueuePlan
    ): List<String> = currentQueue.take(currentQueueIndex + 1) +
        plan.futureOccurrenceIndices.map(originalOccurrences::get)

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

    @Test
    fun `single occurrence keeps current item and arms selected destination`() {
        val occurrences = listOf("A0", "B0", "C0")
        val plan = requireNotNull(
            decideGroupedDefineNextQueue(
                selectedOccurrenceIndex = 2,
                occurrenceCount = occurrences.size,
                currentOccurrenceIndex = 0,
                currentGroupLastOccurrenceIndex = 0,
                selectedBelongsToCurrentGroup = false
            )
        )

        assertEquals(listOf("A0", "C0"), applyPlan(occurrences, 0, occurrences, plan))
        assertEquals(2, plan.armedOccurrenceIndex)
    }

    @Test
    fun `grouped destination from first repeat preserves the complete current group`() {
        val plan = groupedPlan(currentOccurrenceIndex = 0, selectedOccurrenceIndex = 5)

        assertEquals(
            listOf("A0", "A1", "A2", "A3", "C0"),
            applyPlan(repeatedQueue, 0, repeatedQueue, plan)
        )
    }

    @Test
    fun `grouped destination from second repeat preserves remaining repeats`() {
        val plan = groupedPlan(currentOccurrenceIndex = 1, selectedOccurrenceIndex = 5)

        assertEquals(
            listOf("A0", "A1", "A2", "A3", "C0"),
            applyPlan(repeatedQueue, 1, repeatedQueue, plan)
        )
    }

    @Test
    fun `grouped destination from last repeat naturally inserts next`() {
        val plan = groupedPlan(currentOccurrenceIndex = 3, selectedOccurrenceIndex = 5)

        assertEquals(
            listOf("A0", "A1", "A2", "A3", "C0"),
            applyPlan(repeatedQueue, 3, repeatedQueue, plan)
        )
    }

    @Test
    fun `second destination replaces first after remaining current group`() {
        val firstPlan = groupedPlan(currentOccurrenceIndex = 1, selectedOccurrenceIndex = 4)
        val afterFirstClick = applyPlan(repeatedQueue, 1, repeatedQueue, firstPlan)
        val secondPlan = groupedPlan(currentOccurrenceIndex = 1, selectedOccurrenceIndex = 5)

        assertEquals(
            listOf("A0", "A1", "A2", "A3", "B0", "C0"),
            afterFirstClick
        )
        assertEquals(
            listOf("A0", "A1", "A2", "A3", "C0"),
            applyPlan(afterFirstClick, 1, repeatedQueue, secondPlan)
        )
    }

    @Test
    fun `destination before current group keeps group end then rebuilds original continuation`() {
        val occurrences = listOf("B0", "A0", "A1", "C0")
        val plan = requireNotNull(
            decideGroupedDefineNextQueue(
                selectedOccurrenceIndex = 0,
                occurrenceCount = occurrences.size,
                currentOccurrenceIndex = 1,
                currentGroupLastOccurrenceIndex = 2,
                selectedBelongsToCurrentGroup = false
            )
        )

        assertEquals(
            listOf("B0", "A0", "A1", "B0", "A0", "A1", "C0"),
            applyPlan(occurrences, 1, occurrences, plan)
        )
    }

    @Test
    fun `selecting current graphical group preserves restart behavior`() {
        val plan = requireNotNull(
            decideGroupedDefineNextQueue(
                selectedOccurrenceIndex = 0,
                occurrenceCount = repeatedQueue.size,
                currentOccurrenceIndex = 1,
                currentGroupLastOccurrenceIndex = 3,
                selectedBelongsToCurrentGroup = true
            )
        )

        assertEquals(
            listOf("A0", "A1", "A0", "A1", "A2", "A3", "B0", "C0"),
            applyPlan(repeatedQueue, 1, repeatedQueue, plan)
        )
    }

    @Test
    fun `define next after loop cancellation preserves remaining repeated group`() {
        val loopQueue = listOf("A0", "A1", "A2", "A3")
        val plan = groupedPlan(currentOccurrenceIndex = 1, selectedOccurrenceIndex = 4)

        assertEquals(
            listOf("A0", "A1", "A2", "A3", "B0", "C0"),
            applyPlan(loopQueue, 1, repeatedQueue, plan)
        )
    }

    private fun groupedPlan(
        currentOccurrenceIndex: Int,
        selectedOccurrenceIndex: Int
    ): GroupedDefineNextQueuePlan = requireNotNull(
        decideGroupedDefineNextQueue(
            selectedOccurrenceIndex = selectedOccurrenceIndex,
            occurrenceCount = repeatedQueue.size,
            currentOccurrenceIndex = currentOccurrenceIndex,
            currentGroupLastOccurrenceIndex = 3,
            selectedBelongsToCurrentGroup = false
        )
    )
}
