package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.core.parseChordPro
import kotlin.math.min

internal fun extractPrompterChordPaletteFromText(raw: String): List<String> = buildList {
    parseChordPro(raw).lines.forEach { line ->
        line.anchors.forEach { anchor ->
            anchor.symbol.raw.takeIf { it !in this }?.let(::add)
        }
    }
}

internal fun insertEmptyChordProAtSelection(value: TextFieldValue): TextFieldValue {
    val insertionOffset = value.selection.min.coerceIn(0, value.text.length)
    return TextFieldValue(
        text = value.text.substring(0, insertionOffset) +
            "[]" +
            value.text.substring(insertionOffset),
        selection = TextRange(insertionOffset + 1)
    )
}

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
    if (command == PrompterMarkupCommand.BOLD || command == PrompterMarkupCommand.ITALIC) {
        return togglePrompterEmphasis(value, command, placeholder)
    }
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

internal fun canEditPrompterColor(value: TextFieldValue): Boolean {
    if (!value.selection.collapsed) return true
    return prompterColorRegion.findAll(value.text).any { match ->
        val content = match.groups[2]!!
        value.selection.start >= content.range.first &&
            value.selection.start <= content.range.last + 1
    }
}

internal fun applyPrompterColor(
    value: TextFieldValue,
    color: PrompterTextColor,
    placeholder: String
): TextFieldValue {
    if (hasPartialMarkupSelection(value)) return value
    val existing = prompterColorRegion.findAll(value.text).firstOrNull { match ->
        val body = match.groups[2]!!
        value.selection.min >= body.range.first && value.selection.max <= body.range.last + 1
    }
    if (existing != null && "<c=" !in existing.groupValues[2]) {
        val oldEnd = existing.groups[2]!!.range.first
        val opening = "<c=${color.syntaxName}>"
        val delta = opening.length - (oldEnd - existing.range.first)
        return TextFieldValue(
            value.text.replaceRange(existing.range.first, oldEnd, opening),
            TextRange(value.selection.start + delta, value.selection.end + delta)
        )
    }
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
    if ("<c=" in uncolored || "</c>" in uncolored) return value
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


/** Palette action: a caret inside an existing valid chord edits that chord. */
internal fun editOrInsertPrompterChord(value: TextFieldValue, chord: String): TextFieldValue {
    val normalizedChord = chord.trim()
    val tag = "[$normalizedChord]"
    if (!parseChordPro(tag).hasChords) return value
    val replacementRange = selectedPrompterChordAnchor(value)?.sourceRange
        ?: emptyChordBracketsAtCaret(value)
        ?: return insertChordProAtSelection(value, normalizedChord)
    val start = replacementRange.first
    return TextFieldValue(
        value.text.replaceRange(start, replacementRange.last + 1, tag),
        TextRange(start + tag.length)
    )
}

internal fun selectedPrompterChord(value: TextFieldValue): String? =
    selectedPrompterChordAnchor(value)?.symbol?.raw

internal fun isValidPrompterChordInput(chord: String): Boolean {
    val normalized = chord.trim()
    return normalized.isNotEmpty() && parseChordPro("[$normalized]").hasChords
}

internal fun replaceExactPrompterChordOccurrences(
    value: TextFieldValue,
    oldChord: String,
    newChord: String
): TextFieldValue {
    val normalizedOldChord = oldChord.trim()
    val normalizedNewChord = newChord.trim()
    if (
        normalizedOldChord.isEmpty() ||
        normalizedOldChord == normalizedNewChord ||
        !isValidPrompterChordInput(normalizedNewChord)
    ) {
        return value
    }

    val replacement = "[$normalizedNewChord]"
    val ranges = parseChordPro(value.text).lines
        .flatMap { it.anchors }
        .filter { it.symbol.raw == normalizedOldChord }
        .map { it.sourceRange }
        .sortedBy { it.first }
    if (ranges.isEmpty()) return value

    fun mapOffset(offset: Int): Int {
        var delta = 0
        ranges.forEach { range ->
            val endExclusive = range.last + 1
            if (offset <= range.first) return offset + delta
            if (offset < endExclusive) return range.first + delta + replacement.length
            delta += replacement.length - (endExclusive - range.first)
        }
        return offset + delta
    }

    var updatedText = value.text
    ranges.asReversed().forEach { range ->
        updatedText = updatedText.replaceRange(range.first, range.last + 1, replacement)
    }
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(
            start = mapOffset(value.selection.start).coerceIn(0, updatedText.length),
            end = mapOffset(value.selection.end).coerceIn(0, updatedText.length)
        )
    )
}

