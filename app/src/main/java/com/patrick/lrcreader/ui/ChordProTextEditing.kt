package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.PrompterTextColor
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


internal enum class PrompterMarkupCommand(val prefix: String, val suffix: String = "", val block: Boolean = false) {
    TITLE("# ", block = true),
    SECTION("## ", block = true),
    VERSE("## ", block = true),
    CHORUS("## ", block = true),
    COMMENT("*", "*", block = true),
    BOLD("**", "**"),
    ITALIC("*", "*"),
    DIVIDER("---", block = true)
}

internal fun insertPrompterMarkup(
    value: TextFieldValue,
    command: PrompterMarkupCommand,
    placeholder: String
): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val body = selected.ifEmpty { placeholder }
    val before = if (command.block && start > 0 && value.text[start - 1] !in "\r\n") "\n" else ""
    val insertionEnd = if (command == PrompterMarkupCommand.DIVIDER) start else end
    val after = if (command.block && (insertionEnd == value.text.length || value.text[insertionEnd] !in "\r\n")) "\n" else ""
    // A divider does not consume the selection: it is inserted immediately before it.
    val content = if (command == PrompterMarkupCommand.DIVIDER) "" else body
    val formatted = if (command.suffix.isNotEmpty()) {
        Regex("[^\r\n]+").replace(content) { command.prefix + it.value + command.suffix }
    } else command.prefix + content
    val replacement = before + formatted + after
    val replaceEnd = if (command == PrompterMarkupCommand.DIVIDER) start else end
    val contentStart = start + before.length + command.prefix.length
    return TextFieldValue(
        value.text.replaceRange(start, replaceEnd, replacement),
        if (command == PrompterMarkupCommand.DIVIDER) TextRange(start + replacement.length)
        else if (selected.isEmpty()) TextRange(contentStart, contentStart + content.length)
        else TextRange(start + replacement.length)
    )
}

private val prompterColorRegion = Regex("<c=(yellow|orange|red|blue|green|white)>([^\r\n]*?)</c>")

internal fun applyPrompterColor(
    value: TextFieldValue,
    color: PrompterTextColor,
    placeholder: String
): TextFieldValue {
    var start = value.selection.min
    var end = value.selection.max
    // Recolour an existing region instead of nesting unsupported colour tags.
    prompterColorRegion.findAll(value.text).forEach { match ->
        val content = match.groups[2]!!
        if ((start < match.range.last + 1 && end > match.range.first) ||
            (start == end && start >= content.range.first && start <= content.range.last + 1)) {
            start = minOf(start, match.range.first)
            end = maxOf(end, match.range.last + 1)
        }
    }
    val selected = value.text.substring(start, end)
    val body = selected.ifEmpty { placeholder }
    val uncolored = prompterColorRegion.replace(body) { it.groupValues[2] }
    val opening = "<c=${color.syntaxName}>"
    // The existing parser is line-based. Keep headings outside the inline colour tag.
    val colored = Regex("[^\r\n]+").replace(uncolored) { match ->
        val line = match.value
        val prefix = when {
            line.startsWith("## ") -> "## "
            line.startsWith("# ") -> "# "
            else -> ""
        }
        if (line.trim() == "---") line
        else prefix + opening + line.removePrefix(prefix) + "</c>"
    }
    val selection = if (selected.isEmpty()) {
        TextRange(start + opening.length, start + opening.length + placeholder.length)
    } else TextRange(start + colored.length)
    return TextFieldValue(value.text.replaceRange(start, end, colored), selection)
}
