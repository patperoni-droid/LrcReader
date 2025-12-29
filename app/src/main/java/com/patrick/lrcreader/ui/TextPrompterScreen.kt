package com.patrick.lrcreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TextPrompterPrefs
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlinx.coroutines.delay

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
        } catch (_: Exception) {
        }
        result
    }

    val scrollState = rememberScrollState()
    var isPlaying by remember { mutableStateOf(true) }

    var speedFactor by remember(songId) {
        mutableStateOf(
            TextPrompterPrefs.getSpeed(context, songId)?.coerceIn(0.3f, 3f) ?: 1f
        )
    }

    LaunchedEffect(songId, isPlaying, speedFactor) {
        if (!isPlaying) return@LaunchedEffect
        delay(50)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clampedSpeed = speedFactor.coerceIn(0.3f, 3f)
        val baseDurationMs = 60_000L
        val duration = (baseDurationMs / clampedSpeed).toInt().coerceAtLeast(500)
        delay(1200) // ou 1500 si tu veux vraiment le temps
        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
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
                    text = songInfo.title ?: "Titre texte",
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

                Column(modifier = Modifier.width(200.dp)) {
                    Text(
                        text = "Vitesse (${String.format("%.1fx", speedFactor)})",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = speedFactor,
                        onValueChange = {
                            speedFactor = it
                            TextPrompterPrefs.saveSpeed(context, songId, it)
                        },
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
                val content = songInfo.content
                if (content.isNullOrBlank()) {
                    Text(
                        text = "Texte introuvable.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val startOffsetFraction = 0.55f
                        val topPad = maxHeight * startOffsetFraction
                        val bottomPad = maxHeight * 0.30f

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // ✅ le texte commence bas MAIS la fenêtre reste grande
                            Spacer(Modifier.height(topPad))

                            Text(
                                text = content,
                                color = Color.White,
                                fontSize = 26.sp,
                                lineHeight = 32.sp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ✅ marge pour que la fin du texte puisse remonter correctement
                            Spacer(Modifier.height(bottomPad))
                        }
                    }
                }
            }
        }
    }
}