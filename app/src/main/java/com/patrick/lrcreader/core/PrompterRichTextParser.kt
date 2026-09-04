package com.patrick.lrcreader.core

data class PrompterRichTextDocument(
    val source: String,
    val lines: List<PrompterRichTextLine>,
    val hasFormatting: Boolean
)

data class PrompterRichTextLine(
    val source: String,
    val plainText: String,
    val blockKind: PrompterRichTextBlockKind,
    val spans: List<PrompterRichTextSpan>,
    val inputOffsetToPlainOffset: List<Int>
) {
    fun plainOffsetForInputOffset(inputOffset: Int): Int {
        require(inputOffset in 0..source.length) { "Input offset is outside the source line" }
        return inputOffsetToPlainOffset[inputOffset]
    }
}

data class PrompterRichTextSpan(
    val start: Int,
    val endExclusive: Int,
    val style: PrompterRichTextStyle
) {
    init {
        require(start >= 0) { "Span start must be non-negative" }
        require(endExclusive > start) { "Span must contain text" }
    }
}

enum class PrompterRichTextBlockKind {
    BODY,
    TITLE,
    SECTION,
    DIVIDER
}

sealed interface PrompterRichTextStyle {
    data object Bold : PrompterRichTextStyle
    data object Italic : PrompterRichTextStyle
    data class ForegroundColor(
        val color: PrompterTextColor
    ) : PrompterRichTextStyle
}

enum class PrompterTextColor(val syntaxName: String) {
    YELLOW("yellow"),
    ORANGE("orange"),
    RED("red"),
    BLUE("blue"),
    GREEN("green"),
    WHITE("white");

    companion object {
        fun fromSyntaxName(value: String): PrompterTextColor? =
            entries.firstOrNull { it.syntaxName == value }
    }
}

fun parsePrompterRichText(
    source: String,
    emptyColorOffsets: Set<Int> = emptySet()
): PrompterRichTextDocument {
    val lines = splitPrompterRichTextSourceLines(source).map {
        parsePrompterRichTextLine(it, emptyColorOffsets)
    }
    return PrompterRichTextDocument(
        source = source,
        lines = lines,
        hasFormatting = lines.any { line ->
            line.blockKind != PrompterRichTextBlockKind.BODY || line.spans.isNotEmpty()
        }
    )
}

private fun parsePrompterRichTextLine(source: String, emptyColorOffsets: Set<Int>): PrompterRichTextLine {
    if (source.trim() == "---") {
        return PrompterRichTextLine(
            source = source,
            plainText = "",
            blockKind = PrompterRichTextBlockKind.DIVIDER,
            spans = emptyList(),
            inputOffsetToPlainOffset = List(source.length + 1) { 0 }
        )
    }

    val (blockKind, contentStart) = when {
        source.startsWith("## ") -> PrompterRichTextBlockKind.SECTION to 3
        source.startsWith("# ") -> PrompterRichTextBlockKind.TITLE to 2
        else -> PrompterRichTextBlockKind.BODY to 0
    }
    val colorPass = parseColorMarkup(source, contentStart, emptyColorOffsets)
    val markdownPass = parseMarkdownMarkup(colorPass.text)
    val colorSpans = colorPass.spans.mapNotNull { span ->
        if (markdownPass.inputOffsetToPlainOffset[span.start] ==
            markdownPass.inputOffsetToPlainOffset[span.endExclusive]) return@mapNotNull null
        PrompterRichTextSpan(
            start = markdownPass.inputOffsetToPlainOffset[span.start],
            endExclusive = markdownPass.inputOffsetToPlainOffset[span.endExclusive],
            style = span.style
        )
    }

    return PrompterRichTextLine(
        source = source,
        plainText = markdownPass.text,
        blockKind = blockKind,
        spans = markdownPass.spans + colorSpans,
        inputOffsetToPlainOffset = colorPass.inputOffsetToPlainOffset.map { intermediateOffset ->
            markdownPass.inputOffsetToPlainOffset[intermediateOffset]
        }
    )
}

private data class InlineMarkupPass(
    val text: String,
    val spans: List<PrompterRichTextSpan>,
    val inputOffsetToPlainOffset: List<Int>
)

private fun parseColorMarkup(source: String, contentStart: Int, emptyColorOffsets: Set<Int>): InlineMarkupPass {
    val builder = RichTextLineBuilder(source)
    val spans = mutableListOf<PrompterRichTextSpan>()
    builder.skipUntil(contentStart)

    while (builder.inputOffset < source.length) {
        val markerStart = builder.inputOffset
        if (!source.startsWith(COLOR_OPENING_PREFIX, markerStart)) {
            builder.copyUntil(markerStart + 1)
            continue
        }

        val openingEnd = source.indexOf('>', startIndex = markerStart + COLOR_OPENING_PREFIX.length)
        if (openingEnd < 0) {
            builder.copyUntil(source.length)
            continue
        }

        val contentStartOffset = openingEnd + 1
        val closingStart = source.indexOf(COLOR_CLOSING_TAG, startIndex = contentStartOffset)
        val literalEnd = if (closingStart < 0) {
            source.length
        } else {
            closingStart + COLOR_CLOSING_TAG.length
        }
        val colorName = source.substring(
            markerStart + COLOR_OPENING_PREFIX.length,
            openingEnd
        )
        val color = PrompterTextColor.fromSyntaxName(colorName)
        val nestedOpening = if (closingStart < 0) {
            -1
        } else {
            source.indexOf(COLOR_OPENING_PREFIX, startIndex = contentStartOffset)
        }
        val hasNestedColor = closingStart >= 0 &&
            nestedOpening >= contentStartOffset &&
            nestedOpening < closingStart
        val isValid = color != null &&
            (closingStart > contentStartOffset ||
                (closingStart == contentStartOffset && markerStart in emptyColorOffsets)) &&
            !hasNestedColor

        if (!isValid) {
            builder.copyUntil(literalEnd)
            continue
        }

        builder.skipUntil(contentStartOffset)
        val spanStart = builder.plainLength
        builder.copyUntil(closingStart)
        val spanEnd = builder.plainLength
        builder.skipUntil(closingStart + COLOR_CLOSING_TAG.length)
        if (spanEnd > spanStart) spans += PrompterRichTextSpan(
            start = spanStart,
            endExclusive = spanEnd,
            style = PrompterRichTextStyle.ForegroundColor(requireNotNull(color))
        )
    }

    return InlineMarkupPass(
        text = builder.plainText,
        spans = spans,
        inputOffsetToPlainOffset = builder.offsetMapping
    )
}

