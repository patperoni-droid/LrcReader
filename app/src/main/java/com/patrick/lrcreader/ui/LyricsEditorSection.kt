package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.ChordPaletteStore
import com.patrick.lrcreader.core.CueMidiStore
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcCleaner
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.inferChordPaletteFromText
import com.patrick.lrcreader.core.insertChordAtCursor
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.parseChordPaletteInput
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val INLINE_LRC_TIME_TAG_REGEX =
    Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""")

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

    // ✅ Infos lecture (venant du PlayerScreen)
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onIsPlayingChange: (Boolean) -> Unit,

    // ✅ seek (Exo)
    seekToMs: (Long) -> Unit,

    // ✅ callback sauvegarde
    onSaveSortedLines: (List<LrcLine>) -> Unit,
    onImportedLinesApplied: (List<LrcLine>) -> Unit,
    onPersistLines: (List<LrcLine>) -> Unit,
    onDeletePersisted: () -> Unit,
    showImportButton: Boolean = true,
    mainTabLabelRes: Int = R.string.lyrics_editor_tab_lyrics,
    inputLabelRes: Int = R.string.lyrics_editor_input_label,
    enableCueEditing: Boolean = true,
    showChordPalette: Boolean = false,
    chordPaletteStorageKey: String? = null
) {
    if (!isEditingLyrics) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var isImportBusy by remember { mutableStateOf(false) }
    var rawTextFieldValue by remember(currentTrackUri, showChordPalette) {
        mutableStateOf(
            TextFieldValue(
                text = rawLyricsText,
                selection = TextRange(rawLyricsText.length)
            )
        )
    }
    var paletteInput by remember(chordPaletteStorageKey) { mutableStateOf("") }
    var paletteChords by remember(chordPaletteStorageKey) { mutableStateOf<List<String>>(emptyList()) }

    var editingCueLineIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuText by remember { mutableStateOf("") }

    LaunchedEffect(rawLyricsText) {
        if (rawLyricsText != rawTextFieldValue.text) {
            val nextCursor = rawTextFieldValue.selection.end.coerceAtMost(rawLyricsText.length)
            rawTextFieldValue = TextFieldValue(
                text = rawLyricsText,
                selection = TextRange(nextCursor)
            )
        }
    }

    fun rawToPlainLines(raw: String): List<String> =
        raw.lines()
            .map { line -> line.trim().replace(INLINE_LRC_TIME_TAG_REGEX, "").trim() }
            .filter { it.isNotEmpty() }

    fun updatePalette(raw: String, persist: Boolean) {
        paletteInput = raw
        paletteChords = parseChordPaletteInput(raw)
        val key = chordPaletteStorageKey
        if (showChordPalette && persist && !key.isNullOrBlank()) {
            if (raw.isBlank()) {
                ChordPaletteStore.clear(context, key)
            } else {
                ChordPaletteStore.saveRaw(context, key, raw)
            }
        }
    }

    fun insertChordFromPalette(chord: String) {
        val insertion = insertChordAtCursor(
            text = rawTextFieldValue.text,
            selectionStart = rawTextFieldValue.selection.start,
            selectionEnd = rawTextFieldValue.selection.end,
            chord = chord
        )
        rawTextFieldValue = TextFieldValue(
            text = insertion.text,
            selection = TextRange(insertion.cursor)
        )
        onRawLyricsTextChange(insertion.text)
    }

    LaunchedEffect(showChordPalette, chordPaletteStorageKey) {
        if (!showChordPalette) return@LaunchedEffect
        val key = chordPaletteStorageKey
        if (key.isNullOrBlank()) return@LaunchedEffect
        val saved = ChordPaletteStore.loadRaw(context, key)
        if (saved.isNotBlank()) {
            updatePalette(saved, persist = false)
            return@LaunchedEffect
        }
        val inferred = inferChordPaletteFromText(rawLyricsText)
        if (inferred.isNotEmpty()) {
            updatePalette(inferred.joinToString(", "), persist = true)
            return@LaunchedEffect
        }
        updatePalette("", persist = false)
    }

    // 🔹 Enregistrer
    fun handleSave() {
        val simpleLines = rawToPlainLines(rawTextFieldValue.text)

        if (simpleLines.isEmpty()) {
            onEditingLinesChange(emptyList())
            onRawLyricsTextChange("")
            rawTextFieldValue = TextFieldValue("", TextRange(0))
            onDeletePersisted()
            onSaveSortedLines(emptyList())
            return
        }

        val finalLines: List<LrcLine> = when (currentEditTab) {
            0 -> {
                if (editingLines.isEmpty()) {
                    simpleLines.map { txt -> LrcLine(timeMs = 0L, text = txt) }
                } else {
                    mergeLyricsWithOldTimings(
                        newLines = simpleLines,
                        oldLines = editingLines
                    )
                }
            }
            else -> editingLines.filter { it.text.isNotBlank() }
        }

        Log.d("LrcDebug", "EDITOR_SAVE currentTrackUri=$currentTrackUri lines=${finalLines.size}")

        onPersistLines(finalLines)

        onSaveSortedLines(finalLines)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        val trackUri = currentTrackUri
        if (pickedUri == null) {
            Log.d("LRC_IMPORT", "picker cancelled")
            return@rememberLauncherForActivityResult
        }
        if (trackUri.isNullOrBlank() || isImportBusy) {
            return@rememberLauncherForActivityResult
        }

        val displayName = queryDisplayName(context, pickedUri)
        val nameOk = displayName?.lowercase()?.endsWith(".lrc") == true
        val mime = runCatching { context.contentResolver.getType(pickedUri) }.getOrNull()
        val mimeOk = mime?.startsWith("text/") == true
        if (BuildConfig.DEBUG) {
            Log.d(
                "LRC_IMPORT",
                "picked uri=$pickedUri displayName=$displayName mime=$mime nameOk=$nameOk mimeOk=$mimeOk"
            )
        }
        if (!(nameOk || mimeOk)) {
            Toast.makeText(context, "Choisis un fichier .lrc", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        isImportBusy = true
        scope.launch {
            try {
                val importedText = readImportedLrcText(context, pickedUri)
                if (importedText == null) {
                    Log.e("LRC_IMPORT", "failed to read uri=$pickedUri")
                    Toast.makeText(context, "Import impossible", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (importedText.isBlank()) {
                    Log.w("LRC_IMPORT", "empty file uri=$pickedUri")
                    Toast.makeText(context, "Fichier vide", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val parsed = withContext(Dispatchers.IO) { parseLrc(importedText) }
                onPersistLines(parsed)

                onRawLyricsTextChange(importedText)
                rawTextFieldValue = TextFieldValue(
                    text = importedText,
                    selection = TextRange(importedText.length)
                )
                onEditingLinesChange(parsed)
                onImportedLinesApplied(parsed)
                Log.d("LRC_IMPORT", "imported uri=$pickedUri lines=${parsed.size}")
            } catch (t: Throwable) {
                Log.e("LRC_IMPORT", "import failed uri=$pickedUri", t)
                Toast.makeText(context, "Import impossible", Toast.LENGTH_SHORT).show()
            } finally {
                isImportBusy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Onglets + Enregistrer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TabRow(
                    selectedTabIndex = currentEditTab,
                    containerColor = Color.Transparent,
                    contentColor = highlightColor
                ) {
                    Tab(
                        selected = currentEditTab == 0,
                        onClick = { onCurrentEditTabChange(0) },
                        text = { Text(stringResource(mainTabLabelRes)) }
                    )
                    Tab(
                        selected = currentEditTab == 1,
                        onClick = {
                            onCurrentEditTabChange(1)
                        },
                        text = { Text(stringResource(R.string.lyrics_editor_tab_sync)) }
                    )
                }
            }

            if (showImportButton) {
                TextButton(
                    enabled = currentTrackUri != null && !isImportBusy,
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("Import .LRC", color = Color.White)
                }
            }

            IconButton(
                onClick = { handleSave() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.lyrics_editor_cd_save_lyrics),
                    tint = Color(0xFF80CBC4)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (currentEditTab) {
            0 -> {
                // Onglet SIMPLE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (showChordPalette) {
                        OutlinedTextField(
                            value = paletteInput,
                            onValueChange = { updatePalette(it, persist = true) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.chords_palette_input_label), color = Color.LightGray) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            singleLine = false
                        )

                        if (paletteChords.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paletteChords.forEach { chord ->
                                    TextButton(
                                        onClick = { insertChordFromPalette(chord) }
                                    ) {
                                        Text("[$chord]", color = Color(0xFF80CBC4))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    OutlinedTextField(
                        value = rawTextFieldValue,
                        onValueChange = { value ->
                            rawTextFieldValue = value
                            onRawLyricsTextChange(value.text)
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
                                stringResource(inputLabelRes),
                                color = Color.LightGray
                            )
                        }
                    )
                }
            }

            1 -> {
                // Onglet SYNCHRO
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
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    onIsPlayingChange(false)
                                } else {
                                    if (durationMs > 0) {
                                        onIsPlayingChange(true)
                                        runCatching { FillerSoundManager.fadeOutAndStop(200) }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.lyrics_editor_cd_sync_play_pause),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { runCatching { seekToMs(0L) } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.lyrics_editor_cd_back_to_start),
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
                            onClick = { onEditingLinesChange(editingLines.map { it.copy(timeMs = 0L) }) }
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_editor_reset_tags),
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }
                    }

                    val cuesForTrack = if (currentTrackUri != null) {
                        CueMidiStore.getCuesForTrack(currentTrackUri)
                    } else emptyList()

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (editingLines.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.lyrics_editor_empty_sync_hint),
                                    color = Color.Gray,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            itemsIndexed(editingLines) { index, line ->
                                val timeLabel =
                                    if (line.timeMs > 0) formatLrcTime(line.timeMs) else "--:--.--"

                                val hasCueForThisLine =
                                    enableCueEditing && cuesForTrack.any { it.lineIndex == index }

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
                                                val now = positionMs.coerceAtLeast(0)
                                                onEditingLinesChange(
                                                    editingLines.mapIndexed { i, old ->
                                                        if (i == index) old.copy(timeMs = now.toLong()) else old
                                                    }
                                                )

                                                // auto-scroll
                                                scope.launch {
                                                    val layoutInfo = lazyListState.layoutInfo
                                                    val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
                                                    if (visibleItem != null) {
                                                        val itemCenter = visibleItem.offset + visibleItem.size / 2
                                                        val viewportCenter = layoutInfo.viewportEndOffset / 2
                                                        if (itemCenter > viewportCenter) {
                                                            val nextIndex = (index + 1).coerceAtMost(editingLines.lastIndex)
                                                            lazyListState.animateScrollToItem(
                                                                nextIndex,
                                                                scrollOffset = -layoutInfo.viewportEndOffset / 3
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.lyrics_editor_tag_button), color = Color(0xFF80CBC4), fontSize = 12.sp)
                                        }
                                        Text(timeLabel, color = Color(0xFFB0BEC5), fontSize = 10.sp)
                                    }

                                    // Zone centrale : indicateur CUE + texte cliquable
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hasCueForThisLine) {
                                            Text("🎛", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                                        }

                                        Text(
                                            text = line.text,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            modifier = Modifier.combinedClickable(
                                                onClick = {
                                                    if (enableCueEditing) {
                                                        editingCueLineIndex = index
                                                    }
                                                },
                                                onLongClick = {
                                                    lineMenuIndex = index
                                                    lineMenuText = line.text
                                                }
                                            )
                                        )
                                    }

                                    // Bouton Play par ligne
                                    IconButton(
                                        onClick = {
                                            val t = line.timeMs.coerceAtLeast(0L)
                                            runCatching { seekToMs(t) }
                                            if (!isPlaying) {
                                                onIsPlayingChange(true)
                                                runCatching { FillerSoundManager.fadeOutAndStop(200) }
                                            }
                                        },
                                        enabled = line.timeMs > 0L
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.lyrics_editor_cd_play_from_line),
                                            tint = if (line.timeMs > 0L) Color.White else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Menu ÉDITER / SUPPRIMER
                    if (lineMenuIndex != null) {
                        val idx = lineMenuIndex!!

                        AlertDialog(
                            onDismissRequest = { lineMenuIndex = null },
                            title = { Text(text = stringResource(R.string.lyrics_editor_line_dialog_title, idx + 1), color = Color.White) },
                            text = {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.lyrics_editor_line_dialog_message),
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = lineMenuText,
                                        onValueChange = { lineMenuText = it },
                                        label = { Text(stringResource(R.string.lyrics_editor_line_text_label)) },
                                        singleLine = false,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val list = editingLines.toMutableList()
                                        if (idx in list.indices) {
                                            list[idx] = list[idx].copy(text = lineMenuText.trim())
                                            onEditingLinesChange(list)
                                        } else if (BuildConfig.DEBUG) {
                                            Log.w("LrcDebug", "EDIT_LINE_SKIPPED invalidIndex idx=$idx size=${list.size}")
                                        }
                                        lineMenuIndex = null
                                    }
                                ) { Text(stringResource(R.string.lyrics_editor_edit), color = Color(0xFF80CBC4)) }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            val list = editingLines.toMutableList()
                                            if (idx in list.indices) {
                                                list.removeAt(idx)
                                                onEditingLinesChange(list)
                                            } else if (BuildConfig.DEBUG) {
                                                Log.w("LrcDebug", "DELETE_LINE_SKIPPED invalidIndex idx=$idx size=${list.size}")
                                            }

                                            if (enableCueEditing && currentTrackUri != null) {
                                                CueMidiStore.deleteCue(trackUri = currentTrackUri, lineIndex = idx)
                                            }

                                            lineMenuIndex = null
                                        }
                                    ) { Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF8A80)) }

                                    TextButton(onClick = { lineMenuIndex = null }) {
                                        Text(stringResource(R.string.lyrics_editor_cancel), color = Color.LightGray)
                                    }
                                }
                            }
                        )
                    }

                    // ✅ Popup édition CUE MIDI
                    val lineIndexEditing = editingCueLineIndex
                    if (enableCueEditing && lineIndexEditing != null && currentTrackUri != null) {
                        CueMidiEditorPopup(
                            trackUri = currentTrackUri,
                            lineIndex = lineIndexEditing,
                            onClose = { editingCueLineIndex = null }
                        )
                    }
                } // <-- ferme Column onglet Synchro
            }     // <-- ferme "1 -> {"
        }         // <-- ferme when
    }             // <-- ferme Column principale
}                 // <-- ferme composable
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
        } catch (_: Exception) {
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
                .map { line -> LrcLine(timeMs = 0L, text = line) }
        }
    } catch (_: Exception) {
        null
    }
}

private fun mergeLyricsWithOldTimings(
    newLines: List<String>,
    oldLines: List<LrcLine>
): List<LrcLine> {
    if (oldLines.isEmpty()) {
        return newLines.map { lineText -> LrcLine(timeMs = 0L, text = lineText) }
    }

    val result = mutableListOf<LrcLine>()
    val used = BooleanArray(oldLines.size)

    for (newTextRaw in newLines) {
        val newText = newTextRaw.trim()
        if (newText.isEmpty()) continue

        var matchedIndex = -1
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
            result.add(old.copy(text = newText))
        } else {
            result.add(LrcLine(timeMs = 0L, text = newText))
        }
    }

    return result
}

private suspend fun readImportedLrcText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return@withContext null

    if (bytes.isEmpty()) return@withContext ""

    decodeTextWithFallback(bytes)?.trimEnd('\u0000')
}

private fun decodeTextWithFallback(bytes: ByteArray): String? {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        return decodeStrict(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return decodeStrict(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return decodeStrict(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
    }

    return decodeStrict(bytes, Charsets.UTF_8)
        ?: decodeStrict(bytes, Charsets.UTF_16LE)
        ?: decodeStrict(bytes, Charsets.UTF_16BE)
        ?: decodeStrict(bytes, Charsets.ISO_8859_1)
}

private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
    return runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    }.getOrNull()
}
