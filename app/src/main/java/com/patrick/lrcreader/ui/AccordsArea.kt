package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.buildChordsWindow
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.theme.ChordFont

private enum class ChordsDisplayMode {
    SEQUENTIAL,
    GRID
}

@Composable
fun AccordsArea(
    modifier: Modifier = Modifier,
    parsedLines: List<LrcLine>,
    currentTrackUri: String?,
    loading: Boolean,
    currentLrcIndex: Int
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        var displayMode by remember {
            mutableStateOf(ChordsDisplayMode.GRID)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChordsModeChip(
                label = "Liste",
                selected = displayMode == ChordsDisplayMode.SEQUENTIAL,
                onClick = { displayMode = ChordsDisplayMode.SEQUENTIAL }
            )
            ChordsModeChip(
                label = "Grille",
                selected = displayMode == ChordsDisplayMode.GRID,
                onClick = { displayMode = ChordsDisplayMode.GRID }
            )
        }

        if (parsedLines.isEmpty()) {
            val message = when {
                currentTrackUri == null -> stringResource(R.string.chords_none)
                loading -> stringResource(R.string.common_loading)
                else -> stringResource(R.string.chords_none)
            }
            Text(
                text = message,
                color = Color(0xFF7A7A7A),
                textAlign = TextAlign.Center
            )
            return
        }

        val safeIndex = currentLrcIndex.coerceIn(0, parsedLines.lastIndex)
        val window = remember(parsedLines, safeIndex) {
            buildChordsWindow(parsedLines, safeIndex, nextCount = 3)
        }

        when (displayMode) {
            ChordsDisplayMode.SEQUENTIAL -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    window.previous?.let { prev ->
                        Text(
                            text = formatChord(prev.text),
                            fontFamily = ChordFont,
                            color = Color(0xFF7A7A7A),
                            fontSize = 30.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    Text(
                        text = formatChord(window.current?.text.orEmpty()),
                        fontFamily = ChordFont,
                        color = Color.White,
                        fontSize = 86.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 90.sp
                    )

                    Spacer(Modifier.height(24.dp))

                    val nextSizes = listOf(42.sp, 36.sp, 32.sp)
                    window.next.forEachIndexed { i, line ->
                        Text(
                            text = formatChord(line.text),
                            fontFamily = ChordFont,
                            color = Color(0xFFCFD8DC),
                            fontSize = nextSizes.getOrElse(i) { 30.sp },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            ChordsDisplayMode.GRID -> {
                val rows = remember(parsedLines) {
                    parsedLines.withIndex().chunked(4)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { indexedLine ->
                                val isActive = indexedLine.index == safeIndex
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = if (isActive) {
                                        Color.White.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = formatChord(indexedLine.value.text),
                                            fontFamily = ChordFont,
                                            color = if (isActive) Color.White else Color(0xFFCFD8DC),
                                            fontSize = if (isActive) 34.sp else 30.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            repeat(4 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChordsModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFB0BEC5),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
