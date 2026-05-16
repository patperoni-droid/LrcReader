// LyricsAreaLazy.kt
package com.patrick.lrcreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.LrcLine
import kotlin.math.abs

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
    activeLyricsLineCount: Int,
    onLyricsBoxHeightChange: (Int) -> Unit,
    highlightColor: Color,
    onLineClick: (index: Int, timeMs: Long) -> Unit
) {
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
                val blockSize = activeLyricsLineCount.coerceIn(1, 3)
                val activeBlockStartIndex = (currentLrcIndex / blockSize) * blockSize
                val isActiveBlockLine = blockSize > 1 &&
                    index in activeBlockStartIndex until (activeBlockStartIndex + blockSize)
                val isNextActiveLine = !readabilityModeEnabled &&
                    blockSize == 1 &&
                    index == currentLrcIndex + 1
                val baseColor = line.colorArgb?.let(::Color) ?: Color.White
                val color = when {
                    isActiveLine -> line.colorArgb?.let(::Color) ?: highlightColor
                    isActiveBlockLine -> line.colorArgb?.let(::Color) ?: highlightColor
                    isNextActiveLine -> (line.colorArgb?.let(::Color) ?: highlightColor).copy(alpha = 0.62f)
                    readabilityModeEnabled -> baseColor
                    line.colorArgb != null -> baseColor.copy(alpha = 0.72f)
                    else -> Color.White.copy(alpha = 0.42f)
                }
                val fontWeight = when {
                    isActiveLine -> FontWeight.Bold
                    isActiveBlockLine -> FontWeight.Bold
                    isNextActiveLine -> FontWeight.Medium
                    readabilityModeEnabled -> FontWeight.Medium
                    else -> FontWeight.Normal
                }
                val fontSize = when {
                    isActiveLine -> 27.sp
                    isActiveBlockLine -> 27.sp
                    isNextActiveLine -> 26.sp
                    else -> 25.sp
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
                        color = color,
                        fontWeight = fontWeight,
                        fontSize = fontSize,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }
    }
}
