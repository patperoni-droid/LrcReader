package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.zIndex

@Composable
fun VerticalTransparentSpeedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.3f..3f,
    modifier: Modifier = Modifier,
    height: Dp = 550.dp,
    width: Dp = 100.dp,

    sliderOffsetX: Dp = 20.dp,        // décale le slider (touch + visuel)
    contentOffsetX: Dp = -15.dp,        // déplace SEULEMENT le mécanisme (pas la vitre)
    decorOffsetX: Dp = 20.dp,          // ✅ déplace SEULEMENT la vitre (pas le mécanisme)

    // visuel
    panelTintAlpha: Float = 0.42f,
    overhangRight: Dp = 0.dp,
    decorOverhangLeft: Dp = 0.dp,
    decorOverhangRight: Dp = 40.dp,
    corner: Dp = 14.dp,

    // track & thumb
    trackThickness: Dp = 10.dp,
    thumbHeight: Dp = 25.dp,
    thumbWidth: Dp = 55.dp,
    thumbCorner: Dp = 1.dp,

    // bordure vitre
    borderThickness: Dp = 2.dp,
    borderAlpha: Float = 0.80f
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(corner)
    val thumbShape = RoundedCornerShape(thumbCorner)

    BoxWithConstraints(
        modifier = modifier
            .width(width + decorOverhangLeft + decorOverhangRight)
            .height(height),
        contentAlignment = Alignment.CenterEnd
    ) {
        val min = valueRange.start
        val max = valueRange.endInclusive
        val v = value.coerceIn(min, max)
        val t = ((v - min) / (max - min)).coerceIn(0f, 1f)

        val panelHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val travelPx = (panelHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbYpx = (1f - t) * travelPx

        // ✅ 1) VITRE (fond + bord) FIXE (mais décalable via decorOffsetX)
        Box(
            modifier = Modifier
                .zIndex(100f)
                .fillMaxSize()
                .offset(x = -decorOverhangLeft + decorOffsetX) // ✅ vitre déborde à gauche
                .clip(shape)
                .background(Color.Black.copy(alpha = panelTintAlpha))
                .border(borderThickness, Color.White.copy(alpha = borderAlpha), shape)
        )

        // ✅ 2) MÉCANISME (trait + curseur) DÉPLACABLE
        Box(
            modifier = Modifier
                .zIndex(101f)
                .align(Alignment.CenterEnd)
                .width(width)
                .fillMaxHeight()
                .offset(x = sliderOffsetX + contentOffsetX) // ✅ bouge uniquement le mécanisme
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
            // Trait central
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