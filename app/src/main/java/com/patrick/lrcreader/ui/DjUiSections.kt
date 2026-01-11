package com.patrick.lrcreader.ui

import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Switch
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.dj.DjQueuedTrack

/* -------------------------------------------------------------------------- */
/*  Carte principale : volume, platines, crossfader, GO, STOP                 */
/* -------------------------------------------------------------------------- */

@Composable
fun DjMainCard(
    cardColor: Color,
    subColor: Color,
    onBg: Color,
    accentGo: Color,
    deckAGlow: Color,
    deckBGlow: Color,
    crossfadePos: Float,
    activeSlot: Int,
    deckATitle: String,
    deckBTitle: String,
    isPlaying: Boolean,
    angleA: Float,
    angleB: Float,
    pulse: Float,
    goEnabled: Boolean,
    onCrossfadeChange: (Float) -> Unit,
    onGo: () -> Unit,
    onStop: () -> Unit,
    progress: Float,
    currentPositionMs: Int,
    currentDurationMs: Int,
    onSeekTo: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Bandeau BUS DJ (un poil moins haut)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3A2C24), Color(0xFF4B372A), Color(0xFF3A2C24))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BUS DJ / MIX AUTO",
                    color = Color(0xFFFFECB3),
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }

            // Platines + crossfader + GO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deck A
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                color = if (activeSlot == 1) deckAGlow.copy(alpha = 0.4f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .graphicsLayer {
                                    rotationZ = if (activeSlot == 1) angleA else 0f
                                    val s = if (activeSlot == 1) pulse else 1f
                                    scaleX = s; scaleY = s
                                }
                                .background(Color(0xFF1F1F1F), CircleShape)
                                .border(2.dp, deckAGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(16.dp).background(Color.Black, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(deckATitle, color = onBg, fontSize = 11.sp, maxLines = 1)
                }

                // Centre
                Column(
                    modifier = Modifier.width(100.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("X-Fade", color = subColor, fontSize = 10.sp)
                    Slider(
                        value = crossfadePos,
                        onValueChange = onCrossfadeChange,
                        modifier = Modifier.height(60.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onGo,
                        enabled = goEnabled,
                        modifier = Modifier
                            .height(40.dp)
                            .width(80.dp)
                            .graphicsLayer { if (goEnabled) shadowElevation = 18f },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGo,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF555555),
                            disabledContentColor = Color(0xFF222222)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("GO", fontSize = 12.sp) }
                }

                // Deck B
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                color = if (activeSlot == 2) deckBGlow.copy(alpha = 0.4f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .graphicsLayer {
                                    rotationZ = if (activeSlot == 2) angleB else 0f
                                    val s = if (activeSlot == 2) pulse else 1f
                                    scaleX = s; scaleY = s
                                }
                                .background(Color(0xFF1F1F1F), CircleShape)
                                .border(2.dp, deckBGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(16.dp).background(Color.Black, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(deckBTitle, color = onBg, fontSize = 11.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(8.dp))



           }
        }
    }




/* -------------------------------------------------------------------------- */
/*  File d’attente                                                            */
/* -------------------------------------------------------------------------- */

@Composable
fun DjQueuePanel(
    cardColor: Color,
    subColor: Color,
    queue: List<DjQueuedTrack>,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    queueAutoPlay: Boolean,                 // ✅ NEW
    onToggleAutoPlay: (Boolean) -> Unit,    // ✅ NEW
    onPlayItem: (DjQueuedTrack) -> Unit,
    onRemoveItem: (DjQueuedTrack) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ✅ HEADER + switch bien placé (pas le bordel dans le header global)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),   // 🔥 hauteur contrôlée
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOpen) "File d’attente" else "File d’attente (${queue.size})",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Auto",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = queueAutoPlay,
                    onCheckedChange = onToggleAutoPlay
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    text = if (isOpen) "▾" else "▸",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable { onToggleOpen() }
                )
            }

            if (isOpen) {
                Spacer(Modifier.height(10.dp))

                // ✅ Liste
                queue.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayItem(item) }          // ✅ clic sur le titre = charger la platine
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            color = Color(0xFF81D4FA), // ✅ bleu clair autoplay
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "X",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable { onRemoveItem(item) }    // ✅ remove
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Résultats de recherche globale                                            */
/* -------------------------------------------------------------------------- */

@Composable
fun DjSearchResultsList(
    isVisible: Boolean,
    searchResults: List<DjEntry>,
    playingUri: String?,
    onPlay: (DjEntry) -> Unit,
    onEnqueue: (DjEntry) -> Unit
) {
    if (!isVisible) return

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
    ) {
        items(searchResults, key = { it.uri.toString() }) { entry ->
            val uriStr = entry.uri.toString()
            val isSelected = uriStr == playingUri
            DjTrackRow(
                title = entry.name,
                isPlaying = isSelected,
                onPlay = { onPlay(entry) },
                onEnqueue = { onEnqueue(entry) }
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Navigateur de dossiers / titres                                           */
/* -------------------------------------------------------------------------- */

@Composable
fun DjFolderBrowser(
    currentFolderUri: Uri?,
    visibleEntries: List<DjEntry>,
    onBg: Color,
    subColor: Color,
    isLoading: Boolean,
    onDirectoryClick: (DjEntry) -> Unit,
    onFilePlay: (DjEntry) -> Unit,
    onFileEnqueue: (DjEntry) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
    ) {
        if (currentFolderUri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Choisis un dossier pour tes titres DJ.",
                    color = subColor
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleEntries, key = { it.uri.toString() }) { entry ->
                    if (entry.isDirectory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDirectoryClick(entry) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = onBg,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(entry.name, color = onBg, fontSize = 13.sp)
                        }
                    } else {
                        DjTrackRow(
                            title = entry.name,
                            isPlaying = false, // l’état sélectionné est géré plus haut
                            onPlay = { onFilePlay(entry) },
                            onEnqueue = { onFileEnqueue(entry) }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = onBg)
            }
        }
    }
}