private fun selectedPrompterChordAnchor(value: TextFieldValue) =
    parseChordPro(value.text).lines.flatMap { it.anchors }.firstOrNull {
        val start = it.sourceRange.first
        val end = it.sourceRange.last + 1
        if (value.selection.collapsed) value.selection.start > start && value.selection.start < end
        else value.selection.min == start && value.selection.max == end
    }

private fun emptyChordBracketsAtCaret(value: TextFieldValue): IntRange? {
    if (!value.selection.collapsed) return null
    val caret = value.selection.start.coerceIn(0, value.text.length)
    return if (
        caret > 0 &&
        caret < value.text.length &&
        value.text[caret - 1] == '[' &&
        value.text[caret] == ']'
    ) {
        (caret - 1)..caret
    } else {
        null
    }
}

private fun removeMarkupRanges(value: TextFieldValue, ranges: List<IntRange>): TextFieldValue {
    if (ranges.isEmpty()) return value
    val sorted = ranges.sortedBy { it.first }
    fun map(offset: Int) = offset - sorted.sumOf {
        (offset - it.first).coerceIn(0, it.last - it.first + 1)
    }
    var text = value.text
    sorted.asReversed().forEach { text = text.removeRange(it.first, it.last + 1) }
    return TextFieldValue(text, TextRange(map(value.selection.start), map(value.selection.end)))
}

/** Default removes explicit colour without introducing another wrapper. */
internal fun removePrompterColor(value: TextFieldValue): TextFieldValue {
    val ranges = prompterColorRegion.findAll(value.text).filter { match ->
        "<c=" !in match.groupValues[2] &&
            if (value.selection.collapsed) {
                value.selection.start >= match.range.first && value.selection.start <= match.range.last
            } else value.selection.min <= match.range.last && value.selection.max > match.range.first
    }.flatMap { match ->
        val body = match.groups[2]!!
        sequenceOf(match.range.first until body.range.first, (body.range.last + 1)..match.range.last)
    }.toList()
    return removeMarkupRanges(value, ranges)
}

// Selection boundaries must not cut through markup, even when the visible text is valid.
private fun hasPartialMarkupSelection(value: TextFieldValue): Boolean =
    Regex("<c=[^>]*>|</c>|\\*+").findAll(value.text).any { token ->
        (value.selection.min > token.range.first && value.selection.min <= token.range.last) ||
            (value.selection.max > token.range.first && value.selection.max <= token.range.last)
    } || parseChordPro(value.text).lines.any { line ->
        line.anchors.any { chord ->
            (value.selection.min > chord.sourceRange.first && value.selection.min <= chord.sourceRange.last) ||
                (value.selection.max > chord.sourceRange.first && value.selection.max <= chord.sourceRange.last)
        }
    }

private fun togglePrompterEmphasis(
    value: TextFieldValue,
    command: PrompterMarkupCommand,
    placeholder: String
): TextFieldValue {
    if (hasPartialMarkupSelection(value)) return value
    val marker = command.prefix
    val regions = Regex("(?<!\\*)\\*\\*[^*\r\n]+\\*\\*(?!\\*)|(?<!\\*)\\*[^*\r\n]+\\*(?!\\*)")
        .findAll(value.text).toList()
    val existing = regions.firstOrNull { match ->
        val width = if (match.value.startsWith("**")) 2 else 1
        (value.selection.min >= match.range.first + width && value.selection.max <= match.range.last + 1 - width) ||
            (value.selection.min == match.range.first && value.selection.max == match.range.last + 1)
    }
    if (existing != null) {
        val width = if (existing.value.startsWith("**")) 2 else 1
        if (width != marker.length) return value // Mixed nesting is unsupported by the parser.
        return removeMarkupRanges(value, listOf(
            existing.range.first until existing.range.first + width,
            (existing.range.last + 1 - width)..existing.range.last
        ))
    }
    val start = value.selection.min
    val end = value.selection.max
    if (regions.any { start <= it.range.last && end > it.range.first ||
            (value.selection.collapsed && (start == it.range.first || start == it.range.last + 1)) }) return value
    val selected = value.text.substring(start, end)
    if ('*' in selected) return value
    val body = selected.ifEmpty { placeholder }
    val wrapped = Regex("[^\r\n]+").replace(body) { marker + it.value + marker }
    val text = value.text.replaceRange(start, end, wrapped)
    return TextFieldValue(text,
        if (selected.isEmpty()) TextRange(start + marker.length, start + marker.length + body.length)
        else TextRange(start, start + wrapped.length)
    )
}
