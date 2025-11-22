/**
 * ---------------------------------------------------------------
 *  LYRICS EDITOR SECTION
 *  Ce fichier contient TOUT l’écran d’édition des paroles.
 *
 *  Cet écran apparaît quand on clique sur le bouton "crayon"
 *  dans le lecteur (PlayerScreen).
 *
 *  Fonctions incluses :
 *   - Onglet SIMPLE : édition des paroles ligne par ligne
 *   - Onglet SYNCHRO : TAG + synchronisation des phrases
 *   - Import paroles depuis le MP3
 *   - Lecture/Pause pendant la synchro
 *   - Boutons TAG / Reset TAG
 *   - Sauvegarde / Annuler
 *
 *  En résumé : C’est le module complet d’édition des paroles
 *  utilisé avant la lecture ou le prompteur.
 * ---------------------------------------------------------------
 */
package com.patrick.lrcreader.ui


import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcCleaner
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.pauseWithFade
import kotlinx.coroutines.launch

@Composable
fun LyricsEditorSection(
    highlightColor: Color,
    currentTrackUri: String?,
    isEditingLyrics: Boolean,
    onCloseEditor: () -> Unit,
    rawLyricsText: String,
    onRawLyricsTextChange: (String) -> Unit,
    editingLines: List<LrcLine>,
    onEditingLinesChange: (List<LrcLine>) -> Unit,
    currentEditTab: Int,
    onCurrentEditTabChange: (Int) -> Unit,
    mediaPlayer: MediaPlayer,
    positionMs: Int,
    durationMs: Int,
    onIsPlayingChange: (Boolean) -> Unit,
    onSaveSortedLines: (List<LrcLine>) -> Unit
) {
    if (!isEditingLyrics) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Éditeur de paroles",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCloseEditor) {
                Text("Fermer", color = Color(0xFFFF8A80))
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = currentEditTab,
            containerColor = Color.Transparent,
            contentColor = highlightColor
        ) {
            Tab(
                selected = currentEditTab == 0,
                onClick = { onCurrentEditTabChange(0) },
                text = { Text("Simple") }
            )
            Tab(
                selected = currentEditTab == 1,
                onClick = {
                    val lines = rawLyricsText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    onEditingLinesChange(
                        lines.mapIndexed { index, lineText ->
                            val old = editingLines.getOrNull(index)
                            if (old != null) old.copy(text = lineText)
                            else LrcLine(timeMs = 0L, text = lineText)
                        }
                    )

                    onCurrentEditTabChange(1)
                },
                text = { Text("Synchro") }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (currentEditTab) {
            0 -> {
                // ---------- Onglet SIMPLE ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val uri = currentTrackUri
                                if (uri != null) {
                                    val imported =
                                        importLyricsFromAudio(context, uri)
                                    if (imported != null && imported.isNotEmpty()) {
                                        onEditingLinesChange(imported)
                                        onRawLyricsTextChange(
                                            imported.joinToString("\n") { it.text }
                                        )
                                        onCurrentEditTabChange(1)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "Importer depuis MP3",
                                color = Color(0xFF80CBC4),
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rawLyricsText,
                        onValueChange = { newText ->
                            onRawLyricsTextChange(newText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        label = {
                            Text(
                                "Paroles (une ligne par phrase)",
                                color = Color.LightGray
                            )
                        }
                    )
                }
            }

            1 -> {
                // ---------- Onglet SYNCHRO ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Mini player synchro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = {
                            if (mediaPlayer.isPlaying) {
                                pauseWithFade(scope, mediaPlayer, 200L) {
                                    onIsPlayingChange(false)
                                    runCatching {
                                        FillerSoundManager.startIfConfigured(context)
                                    }
                                }
                            } else {
                                if (durationMs > 0) {
                                    mediaPlayer.start()
                                    onIsPlayingChange(true)
                                    runCatching {
                                        FillerSoundManager.fadeOutAndStop(200)
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (mediaPlayer.isPlaying)
                                    Icons.Filled.Pause
                                else
                                    Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause synchro",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            runCatching { mediaPlayer.seekTo(0) }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Revenir début",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = formatMs(positionMs),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }

                    // Reset TAGs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onEditingLinesChange(
                                    editingLines.map {
                                        it.copy(timeMs = 0L)
                                    }
                                )
                            }
                        ) {
                            Text(
                                text = "Reset TAGs",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Liste des lignes taguables
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (editingLines.isEmpty()) {
                            Text(
                                "Ajoute d’abord des paroles dans l’onglet Simple.",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            editingLines.forEachIndexed { index, line ->
                                val timeLabel =
                                    if (line.timeMs > 0)
                                        formatLrcTime(line.timeMs)
                                    else
                                        "--:--.--"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val now = runCatching {
                                                    mediaPlayer.currentPosition
                                                }.getOrElse { 0 }

                                                onEditingLinesChange(
                                                    editingLines.mapIndexed { i, old ->
                                                        if (i == index)
                                                            old.copy(
                                                                timeMs = now.toLong()
                                                            )
                                                        else old
                                                    }
                                                )
                                            }
                                        ) {
                                            Text(
                                                text = "TAG",
                                                color = Color(0xFF80CBC4),
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = timeLabel,
                                            color = Color(0xFFB0BEC5),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Text(
                                        text = line.text,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            val t = line.timeMs
                                                .coerceAtLeast(0L)
                                                .toInt()
                                            runCatching { mediaPlayer.seekTo(t) }
                                            if (!mediaPlayer.isPlaying) {
                                                mediaPlayer.start()
                                                onIsPlayingChange(true)
                                                runCatching {
                                                    FillerSoundManager.fadeOutAndStop(
                                                        200
                                                    )
                                                }
                                            }
                                        },
                                        enabled = line.timeMs > 0L
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Lire depuis cette phrase",
                                            tint = if (line.timeMs > 0L)
                                                Color.White
                                            else
                                                Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Barre d’actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onCloseEditor) {
                Text("Annuler", color = Color.LightGray)
            }
            TextButton(onClick = {
                var lines = editingLines
                if (lines.isEmpty()) {
                    lines = rawLyricsText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { LrcLine(timeMs = 0L, text = it) }
                }

                val sorted = lines.sortedWith(
                    compareBy<LrcLine> {
                        if (it.timeMs <= 0L) Long.MAX_VALUE else it.timeMs
                    }
                )

                onSaveSortedLines(sorted)
            }) {
                Text("Enregistrer", color = Color(0xFF80CBC4))
            }
        }
    }
}

/* ─────────────────────────────
   FONCTIONS UTILITAIRES (LOCALES À L'ÉDITEUR)
   ───────────────────────────── */

private fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Format LRC style : mm:ss.xx */
private fun formatLrcTime(ms: Long): String {
    if (ms <= 0L) return "00:00.00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

/**
 * Essaie d'importer les paroles intégrées dans le MP3 (Musicolet ou autre).
 * Ne modifie jamais le MP3.
 */
private fun importLyricsFromAudio(
    context: Context,
    uriString: String
): List<LrcLine>? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(uriString))

        val raw: String? = try {
            val field = MediaMetadataRetriever::class.java.getField("METADATA_KEY_LYRICS")
            val key = field.getInt(null)
            retriever.extractMetadata(key)
        } catch (e: Exception) {
            null
        }

        retriever.release()

        if (raw.isNullOrBlank()) return null

        val cleaned = LrcCleaner.clean(raw)

        if (cleaned.isNotEmpty()) {
            cleaned.map { it.copy(timeMs = 0L, text = it.text) }
        } else {
            raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    LrcLine(timeMs = 0L, text = line)
                }
        }
    } catch (_: Exception) {
        null
    }
}