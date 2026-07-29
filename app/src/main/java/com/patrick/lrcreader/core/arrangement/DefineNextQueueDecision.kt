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
