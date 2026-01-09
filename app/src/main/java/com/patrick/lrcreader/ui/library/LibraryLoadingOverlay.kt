package com.patrick.lrcreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun LibraryLoadingOverlay(
    isLoading: Boolean,
    moveProgress: Float?,
    moveLabel: String?,
    modifier: Modifier = Modifier
) {
    if (!isLoading) return

    val p = moveProgress?.takeIf { it.isFinite() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (p == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    moveLabel ?: "Déplacement…",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            } else {
                val clamped = p.coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { clamped },
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                val pct = (clamped * 100f).roundToInt()
                Text(
                    text = (moveLabel ?: "Copie…") + " $pct%",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }
    }
}