package com.patrick.lrcreader.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TextPrompterPrefs
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

private data class SongInfo(
    val title: String?,
    val content: String?
)

@Composable
fun TextPrompterScreen(
    modifier: Modifier = Modifier,
    songId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val songInfo = remember(songId) {
        var result = SongInfo(title = null, content = null)
        try {
            when {
                songId.startsWith("note:") -> {
                    val raw = songId.removePrefix("note:")
                    val idLong = raw.toLongOrNull()
                    if (idLong != null) {
                        val note = NotesRepository.get(context, idLong)
                        if (note != null) {
                            result = SongInfo(
                                title = note.title.ifBlank { "Texte" },
                                content = note.content
                            )
                        }
                    }
                }

                songId.startsWith("text:") -> {
                    val raw = songId.removePrefix("text:")
                    val s = TextSongRepository.get(context, raw)
                    if (s != null) result = SongInfo(title = s.title, content = s.content)
                }

                else -> {
                    val numeric = songId.toLongOrNull()
                    if (numeric != null) {
                        val note = NotesRepository.get(context, numeric)
                        if (note != null) {
                            result = SongInfo(
                                title = note.title.ifBlank { "Texte" },
                                content = note.content
                            )
                        } else {
                            val s = TextSongRepository.get(context, songId)
                            if (s != null) result = SongInfo(title = s.title, content = s.content)
                        }
                    } else {
                        val s = TextSongRepository.get(context, songId)
                        if (s != null) result = SongInfo(title = s.title, content = s.content)
                    }
                }
            }
        } catch (_: Exception) {}
        result
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isSpeedSliderOpen by remember { mutableStateOf(false) }
    val minSpeed = 0.30f
    val maxSpeed = 1.40f

    var speedFactor by remember(songId) {
        mutableStateOf(
            (TextPrompterPrefs.getSpeed(context, songId) ?: 1f)
                .coerceIn(minSpeed, maxSpeed)
        )
    }

    // ✅ Auto scroll
    LaunchedEffect(songId, isPlaying, speedFactor) {
        if (!isPlaying) return@LaunchedEffect
        delay(50)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clampedSpeed = speedFactor.coerceIn(minSpeed, maxSpeed)
        val baseDurationMs = 60_000L
        val duration = (baseDurationMs / clampedSpeed).toInt().coerceAtLeast(500)

        delay(1)
        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
    }

    DarkBlueGradientBackground {




        // ✅ Insets (base propre)
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val transportBottom = navBottom + 80.dp      // ✅ TOUJOURS POSITIF
        val transportHeight = 72.dp

        val transportNudgeY = (+140).dp              // ✅ ton réglage fin (ici tu descends/monte)

        // ✅ Vitre : élargissement gauche/droite
        val glassOverhangLeft = 30.dp
        val glassOverhangRight = 30.dp

        // ✅ Slider tiroir : dimensions
        val sliderHeight = 450.dp
        val sliderWidth = 60.dp
        val overhangRight = 18.dp

        // ✅ Réglages fins : bloc slider
        val blockOffsetY = (-20).dp
        val blockPaddingEnd = 10.dp

        // ✅ Réglages fins : bouton slider
        val buttonOffsetX = 30.dp
        val buttonOffsetY = -0.dp

        Box(modifier = modifier.fillMaxSize()) {

            // 1) TEXTE plein écran
            PrompterTextViewport(
                content = songInfo.content.orEmpty(),
                scrollState = scrollState,
                startOffsetFraction = 0.55f,
                bottomOffsetFraction = 0.30f,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            )

            // 2) PROGRESS (calculs)
            val progress =
                if (scrollState.maxValue > 0)
                    scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                else 0f

            val speed = speedFactor.coerceIn(0.3f, 3f)
            val totalMs = (60_000f / speed).toLong().coerceAtLeast(500L)
            val currentMs = (progress * totalMs.toFloat()).toLong().coerceIn(0L, totalMs)

            fun formatMs(ms: Long): String {
                val totalSec = (ms / 1000L).toInt()
                val m = totalSec / 60
                val s = totalSec % 60
                return "%d:%02d".format(m, s)
            }

            // ✅ VITRE (derrière progress + transport)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .wrapContentWidth(unbounded = true)
                    .padding(bottom = transportBottom)
                    .offset(
                        x = (glassOverhangRight - glassOverhangLeft) / 2,
                        y = transportNudgeY
                    )
                    .width(
                        LocalConfiguration.current.screenWidthDp.dp +
                                glassOverhangLeft + glassOverhangRight
                    )
                    .height(transportHeight + 44.dp)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(16.dp)
                    )
            )

// ✅ PROGRESS AU-DESSUS de la vitre
            Row(
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom + transportHeight + 2.dp
                    )
                    .offset(y = transportNudgeY),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(currentMs),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )

                Slider(
                    value = progress,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        disabledThumbColor = Color.White.copy(alpha = 0.9f),
                        disabledActiveTrackColor = Color.White.copy(alpha = 0.8f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Text(
                    text = formatMs(totalMs),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }

// ✅ TRANSPORT AU-DESSUS de la vitre
            PrompterTransportBarAudioLike(
                isPlaying = isPlaying,
                onPlayPause = { isPlaying = !isPlaying },
                onPrev = {
                    scope.launch {
                        val step = (scrollState.maxValue * 0.08f).toInt().coerceAtLeast(80)
                        val target = (scrollState.value - step).coerceAtLeast(0)
                        scrollState.animateScrollTo(target)
                    }
                },
                onNext = {
                    scope.launch {
                        val step = (scrollState.maxValue * 0.08f).toInt().coerceAtLeast(80)
                        val target = (scrollState.value + step).coerceAtMost(scrollState.maxValue)
                        scrollState.animateScrollTo(target)
                    }
                },
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom
                    )
                    .offset(y = transportNudgeY)
            )

            // 4) SLIDER + BOUTON (bloc complet)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = blockPaddingEnd)
                    .offset(y = blockOffsetY)
                    .zIndex(10f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ✅ Zone réservée : le bouton ne bouge jamais (place fixe)
                Box(
                    modifier = Modifier
                        .height(sliderHeight)
                        .width(sliderWidth + overhangRight + 9.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // ✅ “Tiroir” : le slider glisse / disparaît mais la place reste
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSpeedSliderOpen,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        VerticalTransparentSpeedSlider(
                            value = speedFactor,
                            onValueChange = { new ->
                                speedFactor = new.coerceIn(minSpeed, maxSpeed)
                                TextPrompterPrefs.saveSpeed(context, songId, speedFactor)
                            },
                            valueRange = (minSpeed..maxSpeed),   // ✅ ICI c’est ça
                            height = sliderHeight,
                            width = sliderWidth,
                            overhangRight = overhangRight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ✅ Bouton (position stable)
                FilledTonalIconButton(
                    onClick = { isSpeedSliderOpen = !isSpeedSliderOpen },
                    modifier = Modifier
                        .size(40.dp)
                        .offset(x = buttonOffsetX, y = buttonOffsetY)
                ) {
                    Icon(
                        imageVector = if (isSpeedSliderOpen)
                            Icons.Filled.KeyboardArrowRight
                        else
                            Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Ouvrir / Fermer slider"
                    )
                }
            }
        }
    }
}