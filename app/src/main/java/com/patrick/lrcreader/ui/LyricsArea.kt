// LyricsArea.kt
// Affichage des paroles en mode MANU (défilement manuel + surlignage)

package com.patrick.lrcreader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.LrcLine
import kotlin.math.abs

@Composable
fun LyricsArea(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    parsedLines: List<LrcLine>,
    isContinuousScroll: Boolean,
    isConcertMode: Boolean,
    currentLrcIndex: Int,
    baseTopSpacerPx: Int,
    lyricsBoxHeightPx: Int,
    onLyricsBoxHeightChange: (Int) -> Unit,
    highlightColor: Color,
    rowHeightDp: Dp, // ✅ LA source de vérité (vient de PlayerScreen)
    onLineClick: (index: Int, timeMs: Long) -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                onLyricsBoxHeightChange(coords.size.height)
            }
    ) {

        // ===== COLONNE SCROLLABLE =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- espace haut pour centrer la ligne courante ---
            if (baseTopSpacerPx > 0) {
                Spacer(
                    modifier = Modifier.height(
                        with(density) { baseTopSpacerPx.toDp() }
                    )
                )
            }

            // --- PAROLES ---
            if (parsedLines.isEmpty()) {
                Text(
                    text = "Aucune parole",
                    color = Color.Gray,
                    modifier = Modifier.padding(30.dp),
                    textAlign = TextAlign.Center
                )
            } else {

                parsedLines.forEachIndexed { index, line ->

                    val (color, weight) =
                        if (isContinuousScroll) {
                            Color.White to FontWeight.Normal
                        } else {
                            val isCurrent = index == currentLrcIndex
                            val dist = abs(index - currentLrcIndex)

                            val alpha =
                                if (!isConcertMode) 1f
                                else when (dist) {
                                    0 -> 1f
                                    1 -> 0.8f
                                    2 -> 0.4f
                                    else -> 0.08f
                                }

                            highlightColor.copy(alpha = alpha) to
                                    (if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                        }

                    // 🔒 CONTENEUR DE LIGNE À HAUTEUR FIXE
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp)   // ✅ hauteur FIXE (même que PlayerScreen)
                            .clickable { onLineClick(index, line.timeMs) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = line.text,
                            color = color,
                            fontWeight = weight,
                            fontSize = 25.sp,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,  // ✅ autorise 3 lignes
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(
                                    includeFontPadding = false // ✅ évite les surprises Android
                                )
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // --- espace bas ---
            Spacer(modifier = Modifier.height(80.dp))
        }

        // ===== TRAIT HORIZONTAL DE REPÈRE (hors Column) =====
        if (lyricsBoxHeightPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x33FFFFFF))
                    .align(Alignment.TopCenter)
                    .padding(
                        top = with(density) {
                            (lyricsBoxHeightPx / 2).toDp()
                        }
                    )
            )
        }
    }
}