private fun parseMarkdownMarkup(source: String): InlineMarkupPass {
    val builder = RichTextLineBuilder(source)
    val spans = mutableListOf<PrompterRichTextSpan>()

    while (builder.inputOffset < source.length) {
        val markerStart = builder.inputOffset
        when {
            source.startsWith("**", markerStart) -> {
                val closingMarker = source.indexOf("**", startIndex = markerStart + 2)
                if (closingMarker < 0) {
                    builder.copyUntil(source.length)
                } else {
                    val contentStartOffset = markerStart + 2
                    val content = source.substring(contentStartOffset, closingMarker)
                    if (content.isEmpty() || '*' in content) {
                        builder.copyUntil(closingMarker + 2)
                    } else {
                        builder.skipUntil(contentStartOffset)
                        val spanStart = builder.plainLength
                        builder.copyUntil(closingMarker)
                        val spanEnd = builder.plainLength
                        builder.skipUntil(closingMarker + 2)
                        spans += PrompterRichTextSpan(
                            start = spanStart,
                            endExclusive = spanEnd,
                            style = PrompterRichTextStyle.Bold
                        )
                    }
                }
            }

            source[markerStart] == '*' -> {
                val closingMarker = findItalicClosingMarker(source, markerStart + 1)
                if (closingMarker == null) {
                    builder.copyUntil(source.length)
                } else if (closingMarker.hasNestedBold || closingMarker.offset == markerStart + 1) {
                    builder.copyUntil(closingMarker.offset + 1)
                } else {
                    builder.skipUntil(markerStart + 1)
                    val spanStart = builder.plainLength
                    builder.copyUntil(closingMarker.offset)
                    val spanEnd = builder.plainLength
                    builder.skipUntil(closingMarker.offset + 1)
                    spans += PrompterRichTextSpan(
                        start = spanStart,
                        endExclusive = spanEnd,
                        style = PrompterRichTextStyle.Italic
                    )
                }
            }

            else -> builder.copyUntil(markerStart + 1)
        }
    }

    return InlineMarkupPass(
        text = builder.plainText,
        spans = spans,
        inputOffsetToPlainOffset = builder.offsetMapping
    )
}

private const val COLOR_OPENING_PREFIX = "<c="
private const val COLOR_CLOSING_TAG = "</c>"

private data class ItalicClosingMarker(
    val offset: Int,
    val hasNestedBold: Boolean
)

private fun findItalicClosingMarker(source: String, startIndex: Int): ItalicClosingMarker? {
    var cursor = startIndex
    var hasNestedBold = false
    while (cursor < source.length) {
        if (source.startsWith("**", cursor)) {
            hasNestedBold = true
            cursor += 2
        } else if (source[cursor] == '*') {
            return ItalicClosingMarker(offset = cursor, hasNestedBold = hasNestedBold)
        } else {
            cursor += 1
        }
    }
    return null
}

private class RichTextLineBuilder(
    private val source: String
) {
    private val plain = StringBuilder(source.length)
    private val mapping = IntArray(source.length + 1)

    var inputOffset: Int = 0
        private set

    val plainLength: Int
        get() = plain.length

    val plainText: String
        get() = plain.toString()

    val offsetMapping: List<Int>
        get() = mapping.toList()

    fun copyUntil(endExclusive: Int) {
        require(endExclusive in inputOffset..source.length)
        while (inputOffset < endExclusive) {
            plain.append(source[inputOffset])
            inputOffset += 1
            mapping[inputOffset] = plain.length
        }
    }

    fun skipUntil(endExclusive: Int) {
        require(endExclusive in inputOffset..source.length)
        while (inputOffset < endExclusive) {
            inputOffset += 1
            mapping[inputOffset] = plain.length
        }
    }
}

private fun splitPrompterRichTextSourceLines(source: String): List<String> {
    val lines = mutableListOf<String>()
    var lineStart = 0
    var cursor = 0

    while (cursor < source.length) {
        val separatorLength = when (source[cursor]) {
            '\r' -> if (source.getOrNull(cursor + 1) == '\n') 2 else 1
            '\n' -> 1
            else -> 0
        }
        if (separatorLength == 0) {
            cursor += 1
            continue
        }

        lines += source.substring(lineStart, cursor)
        cursor += separatorLength
        lineStart = cursor
    }

    lines += source.substring(lineStart)
    return lines
}
