package com.patrick.lrcreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

// Petit conteneur local pour ne pas dépendre du type exact de TextSongRepository
private data class SongInfo(
    val title: String?,
    val content: String?
)

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

    // 🔥 Résout le problème "Texte introuvable" :
    // on supporte :
    //  - "note:123"  -> NotesRepository
    //  - "text:xyz"  -> TextSongRepository
    //  - "123"       -> NotesRepository (compat)
    //  - "xyz"       -> TextSongRepository (compat)
    val songInfo = remember(songId) {
        // par défaut : rien trouvé
        var result = SongInfo(title = null, content = null)

        try {
            when {
                // NOTE moderne : note:<idLong>
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

                // Ancien texte prompteur : text:<idString>
                songId.startsWith("text:") -> {
                    val raw = songId.removePrefix("text:")
                    val s = TextSongRepository.get(context, raw)
                    if (s != null) {
                        result = SongInfo(
                            title = s.title,
                            content = s.content
                        )
                    }
                }

                else -> {
                    // Compatibilité : si c'est un nombre -> peut-être une Note
                    val numeric = songId.toLongOrNull()
                    if (numeric != null) {
                        val note = NotesRepository.get(context, numeric)
                        if (note != null) {
                            result = SongInfo(
                                title = note.title.ifBlank { "Texte" },
                                content = note.content
                            )
                        } else {
                            // sinon, on tente TextSongRepository avec l'id brut
                            val s = TextSongRepository.get(context, songId)
                            if (s != null) {
                                result = SongInfo(
                                    title = s.title,
                                    content = s.content
                                )
                            }
                        }
                    } else {
                        // id non numérique -> ancien TextSongRepository
                        val s = TextSongRepository.get(context, songId)
                        if (s != null) {
                            result = SongInfo(
                                title = s.title,
                                content = s.content
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // en cas de bug, on laisse result vide -> "Texte introuvable"
        }

        result
    }

    val scrollState = rememberScrollState()

    var isPlaying by remember { mutableStateOf(true) }
    // vitesse "relative" (1f = normal, 0.3f = très lent, 3f = très rapide)
    var speedFactor by remember { mutableStateOf(1f) }

    // 🔁 Auto-scroll basé sur une animation
    LaunchedEffect(songId, isPlaying, speedFactor) {
        if (!isPlaying) return@LaunchedEffect

        // petit délai pour laisser le temps au layout de mesurer la hauteur
        kotlinx.coroutines.delay(50)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clampedSpeed = speedFactor.coerceIn(0.3f, 3f)

        // Durée de base = 60s pour traverser tout le texte à vitesse 1.0
        val baseDurationMs = 60_000L
        val duration = (baseDurationMs / clampedSpeed).toInt().coerceAtLeast(500)

        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(
                durationMillis = duration,
                easing = LinearEasing
            )
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

                Column(
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = "Vitesse (${String.format("%.1fx", speedFactor)})",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp
                    )
                    Slider(
                        value = speedFactor,
                        onValueChange = { speedFactor = it },
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
                    Text(
                        text = content,
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