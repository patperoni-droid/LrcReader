package com.patrick.lrcreader.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.ChordProDocument
import com.patrick.lrcreader.core.ChordProLine
import com.patrick.lrcreader.core.PrompterPreparedDocument
import com.patrick.lrcreader.core.PrompterPreparedLine
import com.patrick.lrcreader.core.PrompterRichTextBlockKind
import com.patrick.lrcreader.core.PrompterRichTextSpan
import com.patrick.lrcreader.core.PrompterRichTextStyle
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.theme.SplColors
import kotlinx.coroutines.delay

internal data class PrompterChordRun(
    val lyricText: String,
    val chords: List<String>,
    val spans: List<PrompterRichTextSpan> = emptyList()
)

internal data class PrompterChordWord(
    val runs: List<PrompterChordRun>
) {
    val lyricText: String
        get() = runs.joinToString(separator = "") { it.lyricText }
}

internal data class PrompterChordRenderLine(
    val lyricText: String,
    val words: List<PrompterChordWord>,
    val hasChords: Boolean,
    val blockKind: PrompterRichTextBlockKind = PrompterRichTextBlockKind.BODY,
    val spans: List<PrompterRichTextSpan> = emptyList()
)

internal enum class PrompterRenderMode {
    PLAIN_TEXT,
    CHORD_PRO,
    RICH_TEXT
}

internal fun resolvePrompterRenderMode(document: PrompterPreparedDocument): PrompterRenderMode =
    when {
        document.hasFormatting -> PrompterRenderMode.RICH_TEXT
        document.hasChords -> PrompterRenderMode.CHORD_PRO
        else -> PrompterRenderMode.PLAIN_TEXT
    }

internal fun buildChordProPrompterLines(
    document: ChordProDocument
): List<PrompterChordRenderLine>? {
    if (!document.hasChords) return null

    return document.lines.map { line ->
        if (line.anchors.isEmpty()) {
            PrompterChordRenderLine(
                lyricText = line.lyricText,
                words = emptyList(),
                hasChords = false
            )
        } else {
            buildChordProPrompterLine(line)
        }
    }
}

private fun buildChordProPrompterLine(line: ChordProLine): PrompterChordRenderLine {
    return buildPrompterRenderLine(
        lyricText = line.lyricText,
        chordsByOffset = line.anchors.groupBy(
            keySelector = { it.lyricOffset },
            valueTransform = { it.symbol.raw }
        )
    )
}

internal fun buildRichTextPrompterLines(
    document: PrompterPreparedDocument
): List<PrompterChordRenderLine> = document.lines.map(::buildRichTextPrompterLine)

private fun buildRichTextPrompterLine(line: PrompterPreparedLine): PrompterChordRenderLine {
    if (!line.hasChords) {
        return PrompterChordRenderLine(
            lyricText = line.plainText,
            words = emptyList(),
            hasChords = false,
            blockKind = line.blockKind,
            spans = line.spans
        )
    }

    return buildPrompterRenderLine(
        lyricText = line.plainText,
        chordsByOffset = line.chords.groupBy(
            keySelector = { it.plainTextOffset },
            valueTransform = { it.symbol.raw }
        ),
        blockKind = line.blockKind,
        spans = line.spans
    )
}

private fun buildPrompterRenderLine(
    lyricText: String,
    chordsByOffset: Map<Int, List<String>>,
    blockKind: PrompterRichTextBlockKind = PrompterRichTextBlockKind.BODY,
    spans: List<PrompterRichTextSpan> = emptyList()
): PrompterChordRenderLine {
    val ranges = lyricWordRanges(lyricText).toMutableList()
    if (lyricText.isEmpty() || chordsByOffset.containsKey(lyricText.length)) {
        ranges += lyricText.length until lyricText.length
    }

    return PrompterChordRenderLine(
        lyricText = lyricText,
        words = ranges.map { range ->
            val offsets = chordsByOffset.keys
                .filter { offset ->
                    if (range.isEmpty()) offset == range.first
                    else offset >= range.first && offset <= range.last
                }
                .sorted()
            val runs = mutableListOf<PrompterChordRun>()
            var cursor = range.first

            offsets.forEachIndexed { index, offset ->
                if (offset > cursor) {
                    runs += PrompterChordRun(
                        lyricText = lyricText.substring(cursor, offset),
                        chords = emptyList(),
                        spans = spans.localTo(cursor, offset)
                    )
                }
                val nextOffset = offsets.getOrNull(index + 1) ?: (range.last + 1)
                runs += PrompterChordRun(
                    lyricText = lyricText.substring(offset, nextOffset),
                    chords = chordsByOffset.getValue(offset),
                    spans = spans.localTo(offset, nextOffset)
                )
                cursor = nextOffset
            }

            if (cursor <= range.last) {
                runs += PrompterChordRun(
                    lyricText = lyricText.substring(cursor, range.last + 1),
                    chords = emptyList(),
                    spans = spans.localTo(cursor, range.last + 1)
                )
            }
            PrompterChordWord(runs = runs)
        },
        hasChords = true,
        blockKind = blockKind,
        spans = spans
    )
}

