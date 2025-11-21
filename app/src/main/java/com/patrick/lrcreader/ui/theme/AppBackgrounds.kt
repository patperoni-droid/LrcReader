package com.patrick.lrcreader.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 💠 Dégradé premium sombre pour toutes les pages
 * Réutilisable partout.
 */
@Composable
fun DarkBlueGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF05050A),  // noir profond
                        Color(0xFF0A0E1A),  // bleu nuit
                        Color(0xFF101C3C)   // bleu profond
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * Variante horizontale si tu veux un fond “studio”
 */
@Composable
fun DarkBlueGradientHorizontal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF05050A),
                        Color(0xFF0A0E1A),
                        Color(0xFF101C3C)
                    )
                )
            )
    ) {
        content()
    }
}