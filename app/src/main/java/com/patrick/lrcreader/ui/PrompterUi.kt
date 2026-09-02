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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.ChordProDocument
import com.patrick.lrcreader.core.ChordProLine
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.theme.SplColors
import kotlinx.coroutines.delay

internal data class PrompterChordRun(
    val lyricText: String,
    val chords: List<String>
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
    val hasChords: Boolean
)

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
    val anchorsByOffset = line.anchors.groupBy { it.lyricOffset }
    val ranges = lyricWordRanges(line.lyricText).toMutableList()
    if (line.lyricText.isEmpty() || anchorsByOffset.containsKey(line.lyricText.length)) {
        ranges += line.lyricText.length until line.lyricText.length
    }

    return PrompterChordRenderLine(
        lyricText = line.lyricText,
        words = ranges.map { range ->
            val offsets = anchorsByOffset.keys
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
                        lyricText = line.lyricText.substring(cursor, offset),
                        chords = emptyList()
                    )
                }
                val nextOffset = offsets.getOrNull(index + 1) ?: (range.last + 1)
                runs += PrompterChordRun(
                    lyricText = line.lyricText.substring(offset, nextOffset),
                    chords = anchorsByOffset.getValue(offset).map { it.symbol.raw }
                )
                cursor = nextOffset
            }

            if (cursor <= range.last) {
                runs += PrompterChordRun(
                    lyricText = line.lyricText.substring(cursor, range.last + 1),
                    chords = emptyList()
                )
            }
            PrompterChordWord(runs = runs)
        },
        hasChords = true
    )
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
    chordProDocument: ChordProDocument,
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
                val chordProLines = remember(chordProDocument) {
                    buildChordProPrompterLines(chordProDocument)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    Spacer(Modifier.height(topPad))

                    if (chordProLines == null) {
                        Text(
                            text = content,
                            color = textColor,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeight.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ChordProPrompterContent(
                            lines = chordProLines,
                            textColor = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
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
    modifier: Modifier = Modifier
) {
    val chordFontSize = (fontSize * 0.70f).sp
    val chordLineHeight = (fontSize * 0.82f).sp
    val emptyLineHeight = with(LocalDensity.current) { lineHeight.sp.toDp() }
    val chordBandHeight = with(LocalDensity.current) { chordLineHeight.toDp() }

    Column(modifier = modifier) {
        lines.forEach { line ->
            if (!line.hasChords) {
                if (line.lyricText.isEmpty()) {
                    Spacer(Modifier.height(emptyLineHeight))
                } else {
                    Text(
                        text = line.lyricText,
                        color = textColor,
                        fontSize = fontSize.sp,
                        lineHeight = lineHeight.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
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
