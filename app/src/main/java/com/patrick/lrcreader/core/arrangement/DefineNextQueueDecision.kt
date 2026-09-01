package com.patrick.lrcreader.core.arrangement

enum class DefineNextQueueOperation {
    ADD,
    REPLACE
}

data class DefineNextQueueDecision(
    val armedOccurrenceIndex: Int,
    val insertionIndex: Int,
    val operation: DefineNextQueueOperation
)

data class GroupedDefineNextQueuePlan(
    val armedOccurrenceIndex: Int,
    val futureOccurrenceIndices: List<Int>
)

fun decideDefineNextQueue(
    selectedOccurrenceIndex: Int,
    occurrenceCount: Int,
    currentMediaItemIndex: Int,
    mediaItemCount: Int
): DefineNextQueueDecision? {
    if (selectedOccurrenceIndex !in 0 until occurrenceCount) return null

    val insertionIndex = currentMediaItemIndex.coerceAtLeast(0) + 1
    val operation = if (mediaItemCount <= insertionIndex) {
        DefineNextQueueOperation.ADD
    } else {
        DefineNextQueueOperation.REPLACE
    }
    return DefineNextQueueDecision(
        armedOccurrenceIndex = selectedOccurrenceIndex,
        insertionIndex = insertionIndex,
        operation = operation
    )
}

fun decideGroupedDefineNextQueue(
    selectedOccurrenceIndex: Int,
    occurrenceCount: Int,
    currentOccurrenceIndex: Int,
    currentGroupLastOccurrenceIndex: Int,
    selectedBelongsToCurrentGroup: Boolean
): GroupedDefineNextQueuePlan? {
    if (selectedOccurrenceIndex !in 0 until occurrenceCount) return null

    val hasValidCurrentGroup =
        currentOccurrenceIndex in 0 until occurrenceCount &&
            currentGroupLastOccurrenceIndex in currentOccurrenceIndex until occurrenceCount
    val remainingCurrentGroupIndices = if (
        hasValidCurrentGroup &&
        !selectedBelongsToCurrentGroup &&
        currentOccurrenceIndex < currentGroupLastOccurrenceIndex
    ) {
        (currentOccurrenceIndex + 1..currentGroupLastOccurrenceIndex).toList()
    } else {
        emptyList()
    }

    return GroupedDefineNextQueuePlan(
        armedOccurrenceIndex = selectedOccurrenceIndex,
        futureOccurrenceIndices =
            remainingCurrentGroupIndices + (selectedOccurrenceIndex until occurrenceCount)
    )
}
