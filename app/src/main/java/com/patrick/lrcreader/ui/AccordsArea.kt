package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            window.previous?.let { prev ->
                Text(
                    text = prev.text,
                    color = Color(0xFF7A7A7A),
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                text = window.current?.text.orEmpty(),
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
                    text = line.text,
                    color = Color(0xFFCFD8DC),
                    fontSize = nextSizes.getOrElse(i) { 30.sp },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
