package com.patrick.lrcreader.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp

fun formatChord(chord: String): AnnotatedString {
    val normalized = chord
        .replace("b", "♭")
        .replace("#", "♯")

    return buildAnnotatedString {
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]

            when {
                normalized.startsWith("maj", i) -> {
                    pushStyle(SpanStyle(fontSize = 13.sp))
                    append("maj")
                    pop()
                    i += 3
                }

                c == 'm' -> {
                    pushStyle(SpanStyle(fontSize = 13.sp))
                    append("m")
                    pop()
                    i++
                }

                c == '♯' -> {
                    pushStyle(SpanStyle(fontSize = 6.sp))
                    append(" ")
                    pop()
                    pushStyle(
                        SpanStyle(
                            fontSize = 13.sp
                        )
                    )
                    append("♯")
                    pop()
                    i++
                }

                c.isDigit() -> {
                    val start = i
                    while (i < normalized.length && normalized[i].isDigit()) i++

                    pushStyle(
                        SpanStyle(
                            fontSize = 12.sp,
                            baselineShift = BaselineShift.Superscript
                        )
                    )
                    append(normalized.substring(start, i))
                    pop()
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}
