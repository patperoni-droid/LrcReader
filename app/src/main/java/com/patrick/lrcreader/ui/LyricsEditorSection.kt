package com.patrick.lrcreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.util.Log
import com.patrick.lrcreader.core.CueMidi
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import com.patrick.lrcreader.core.CueMidiStore
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import com.patrick.lrcreader.core.LrcStorage   // 🟢 AJOUT IMPORTANT

// ─────────────────────────────
//  ÉDITEUR DE PAROLES
// ─────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
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
    val lazyListState = rememberLazyListState()
    var editingCueLineIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuText by remember { mutableStateOf("") }

    // 🔹 Toute la logique "Enregistrer" (copiée de ta barre du bas)
    fun handleSave() {
        // 1) On récupère les lignes du texte brut
        val simpleLines = rawLyricsText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Cas "j'efface tout" → on vide vraiment tout
        if (simpleLines.isEmpty()) {
            onEditingLinesChange(emptyList())
            onRawLyricsTextChange("")

            if (currentTrackUri != null) {
                LrcStorage.deleteForTrack(context, currentTrackUri)
            }

            onSaveSortedLines(emptyList())
            return
        }

        // 2) Quelle est la source de vérité ?
        val finalLines: List<LrcLine> = when (currentEditTab) {
            0 -> {
                // Onglet SIMPLE :
                // ➜ On ESSAIE de garder les timings existants (editingLines)
                if (editingLines.isEmpty()) {
                    // Rien d'ancien → on crée tout à 0 ms
                    simpleLines.map { txt ->
                        LrcLine(timeMs = 0L, text = txt)
                    }
                } else {
                    // On fusionne le nouveau texte avec les anciens timings
                    mergeLyricsWithOldTimings(
                        newLines = simpleLines,
                        oldLines = editingLines
                    )
                }
            }

            else -> {
                // Onglet SYNCHRO : on fait CONFIANCE à editingLines
                // (c'est là que tu as tagué les phrases)
                editingLines
                    .filter { it.text.isNotBlank() }
            }
        }

        // 🟢 LOG côté éditeur
        Log.d(
            "LrcDebug",
            "EDITOR_SAVE currentTrackUri=$currentTrackUri lines=${finalLines.size}"
        )

        // 3) Sauvegarde sur disque
        if (currentTrackUri != null) {
            LrcStorage.saveForTrack(
                context = context,
                trackUriString = currentTrackUri,
                lines = finalLines
            )
        }

        // 4) Mise à jour de l’état côté parent / lecteur
        onSaveSortedLines(finalLines)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 🔹 Ligne du haut : onglets + petit bouton Enregistrer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // Onglets
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
                            // On ne touche plus aux timings ici,
                            // on fait juste changer d’onglet.
                            onCurrentEditTabChange(1)
                        },
                        text = { Text("Synchro") }
                    )
                }
            }

            IconButton(
                onClick = { handleSave() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Enregistrer les paroles",
                    tint = Color(0xFF80CBC4)
                )
            }
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
                    OutlinedTextField(
                        value = rawLyricsText,
                        onValueChange = onRawLyricsTextChange,
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
                    // Mini player synchro (Play/Pause + retour début + timecode)
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
                            text = formatMsLyricsEditor(positionMs),
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

                    // Cues MIDI pour ce morceau
                    val cuesForTrack = CueMidiStore.getCuesForTrack(currentTrackUri)

                    // Liste des lignes taguables
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (editingLines.isEmpty()) {
                            item {
                                Text(
                                    "Ajoute d’abord des paroles dans l’onglet Simple.",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            itemsIndexed(editingLines) { index, line ->
                                val timeLabel =
                                    if (line.timeMs > 0)
                                        formatLrcTime(line.timeMs)
                                    else
                                        "--:--.--"

                                val hasCueForThisLine =
                                    cuesForTrack.any { it.lineIndex == index }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Colonne gauche : TAG + time
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
                                                            old.copy(timeMs = now.toLong())
                                                        else old
                                                    }
                                                )

                                                // auto-scroll
                                                scope.launch {
                                                    val layoutInfo = lazyListState.layoutInfo
                                                    val visibleItem =
                                                        layoutInfo.visibleItemsInfo
                                                            .find { it.index == index }
                                                    if (visibleItem != null) {
                                                        val itemCenter =
                                                            visibleItem.offset + visibleItem.size / 2
                                                        val viewportCenter =
                                                            layoutInfo.viewportEndOffset / 2
                                                        if (itemCenter > viewportCenter) {
                                                            val nextIndex =
                                                                (index + 1).coerceAtMost(
                                                                    editingLines.lastIndex
                                                                )
                                                            lazyListState.animateScrollToItem(
                                                                nextIndex,
                                                                scrollOffset = -layoutInfo.viewportEndOffset / 3
                                                            )
                                                        }
                                                    }
                                                }
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

                                    // Zone centrale : indicateur CUE + texte cliquable
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hasCueForThisLine) {
                                            Text(
                                                text = "🎛",
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }

                                        Text(
                                            text = line.text,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        // Clic normal → ouvre le CUE MIDI
                                                        editingCueLineIndex = index
                                                    },
                                                    onLongClick = {
                                                        // Appui long → ouvre le mini-menu (éditer / supprimer)
                                                        lineMenuIndex = index
                                                        lineMenuText = line.text
                                                    }
                                                )
                                        )
                                    }

                                    // Bouton Play par ligne
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
                                                    FillerSoundManager.fadeOutAndStop(200)
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

                    // ─────────────────────────────
                    //  Menu ÉDITER / SUPPRIMER la ligne (appui long)
                    // ─────────────────────────────
                    if (lineMenuIndex != null) {
                        val idx = lineMenuIndex!!

                        AlertDialog(
                            onDismissRequest = { lineMenuIndex = null },
                            title = {
                                Text(
                                    text = "Ligne ${idx + 1}",
                                    color = Color.White
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Modifier le texte ou supprimer la phrase.",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 13.sp
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = lineMenuText,
                                        onValueChange = { lineMenuText = it },
                                        label = { Text("Texte de la phrase") },
                                        singleLine = false,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                            },
                            confirmButton = {
                                // Bouton "Modifier"
                                TextButton(
                                    onClick = {
                                        val list = editingLines.toMutableList()
                                        if (idx in list.indices) {
                                            list[idx] = list[idx].copy(
                                                text = lineMenuText.trim()
                                            )
                                            onEditingLinesChange(list)
                                        }
                                        lineMenuIndex = null
                                    }
                                ) {
                                    Text("Modifier", color = Color(0xFF80CBC4))
                                }
                            },
                            dismissButton = {
                                Row {
                                    // Bouton "Supprimer"
                                    TextButton(
                                        onClick = {
                                            val list = editingLines.toMutableList()
                                            if (idx in list.indices) {
                                                list.removeAt(idx)
                                                onEditingLinesChange(list)
                                            }

                                            // On supprime aussi le CUE éventuel de cette ligne
                                            if (currentTrackUri != null) {
                                                CueMidiStore.deleteCue(
                                                    trackUri = currentTrackUri,
                                                    lineIndex = idx
                                                )
                                            }

                                            lineMenuIndex = null
                                        }
                                    ) {
                                        Text("Supprimer", color = Color(0xFFFF8A80))
                                    }

                                    TextButton(onClick = { lineMenuIndex = null }) {
                                        Text("Annuler", color = Color.LightGray)
                                    }
                                }
                            }
                        )
                    }

                    val lineIndexEditing = editingCueLineIndex
                    if (lineIndexEditing != null && currentTrackUri != null) {
                        CueMidiEditorPopup(
                            lineIndex = lineIndexEditing,
                            currentTrackUri = currentTrackUri,
                            onClose = { editingCueLineIndex = null }
                        )
                    }

                }
            }
        }
    }
}

