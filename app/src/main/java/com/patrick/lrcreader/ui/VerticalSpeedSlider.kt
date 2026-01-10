package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalTransparentSpeedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.3f..3f,
    modifier: Modifier = Modifier,
    height: Dp = 550.dp,
    width: Dp = 100.dp,
    sliderOffsetX: Dp = 15.dp, // ✅ décalage interne du slider (trait + curseur)

    // visuel
    panelTintAlpha: Float = 0.22f,     // 👈 plus opaque = texte derrière moins présent
    overhangRight: Dp = 18.dp,         // 👈 le décor déborde à droite
    corner: Dp = 14.dp,                // 👈 moins arrondi (décor)

    // track & thumb
    trackThickness: Dp = 4.dp,         // 👈 trait central fin
    thumbHeight: Dp = 25.dp,           // 👈 curseur moins grand
    thumbWidth: Dp = 55.dp,
    thumbCorner: Dp = 1.dp             // 👈 moins arrondi (curseur)
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(corner)
    val thumbShape = RoundedCornerShape(thumbCorner)

    BoxWithConstraints(
        modifier = modifier
            .width(width)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        val min = valueRange.start
        val max = valueRange.endInclusive
        val v = value.coerceIn(min, max)
        val t = ((v - min) / (max - min)).coerceIn(0f, 1f)

        // px
        val panelH = maxHeight
        val panelHeightPx = with(density) { panelH.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val travelPx = (panelHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbYpx = (1f - t) * travelPx

        // ✅ 1) DÉCOR (UNE SEULE FOIS) + débord à droite
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 10.dp)
                .width(width + overhangRight)              // côté droit plus large
                .fillMaxHeight()
                .clip(shape)
                .background(Color.Black.copy(alpha = panelTintAlpha))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
        )

        // ✅ 2) ZONE INTERACTIVE (SANS décor, sinon double-cadre)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = sliderOffsetX) // ✅ décale le slider (visuel + touch)
                .pointerInput(min, max, panelHeightPx, thumbHeightPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val y = (offset.y - thumbHeightPx / 2f).coerceIn(0f, travelPx)
                            val tt = 1f - (y / travelPx)
                            onValueChange(min + tt * (max - min))
                        },
                        onDrag = { change, _ ->
                            change.consumeAllChanges()
                            val y = (change.position.y - thumbHeightPx / 2f).coerceIn(0f, travelPx)
                            val tt = 1f - (y / travelPx)
                            onValueChange(min + tt * (max - min))
                        }
                    )
                }
        ) {
            // Trait central (fin)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(trackThickness)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.20f))
            )

            // Curseur
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(density) { thumbYpx.toDp() })
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(thumbShape)
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }
    }
}