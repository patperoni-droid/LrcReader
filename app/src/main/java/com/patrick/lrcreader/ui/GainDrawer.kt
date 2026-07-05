package com.patrick.lrcreader.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.patrick.lrcreader.exo.R
import kotlin.math.roundToInt

@Composable
fun GainDrawer(
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    modifier: Modifier = Modifier,
    faderHeight: Dp,
    faderWidth: Dp,
    drawerWidth: Dp = faderWidth,
    endPadding: Dp = 4.dp,
    bottomPadding: Dp = 8.dp,
    buttonSize: Dp = 38.dp,
    buttonOffsetX: Dp = 6.dp,
    buttonOffsetY: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = endPadding, bottom = bottomPadding)
            .zIndex(9999f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .height(faderHeight)
                .width(drawerWidth),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (isOpen) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                down.consume()
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .height(faderHeight)
                        .width(drawerWidth),
                    contentAlignment = Alignment.CenterEnd,
                    content = content
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilledTonalIconButton(
            onClick = onToggleOpen,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = buttonOffsetX, y = buttonOffsetY)
        ) {
            Icon(
                imageVector = if (isOpen) {
                    Icons.Filled.KeyboardArrowRight
                } else {
                    Icons.Filled.KeyboardArrowLeft
                },
                contentDescription = stringResource(R.string.common_cd_toggle_slider)
            )
        }
    }
}

@Composable
fun TrackGainFader(
    gainDb: Int,
    onGainDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
    faderHeight: Dp = 390.dp,
    faderWidth: Dp = 52.dp,
    inactiveBoostZoneHeight: Dp = 58.dp
) {
    val minGainDb = -24
    val maxActiveGainDb = 6
    val visualMaxGainDb = 12
    var localGainDb by rememberSaveable { mutableIntStateOf(gainDb.coerceIn(minGainDb, maxActiveGainDb)) }
    LaunchedEffect(gainDb) {
        localGainDb = gainDb.coerceIn(minGainDb, maxActiveGainDb)
    }

    Box(
        modifier = modifier
            .width(faderWidth)
            .height(faderHeight)
    ) {
        VerticalTransparentSpeedSlider(
            value = localGainDb.toFloat(),
            onValueChange = { rawValue ->
                val targetDb = rawValue.roundToInt().coerceIn(minGainDb, maxActiveGainDb)
                if (targetDb != localGainDb) {
                    val delta = targetDb - localGainDb
                    localGainDb = targetDb
                    onGainDelta(delta)
                }
            },
            valueRange = minGainDb.toFloat()..visualMaxGainDb.toFloat(),
            modifier = Modifier.align(Alignment.Center),
            height = faderHeight,
            width = faderWidth,
            sliderOffsetX = 0.dp,
            contentOffsetX = 0.dp,
            decorOffsetX = 0.dp,
            panelTintAlpha = 0.34f,
            overhangRight = 0.dp,
            decorOverhangLeft = 0.dp,
            decorOverhangRight = 0.dp,
            corner = 12.dp,
            trackThickness = 4.dp,
            trackVerticalPadding = 18.dp,
            trackColor = Color.White.copy(alpha = 0.22f),
            filledTrackColor = Color(0xFFFFC107).copy(alpha = 0.76f),
            centeredFilledTrack = true,
            thumbHeight = 28.dp,
            thumbWidth = 50.dp,
            thumbCorner = 8.dp,
            thumbColor = Color.White.copy(alpha = 0.94f),
            thumbShadowElevation = 4.dp,
            thumbContent = {
                Text(
                    text = stringResource(R.string.library_lufs_db_value, localGainDb),
                    color = Color(0xFF111111),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            },
            borderThickness = 1.dp,
            borderAlpha = 0.36f
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(faderWidth)
                .height(inactiveBoostZoneHeight)
                .background(
                    Color(0xFF90A4AE).copy(alpha = 0.30f),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
        )
    }
}

@Composable
fun TrackGainDrawer(
    gainDb: Int,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    onGainDelta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val faderHeight = 450.dp
    val faderWidth = 52.dp

    GainDrawer(
        isOpen = isOpen,
        onToggleOpen = onToggleOpen,
        modifier = modifier,
        faderHeight = faderHeight,
        faderWidth = faderWidth
    ) {
        TrackGainFader(
            gainDb = gainDb,
            onGainDelta = onGainDelta,
            faderHeight = faderHeight,
            faderWidth = faderWidth,
            inactiveBoostZoneHeight = 48.dp
        )
    }
}