// ─────────────────────────────
//  POPUP CUE MIDI
// ─────────────────────────────

@Composable
fun CueMidiEditorPopup(
    lineIndex: Int,
    currentTrackUri: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val existingCue = remember(currentTrackUri, lineIndex) {
        CueMidiStore
            .getCuesForTrack(currentTrackUri)
            .firstOrNull { it.lineIndex == lineIndex }
    }

    var channelText by remember {
        mutableStateOf(
            (existingCue?.channel ?: 1).toString()
        )
    }
    var programText by remember {
        mutableStateOf(
            (existingCue?.program ?: 1).toString()
        )
    }

    val channelError = channelText.toIntOrNull()?.let { it !in 1..16 } ?: false
    val programError = programText.toIntOrNull()?.let { it !in 1..128 } ?: false

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = "CUE MIDI – Ligne ${lineIndex + 1}",
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ce CUE enverra un Program Change à ton pédalier quand cette phrase sera jouée.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = channelText,
                    onValueChange = { channelText = it },
                    label = { Text("Canal MIDI (1–16)") },
                    isError = channelError,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    )
                )
                if (channelError) {
                    Text(
                        text = "Canal entre 1 et 16",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = programText,
                    onValueChange = { programText = it },
                    label = { Text("Programme (1–128)") },
                    isError = programError,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    )
                )
                if (programError) {
                    Text(
                        text = "Programme entre 1 et 128",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

                if (existingCue != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Un CUE existe déjà pour cette ligne. Tu peux le modifier ou le supprimer.",
                        color = Color(0xFFFFF59D),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trackKey = currentTrackUri
                    if (trackKey == null) {
                        onClose()
                        return@TextButton
                    }

                    val chan = channelText.toIntOrNull()?.coerceIn(1, 16) ?: 1
                    val prog = programText.toIntOrNull()?.coerceIn(1, 128) ?: 1

                    val cue = CueMidi(
                        lineIndex = lineIndex,
                        channel = chan,
                        program = prog
                    )

                    CueMidiStore.upsertCue(trackKey, cue)
                    onClose()
                }
            ) {
                Text("Enregistrer", color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            Row {
                if (existingCue != null && currentTrackUri != null) {
                    TextButton(
                        onClick = {
                            CueMidiStore.deleteCue(
                                trackUri = currentTrackUri,
                                lineIndex = lineIndex
                            )
                            onClose()
                        }
                    ) {
                        Text("Supprimer", color = Color(0xFFFF8A80))
                    }
                }

                TextButton(onClick = onClose) {
                    Text("Annuler", color = Color.LightGray)
                }
            }
        }
    )
}

