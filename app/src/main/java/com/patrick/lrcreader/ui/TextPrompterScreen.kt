package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Prompteur texte seul (pas d'audio).
 * Auto-scroll simple avec vitesse réglable.
 */
@Composable
fun TextPrompterScreen(
    modifier: Modifier = Modifier,
    songId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val songData = remember(songId) {
        TextSongRepository.get(context, songId)
    }

    val scrollState = rememberScrollState()

    var isPlaying by remember { mutableStateOf(true) }
    // vitesse "relative" (1f = normal, 0.5f = lent, 2f = rapide)
    var speedFactor by remember { mutableStateOf(1f) }

    // Auto-scroll
    LaunchedEffect(songId, isPlaying, speedFactor) {
        if (!isPlaying) return@LaunchedEffect
        val basePxPerSecond = 40f   // vitesse de base
        val pxPerSecond = basePxPerSecond * speedFactor.coerceIn(0.3f, 3f)
        val frameDelayMs = 16L

        while (isActive && isPlaying) {
            val max = scrollState.maxValue
            if (max <= 0) break

            val step = (pxPerSecond * (frameDelayMs / 1000f)).toInt().coerceAtLeast(1)
            val next = (scrollState.value + step).coerceAtMost(max)
            scrollState.scrollTo(next)

            if (next >= max) break
            delay(frameDelayMs)
        }
    }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = songData?.title ?: "Titre texte",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // CONTROLES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = if (isPlaying) "Défilement" else "En pause",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Column(
                    modifier = Modifier.width(180.dp)
                ) {
                    Text(
                        text = "Vitesse",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = speedFactor,
                        onValueChange = { speedFactor = it.coerceIn(0.3f, 3f) },
                        valueRange = 0.3f..3f
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // TEXTE
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050912))
            ) {
                if (songData == null) {
                    Text(
                        text = "Texte introuvable.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Text(
                        text = songData.content,
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}