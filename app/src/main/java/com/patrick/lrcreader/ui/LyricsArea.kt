// LyricsAreaLazy.kt
package com.patrick.lrcreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.LrcLine

@Composable
fun LyricsAreaLazy(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    parsedLines: List<LrcLine>,
    currentTrackUri: String?,
    lyricsLoading: Boolean,
    isConcertMode: Boolean,
    readabilityModeEnabled: Boolean,
    currentLrcIndex: Int,
    guidedReadingColorsEnabled: Boolean,
    guidedReadingColorA: Int,
    guidedReadingColorB: Int,
    lyricsTextSize: DisplayPrefs.LyricsTextSize,
    onLyricsBoxHeightChange: (Int) -> Unit,
    highlightColor: Color,
    onLineClick: (index: Int, timeMs: Long) -> Unit
) {
    val lyricSizes = lyricsTextSizes(lyricsTextSize)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onLyricsBoxHeightChange(it.size.height) }
    ) {
        if (parsedLines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val message = when {
                    currentTrackUri == null -> stringResource(R.string.lyrics_none)
                    lyricsLoading -> stringResource(R.string.lyrics_loading)
                    else -> stringResource(R.string.lyrics_none)
                }
                Text(message, color = Color.Gray)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // ✅ grosse marge haut/bas pour permettre le centrage
            contentPadding = PaddingValues(top = 220.dp, bottom = 220.dp)
        ) {
            itemsIndexed(parsedLines, key = { idx, _ -> idx }) { index, line ->
                val isActiveLine = index == currentLrcIndex
                val isNextActiveLine = !readabilityModeEnabled &&
                    index == currentLrcIndex + 1
                val manualColor = line.colorArgb?.let(::Color)
                val guidedColor = if (guidedReadingColorsEnabled) {
                    Color(if (index % 2 == 0) guidedReadingColorA else guidedReadingColorB)
                } else {
                    null
                }
                val baseColor = manualColor ?: guidedColor ?: Color.White
                val activeColor = manualColor ?: guidedColor ?: highlightColor
                val color = when {
                    isActiveLine -> activeColor
                    isNextActiveLine -> activeColor.copy(alpha = 0.62f)
                    readabilityModeEnabled -> baseColor
                    manualColor != null || guidedColor != null -> baseColor.copy(alpha = 0.72f)
                    else -> Color.White.copy(alpha = 0.42f)
                }
                val animatedColor by animateColorAsState(
                    targetValue = color,
                    animationSpec = tween(durationMillis = 160),
                    label = "lyricsLineColor"
                )
                val fontWeight = when {
                    isActiveLine -> FontWeight.Bold
                    isNextActiveLine -> FontWeight.Medium
                    readabilityModeEnabled -> FontWeight.Medium
                    else -> FontWeight.Normal
                }
                val fontSize = when {
                    isActiveLine -> lyricSizes.activeSp.sp
                    isNextActiveLine -> lyricSizes.nextSp.sp
                    else -> lyricSizes.defaultSp.sp
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .clickable { onLineClick(index, line.timeMs) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = line.text,
                        color = animatedColor,
                        fontWeight = fontWeight,
                        fontSize = fontSize,
                        lineHeight = lyricSizes.lineHeightSp.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }
    }
}

private data class LyricsTextSizes(
    val defaultSp: Int,
    val nextSp: Int,
    val activeSp: Int,
    val lineHeightSp: Int
)

private fun lyricsTextSizes(size: DisplayPrefs.LyricsTextSize): LyricsTextSizes {
    return when (size) {
        DisplayPrefs.LyricsTextSize.SMALL -> LyricsTextSizes(
            defaultSp = 21,
            nextSp = 22,
            activeSp = 23,
            lineHeightSp = 26
        )

        DisplayPrefs.LyricsTextSize.NORMAL -> LyricsTextSizes(
            defaultSp = 25,
            nextSp = 26,
            activeSp = 27,
            lineHeightSp = 30
        )

        DisplayPrefs.LyricsTextSize.LARGE -> LyricsTextSizes(
            defaultSp = 28,
            nextSp = 29,
            activeSp = 30,
            lineHeightSp = 34
        )

        DisplayPrefs.LyricsTextSize.EXTRA_LARGE -> LyricsTextSizes(
            defaultSp = 31,
            nextSp = 32,
            activeSp = 33,
            lineHeightSp = 38
        )
    }
}