// ─────────────────────────────
//  FONCTIONS UTILITAIRES
// ─────────────────────────────

private fun formatMsLyricsEditor(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatLrcTime(ms: Long): String {
    if (ms <= 0L) return "00:00.00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

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
        return null
    }
}

/**
 * Fusionne un nouveau texte avec les anciens timings.
 *
 * - Si une ligne existait déjà (même texte après trim) → on garde son timeMs.
 * - Si le texte est nouveau ou n'existait pas avant → timeMs = 0.
 *
 * Ça permet :
 *  - de corriger une faute sur UNE ligne (elle perd son TAG, normal)
 *  - tout en gardant les TAGs pour les autres lignes inchangées.
 */
private fun mergeLyricsWithOldTimings(
    newLines: List<String>,
    oldLines: List<LrcLine>
): List<LrcLine> {
    if (oldLines.isEmpty()) {
        return newLines.map { lineText ->
            LrcLine(timeMs = 0L, text = lineText)
        }
    }

    val result = mutableListOf<LrcLine>()
    val used = BooleanArray(oldLines.size)

    for (newTextRaw in newLines) {
        val newText = newTextRaw.trim()
        if (newText.isEmpty()) continue

        var matchedIndex = -1

        // On cherche une ancienne ligne avec exactement le même texte (après trim),
        // qui n'a pas déjà été utilisée.
        for (i in oldLines.indices) {
            if (used[i]) continue
            if (oldLines[i].text.trim() == newText) {
                matchedIndex = i
                break
            }
        }

        if (matchedIndex >= 0) {
            used[matchedIndex] = true
            val old = oldLines[matchedIndex]
            // On garde le timeMs, on prend le texte nouveau (au cas où tu as changé un espace, etc.)
            result.add(
                old.copy(text = newText)
            )
        } else {
            // Nouvelle ligne → pas de timing
            result.add(
                LrcLine(timeMs = 0L, text = newText)
            )
        }
    }

    return result
}