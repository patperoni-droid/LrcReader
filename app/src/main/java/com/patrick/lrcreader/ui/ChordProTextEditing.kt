package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.parseChordPaletteInput
import kotlin.math.min

internal fun parseTextPrompterChordPaletteInput(raw: String): List<String> =
    parseChordPaletteInput(raw.replace(Regex("""\s+"""), ","))

internal fun insertChordProAtSelection(
    value: TextFieldValue,
    chord: String
): TextFieldValue {
    val normalizedChord = chord.trim()
    if (normalizedChord.isEmpty()) return value

    val insertionOffset = min(value.selection.start, value.selection.end)
        .coerceIn(0, value.text.length)
    val chordProTag = "[$normalizedChord]"
    val nextText = value.text.substring(0, insertionOffset) +
        chordProTag +
        value.text.substring(insertionOffset)

    return TextFieldValue(
        text = nextText,
        selection = TextRange(insertionOffset + chordProTag.length)
    )
}
