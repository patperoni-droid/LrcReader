package com.patrick.lrcreader.core

fun nextPlayableIndexAtOrAfter(items: List<String>, startIndex: Int): Int? {
    var index = startIndex.coerceAtLeast(0)
    while (index < items.size) {
        if (isPlayableAudioItem(items[index])) return index
        index++
    }
    return null
}

fun nextPlayableUriAfter(items: List<String>, currentIndex: Int): String? {
    val nextIndex = nextPlayableIndexAtOrAfter(items, currentIndex + 1) ?: return null
    return items[nextIndex]
}
