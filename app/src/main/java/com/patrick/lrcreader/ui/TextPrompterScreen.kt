package com.patrick.lrcreader.ui

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TextPrompterPrefs
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues

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
    var isSpeedSliderOpen by remember { mutableStateOf(true) }
    var speedFactor by remember(songId) {
        mutableStateOf(TextPrompterPrefs.getSpeed(context, songId)?.coerceIn(0.3f, 3f) ?: 1f)
    }

    // ✅ Auto scroll
    LaunchedEffect(songId, isPlaying, speedFactor) {
        if (!isPlaying) return@LaunchedEffect
        delay(50)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clampedSpeed = speedFactor.coerceIn(0.3f, 3f)
        val baseDurationMs = 60_000L
        val duration = (baseDurationMs / clampedSpeed).toInt().coerceAtLeast(500)

        delay(1200)
        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
    }

    // ✅ padding dynamique (nav bars)

    DarkBlueGradientBackground {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val transportBottom = navBottom + 80.dp
        val transportHeight = 72.dp

        Box(modifier = Modifier.fillMaxSize()) {

            // 1) TEXTE : plein écran, PAS de réserve => le texte passe dessous le slider
            PrompterTextViewport(
                content = songInfo.content.orEmpty(),
                scrollState = scrollState,
                startOffsetFraction = 0.55f,
                bottomOffsetFraction = 0.30f,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
            )

            // 2) BARRE PROGRESSION (lecture seule) + timings
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
            // 3) BARRE DE TRANSPORT (boutons)
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
                    .zIndex(3f) // ✅ AU-DESSUS de la vitre
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom
                    )
            )

// ✅ VITRE derrière "progress + transport" (au BON endroit)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = transportBottom // ✅ CRUCIAL : remonte la vitre
                    )
                    .fillMaxWidth()
                    .height(transportHeight + 44.dp) // ajuste si besoin
                    .zIndex(2f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            )
            Row(
                modifier = Modifier
                    .zIndex(3f) // ✅ AU-DESSUS de la vitre
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = transportBottom + transportHeight + 2.dp
                    ),
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


            // 4) SLIDER VITESSE : EN DERNIER => AU-DESSUS DU TEXTE + décor
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .offset(y = (-80).dp)   // ✅ ICI : remonte TOUT le bloc
                    .zIndex(10f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // SLIDER
                VerticalTransparentSpeedSlider(
                    value = speedFactor,
                    onValueChange = { new ->
                        speedFactor = new
                        TextPrompterPrefs.saveSpeed(context, songId, new)
                    },
                    overhangRight = 18.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // BOUTON OUVRIR / FERMER
                FilledTonalIconButton(
                    onClick = { isSpeedSliderOpen = !isSpeedSliderOpen },
                    modifier = Modifier.size(40.dp)
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