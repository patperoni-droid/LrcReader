package com.patrick.lrcreader.ui.adaptive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SmpAdaptiveTokens(
    val tabletMode: Boolean,
    val isLandscape: Boolean,
    val playerControlButtonSize: Dp,
    val playerPrimaryButtonSize: Dp,
    val playlistRowHeight: Dp,
    val screenPadding: Dp,
    val lyricsHorizontalPadding: Dp,
    val lyricsVerticalContentPadding: Dp,
    val lyricsFontBoost: Int
)

@Composable
fun rememberSmpAdaptiveTokens(): SmpAdaptiveTokens {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp

    return if (isTablet) {
        SmpAdaptiveTokens(
            tabletMode = true,
            isLandscape = isLandscape,
            playerControlButtonSize = 56.dp,
            playerPrimaryButtonSize = 76.dp,
            playlistRowHeight = if (isLandscape) 64.dp else 68.dp,
            screenPadding = 20.dp,
            lyricsHorizontalPadding = 24.dp,
            lyricsVerticalContentPadding = if (isLandscape) 180.dp else 220.dp,
            lyricsFontBoost = 2
        )
    } else {
        SmpAdaptiveTokens(
            tabletMode = false,
            isLandscape = isLandscape,
            playerControlButtonSize = 48.dp,
            playerPrimaryButtonSize = 64.dp,
            playlistRowHeight = 56.dp,
            screenPadding = 16.dp,
            lyricsHorizontalPadding = 8.dp,
            lyricsVerticalContentPadding = 220.dp,
            lyricsFontBoost = 0
        )
    }
}