private fun List<PrompterRichTextSpan>.localTo(
    start: Int,
    endExclusive: Int
): List<PrompterRichTextSpan> = mapNotNull { span ->
    val intersectionStart = maxOf(span.start, start)
    val intersectionEnd = minOf(span.endExclusive, endExclusive)
    if (intersectionStart >= intersectionEnd) {
        null
    } else {
        PrompterRichTextSpan(
            start = intersectionStart - start,
            endExclusive = intersectionEnd - start,
            style = span.style
        )
    }
}

private fun lyricWordRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var cursor = 0

    while (cursor < text.length) {
        val start = cursor
        if (text[cursor].isWhitespace()) {
            while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        } else {
            while (cursor < text.length && !text[cursor].isWhitespace()) cursor += 1
            while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        }
        ranges += start until cursor
    }
    return ranges
}

// ─────────────────────────────────────────────────────────────
//  1) VIEWPORT : texte avec marge top/bottom basée sur la hauteur écran
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterTextViewport(
    content: String,
    preparedDocument: PrompterPreparedDocument,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF050912),
    textColor: Color = Color.White,
    fontSize: Int = 26,
    lineHeight: Int = 32,
    startOffsetFraction: Float = 0.55f,
    bottomOffsetFraction: Float = 0.30f,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (content.isBlank()) {
            Text(
                text = "Texte introuvable.",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val topPad = maxHeight * startOffsetFraction
                val bottomPad = maxHeight * bottomOffsetFraction
                val renderMode = remember(preparedDocument) {
                    resolvePrompterRenderMode(preparedDocument)
                }
                val renderedLines = remember(preparedDocument, renderMode) {
                    when (renderMode) {
                        PrompterRenderMode.PLAIN_TEXT -> null
                        PrompterRenderMode.CHORD_PRO -> buildChordProPrompterLines(
                            ChordProDocument(
                                source = preparedDocument.source,
                                lines = preparedDocument.lines.map { it.chordProLine },
                                hasChords = preparedDocument.hasChords
                            )
                        )
                        PrompterRenderMode.RICH_TEXT -> buildRichTextPrompterLines(preparedDocument)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    Spacer(Modifier.height(topPad))

                    if (renderMode == PrompterRenderMode.PLAIN_TEXT) {
                        Text(
                            text = content,
                            color = textColor,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeight.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ChordProPrompterContent(
                            lines = requireNotNull(renderedLines),
                            textColor = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            richTextEnabled = renderMode == PrompterRenderMode.RICH_TEXT,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(bottomPad))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChordProPrompterContent(
    lines: List<PrompterChordRenderLine>,
    textColor: Color,
    fontSize: Int,
    lineHeight: Int,
    richTextEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val emptyLineHeight = with(LocalDensity.current) { lineHeight.sp.toDp() }

    Column(modifier = modifier) {
        lines.forEach { line ->
            if (richTextEnabled && line.blockKind == PrompterRichTextBlockKind.DIVIDER) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = textColor.copy(alpha = 0.35f),
                    thickness = 1.dp
                )
            } else if (!line.hasChords) {
                if (line.lyricText.isEmpty()) {
                    Spacer(Modifier.height(emptyLineHeight))
                } else if (!richTextEnabled) {
                    Text(
                        text = line.lyricText,
                        color = textColor,
                        fontSize = fontSize.sp,
                        lineHeight = lineHeight.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val typography = prompterLineTypography(
                        blockKind = line.blockKind,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        richTextEnabled = richTextEnabled
                    )
                    Text(
                        text = line.lyricText.withPrompterStyles(line.spans),
                        color = textColor,
                        fontSize = typography.fontSize.sp,
                        lineHeight = typography.lineHeight.sp,
                        fontWeight = typography.fontWeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = typography.verticalPadding.dp)
                    )
                }
            } else {
                val typography = prompterLineTypography(
                    blockKind = line.blockKind,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    richTextEnabled = richTextEnabled
                )
                val chordFontSize = (typography.fontSize * 0.70f).sp
                val chordLineHeight = (typography.fontSize * 0.82f).sp
                val chordBandHeight = with(LocalDensity.current) { chordLineHeight.toDp() }
                val lineModifier = if (richTextEnabled) {
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = typography.verticalPadding.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
                FlowRow(
                    modifier = lineModifier,
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.Top
                ) {
                    line.words.forEach { word ->
                        Row {
                            word.runs.forEach { run ->
                                Column {
                                    Row(
                                        modifier = Modifier.height(chordBandHeight),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        run.chords.forEach { chord ->
                                            Text(
                                                text = chord,
                                                color = SplColors.Accent,
                                                fontSize = chordFontSize,
                                                lineHeight = chordLineHeight,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    if (richTextEnabled) {
                                        Text(
                                            text = run.lyricText.withPrompterStyles(run.spans),
                                            color = textColor,
                                            fontSize = typography.fontSize.sp,
                                            lineHeight = typography.lineHeight.sp,
                                            fontWeight = typography.fontWeight,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    } else {
                                        Text(
                                            text = run.lyricText,
                                            color = textColor,
                                            fontSize = fontSize.sp,
                                            lineHeight = lineHeight.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PrompterLineTypography(
    val fontSize: Float,
    val lineHeight: Float,
    val fontWeight: FontWeight?,
    val verticalPadding: Int
)

private fun prompterLineTypography(
    blockKind: PrompterRichTextBlockKind,
    fontSize: Int,
    lineHeight: Int,
    richTextEnabled: Boolean
): PrompterLineTypography {
    if (!richTextEnabled) {
        return PrompterLineTypography(
            fontSize = fontSize.toFloat(),
            lineHeight = lineHeight.toFloat(),
            fontWeight = null,
            verticalPadding = 0
        )
    }
    return when (blockKind) {
        PrompterRichTextBlockKind.TITLE -> PrompterLineTypography(
            fontSize = fontSize * 1.18f,
            lineHeight = lineHeight * 1.18f,
            fontWeight = FontWeight.Bold,
            verticalPadding = 6
        )
        PrompterRichTextBlockKind.SECTION -> PrompterLineTypography(
            fontSize = fontSize * 1.08f,
            lineHeight = lineHeight * 1.08f,
            fontWeight = FontWeight.Bold,
            verticalPadding = 4
        )
        PrompterRichTextBlockKind.BODY,
        PrompterRichTextBlockKind.DIVIDER -> PrompterLineTypography(
            fontSize = fontSize.toFloat(),
            lineHeight = lineHeight.toFloat(),
            fontWeight = null,
            verticalPadding = 0
        )
    }
}

private fun String.withPrompterStyles(spans: List<PrompterRichTextSpan>): AnnotatedString =
    buildAnnotatedString {
        append(this@withPrompterStyles)
        spans.forEach { span ->
            val style = when (span.style) {
                PrompterRichTextStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                PrompterRichTextStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            addStyle(
                style = style,
                start = span.start,
                end = span.endExclusive
            )
        }
    }

// ─────────────────────────────────────────────────────────────
//  2) CONTROLES : Play/Pause + slider vitesse
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterControls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    speedRange: ClosedFloatingPointRange<Float> = 0.3f..3f,
    labelColor: Color = Color(0xFFB0BEC5),
    iconColor: Color = Color.White
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_cd_play_pause),
                    tint = iconColor
                )
            }
            Text(
                text = if (isPlaying) "Défilement" else "En pause",
                color = iconColor,
                fontSize = 14.sp
            )
        }

        Column(modifier = Modifier.width(200.dp)) {
            Text(
                text = "Vitesse (${String.format(java.util.Locale.US, "%.1fx", speed)})",
                color = labelColor,
                fontSize = 11.sp
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = speedRange
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  3) HEADER simple “retour + titre” (optionnel)
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_cd_back),
                tint = color
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title, color = color, fontSize = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────
//  4) MOTEUR AUTO-SCROLL : anime jusqu’en bas selon vitesse
// ─────────────────────────────────────────────────────────────
@Composable
fun AutoScrollEffect(
    key1: Any?,
    isPlaying: Boolean,
    speed: Float,
    scrollState: ScrollState,
    baseDurationMs: Long = 60_000L,
    initialDelayMs: Long = 50L,
    extraDelayBeforeStartMs: Long = 0L
) {
    LaunchedEffect(key1, isPlaying, speed) {
        if (!isPlaying) return@LaunchedEffect

        delay(initialDelayMs)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clamped = speed.coerceIn(0.3f, 3f)
        val duration = (baseDurationMs / clamped).toInt().coerceAtLeast(500)

        if (extraDelayBeforeStartMs > 0) delay(extraDelayBeforeStartMs)

        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
    }
}
