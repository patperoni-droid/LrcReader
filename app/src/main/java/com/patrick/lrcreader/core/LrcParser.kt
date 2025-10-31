package com.patrick.lrcreader.core

private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,2}))?]""")

fun parseLrc(lrcText: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    for (raw in lrcText.lines()) {
        val matches = timeRegex.findAll(raw)
        val text = raw.replace(timeRegex, "").trim()
        for (m in matches) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val cent = m.groupValues.getOrNull(3)?.toLongOrNull() ?: 0
            val timeMs = (min * 60_000) + (sec * 1_000) + (cent * 10)
            lines.add(LrcLine(timeMs, text))
        }
    }
    return lines.sortedBy { it.timeMs }
}