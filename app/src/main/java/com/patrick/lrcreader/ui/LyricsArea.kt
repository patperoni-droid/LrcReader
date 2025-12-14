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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import kotlin.math.abs

@Composable
fun LyricsAreaLazy(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    parsedLines: List<LrcLine>,
    isConcertMode: Boolean,
    currentLrcIndex: Int,
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
                Text("Aucune parole", color = Color.Gray)
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
                val dist = abs(index - currentLrcIndex)

                val alpha =
                    if (!isConcertMode) 1f
                    else when (dist) {
                        0 -> 1f
                        1 -> 0.8f
                        2 -> 0.4f
                        else -> 0.08f
                    }

                val color = highlightColor.copy(alpha = alpha)

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
                        fontWeight = FontWeight.Medium,
                        fontSize = 25.sp,
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
