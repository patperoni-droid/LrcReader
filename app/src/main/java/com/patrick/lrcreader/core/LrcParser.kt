package com.patrick.lrcreader.core

// On suppose que tu as déjà data class LrcLine(val timeMs: Long, val text: String)
// dans ce même package. On le réutilise tel quel.

private val TIME_TAG_REGEX =
    Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""")

private val OFFSET_REGEX =
    Regex("""^\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)

/**
 * Nouveau parseur LRC pour Live in Pocket :
 *
 * - respecte STRICTEMENT l'ordre des lignes dans le fichier
 * - 1 ligne de fichier = 1 LrcLine
 * - si plusieurs time tags sur la même ligne → on prend le PREMIER (simple, stable)
 * - si pas de tag → timeMs = 0L
 * - [offset:x] est appliqué à tous les temps
 */
fun parseLrc(raw: String): List<LrcLine> {
    val result = mutableListOf<LrcLine>()
    var offsetMs = 0L

    raw.lines().forEach { lineRaw ->
        val line = lineRaw.trim()
        if (line.isBlank()) return@forEach

        // Ligne offset ?
        val offsetMatch = OFFSET_REGEX.find(line)
        if (offsetMatch != null) {
            offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
            return@forEach
        }

        val matches = TIME_TAG_REGEX.findAll(line).toList()

        // Texte = le contenu après avoir retiré les tags
        val text = line.replace(TIME_TAG_REGEX, "").trim()

        if (matches.isEmpty()) {
            // Pas de tag → ligne non synchronisée
            result.add(
                LrcLine(
                    timeMs = 0L,
                    text = text
                )
            )
        } else {
            // On prend le PREMIER time tag pour cette ligne
            val m = matches.first()
            val minutes = m.groupValues[1].toIntOrNull() ?: 0
            val seconds = m.groupValues[2].toIntOrNull() ?: 0
            val fractionStr = m.groupValues.getOrNull(3).orEmpty()

            val fractionMs = when (fractionStr.length) {
                0 -> 0
                1 -> (fractionStr + "00").toInt()
                2 -> (fractionStr + "0").toInt()
                else -> fractionStr.take(3).toInt()
            }

            val baseMs = minutes * 60_000L + seconds * 1_000L + fractionMs
            val totalMs = baseMs + offsetMs

            result.add(
                LrcLine(
                    timeMs = totalMs,
                    text = text
                )
            )
        }
    }

    return result
}