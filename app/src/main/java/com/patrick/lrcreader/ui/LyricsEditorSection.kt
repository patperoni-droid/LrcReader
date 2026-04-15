package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.patrick.lrcreader.core.appendCapturedChordLineSorted
import com.patrick.lrcreader.core.captureLiveChord
import com.patrick.lrcreader.core.formatCapturedLiveChordLine
import com.patrick.lrcreader.core.inferChordPaletteFromText
import com.patrick.lrcreader.core.isLiveCaptureAllowed
import com.patrick.lrcreader.core.insertChordAtCursor
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.parseChordPaletteInput
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpMidiCueBridge
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val INLINE_LRC_TIME_TAG_REGEX =
    Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""")
private val LRC_TIMESTAMP_HINT_REGEX = Regex("""\[\d{1,2}:\d{2}""")

// ─────────────────────────────
//  ÉDITEUR DE PAROLES
// ─────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onPersistLines: suspend (List<LrcLine>) -> Boolean,
    onDeletePersisted: suspend () -> Boolean,
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
    var isPersistBusy by remember { mutableStateOf(false) }
    var showTimingsInLyricsTab by remember(currentTrackUri) { mutableStateOf(false) }
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
    var lastLiveCaptureClickElapsedMs by remember { mutableStateOf<Long?>(null) }
    var pendingCapturedLine by remember { mutableStateOf<LrcLine?>(null) }
    var highlightedCapturedIndex by remember { mutableStateOf<Int?>(null) }

    var lineMenuIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuText by remember { mutableStateOf("") }
    var selectedSyncLineIndices by remember(currentTrackUri) { mutableStateOf<Set<Int>>(emptySet()) }
    val displayedPalette = paletteChords

    fun stripInlineTimingTags(raw: String): String =
        raw.lines()
            .joinToString("\n") { line -> line.replace(INLINE_LRC_TIME_TAG_REGEX, "") }

    LaunchedEffect(rawLyricsText, showTimingsInLyricsTab) {
        val targetText = if (showTimingsInLyricsTab) {
            rawLyricsText
        } else {
            stripInlineTimingTags(rawLyricsText)
        }
        if (targetText != rawTextFieldValue.text) {
            val nextCursor = rawTextFieldValue.selection.end.coerceAtMost(targetText.length)
            rawTextFieldValue = TextFieldValue(
                text = targetText,
                selection = TextRange(nextCursor)
            )
        }
    }

    fun rawToPlainLines(raw: String): List<String> =
        stripInlineTimingTags(raw)
            .lines()
            .map { line -> line.trim() }
            .filter { it.isNotEmpty() }

    fun rawContainsLrcTimestamps(raw: String): Boolean =
        LRC_TIMESTAMP_HINT_REGEX.containsMatchIn(raw)

    val timingPreviewText = remember(rawTextFieldValue.text, editingLines) {
        buildLyricsTimingPreviewText(
            rawText = rawTextFieldValue.text,
            existingLines = editingLines
        )
    }
    val hasTimedLines = remember(editingLines) { editingLines.any { it.timeMs > 0L } }

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

    fun applyLinesToRawDraft(lines: List<LrcLine>) {
        val nextRaw = if (showTimingsInLyricsTab) {
            linesToEditorRawText(lines)
        } else {
            plainLinesToEditorRawText(lines)
        }
        rawTextFieldValue = TextFieldValue(
            text = nextRaw,
            selection = TextRange(nextRaw.length)
        )
        onRawLyricsTextChange(nextRaw)
    }

    fun buildLinesFromRawDraft(): List<LrcLine> {
        if (rawContainsLrcTimestamps(rawTextFieldValue.text)) {
            return parseLrc(rawTextFieldValue.text).filter { it.text.isNotBlank() }
        }
        val simpleLines = rawToPlainLines(rawTextFieldValue.text)
        if (simpleLines.isEmpty()) return emptyList()
        return if (editingLines.isEmpty()) {
            simpleLines.map { text -> LrcLine(timeMs = 0L, text = text) }
        } else {
            mergeLyricsWithOldTimings(
                newLines = simpleLines,
                oldLines = editingLines
            )
        }
    }

    fun switchEditTab(targetTab: Int) {
        if (targetTab == currentEditTab) return

        when (targetTab) {
            1 -> {
                val normalizedLines = buildLinesFromRawDraft()
                onEditingLinesChange(normalizedLines)
            }
            0 -> {
                applyLinesToRawDraft(editingLines)
            }
        }

        onCurrentEditTabChange(targetTab)
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

    fun handlePaletteChordClick(chord: String) {
        if (showChordPalette && isPlaying) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (!isLiveCaptureAllowed(nowElapsed, lastLiveCaptureClickElapsedMs)) {
                return
            }
            lastLiveCaptureClickElapsedMs = nowElapsed

            val captured = captureLiveChord(
                chord = chord,
                playerPositionMs = positionMs.toLong()
            )
            val updated = appendCapturedChordLineSorted(
                current = editingLines,
                newLine = captured
            )
            onEditingLinesChange(updated)
            scope.launch {
                // Let Compose render the new tag first, then persist.
                yield()
                onPersistLines(updated)
            }

            val plainChordLine = captured.text
            val nextRaw = if (rawTextFieldValue.text.isBlank()) {
                plainChordLine
            } else {
                rawTextFieldValue.text + "\n" + plainChordLine
            }
            rawTextFieldValue = TextFieldValue(
                text = nextRaw,
                selection = TextRange(nextRaw.length)
            )
            onRawLyricsTextChange(nextRaw)
            pendingCapturedLine = captured

            if (BuildConfig.DEBUG) {
                Log.d(
                    "CHORDS_LIVE_CAPTURE",
                    "captured=${formatCapturedLiveChordLine(captured)} size=${updated.size}"
                )
            }
            return
        }
        insertChordFromPalette(chord)
    }

    LaunchedEffect(showChordPalette, currentEditTab, pendingCapturedLine, editingLines.size) {
        val pending = pendingCapturedLine ?: return@LaunchedEffect
        if (!showChordPalette || currentEditTab != 1) return@LaunchedEffect
        val targetIndex = editingLines.indexOfLast { it.timeMs == pending.timeMs && it.text == pending.text }
        if (targetIndex < 0) return@LaunchedEffect

        val viewport = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
        val comfortableOffset = if (viewport > 0) -viewport / 4 else 0
        runCatching {
            lazyListState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = comfortableOffset
            )
        }
        highlightedCapturedIndex = targetIndex
        pendingCapturedLine = null
    }

    LaunchedEffect(highlightedCapturedIndex) {
        if (highlightedCapturedIndex == null) return@LaunchedEffect
        delay(900L)
        highlightedCapturedIndex = null
    }

    LaunchedEffect(editingLines.size) {
        selectedSyncLineIndices = selectedSyncLineIndices.filter { it in editingLines.indices }.toSet()
    }

    fun tagLineAt(index: Int) {
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

    fun toggleSyncLineSelection(index: Int) {
        selectedSyncLineIndices = if (index in selectedSyncLineIndices) {
            selectedSyncLineIndices - index
        } else {
            selectedSyncLineIndices + index
        }
    }

    fun mergeSelectedSyncLines() {
        val selected = selectedSyncLineIndices.toList().sorted()
        if (selected.size < 2) return
        val isAdjacent = selected.zipWithNext().all { (a, b) -> b == a + 1 }
        if (!isAdjacent) {
            Toast.makeText(
                context,
                context.getString(R.string.lyrics_editor_merge_non_adjacent_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val firstIndex = selected.first()
        val selectedLines = selected.mapNotNull { editingLines.getOrNull(it) }
        if (selectedLines.size < 2) return

        val mergedText = selectedLines
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val mergedLine = selectedLines.first().copy(text = mergedText)
        val nextLines = buildList {
            editingLines.forEachIndexed { index, line ->
                when {
                    index == firstIndex -> add(mergedLine)
                    index in selectedSyncLineIndices -> Unit
                    else -> add(line)
                }
            }
        }
        onEditingLinesChange(nextLines)
        applyLinesToRawDraft(nextLines)
        selectedSyncLineIndices = setOf(firstIndex)
    }

    @Composable
    fun ChordPaletteChipsRow() {
        if (!showChordPalette || displayedPalette.isEmpty()) return
        FlowRow(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            displayedPalette.forEach { chord ->
                TextButton(
                    onClick = { handlePaletteChordClick(chord) },
                    modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("[$chord]", color = Color(0xFF80CBC4), fontSize = 13.sp)
                }
            }
        }
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
        if (isPersistBusy) return
        val rawHasTimestamps = rawContainsLrcTimestamps(rawTextFieldValue.text)
        val parsedRawLines = if (rawHasTimestamps) {
            parseLrc(rawTextFieldValue.text).filter { it.text.isNotBlank() }
        } else {
            emptyList()
        }
        val simpleLines = if (rawHasTimestamps) {
            parsedRawLines.map { it.text }
        } else {
            rawToPlainLines(rawTextFieldValue.text)
        }
        scope.launch {
            isPersistBusy = true
            try {
                if (simpleLines.isEmpty()) {
                    val deleted = onDeletePersisted()
                    if (!deleted) return@launch
                    onEditingLinesChange(emptyList())
                    onRawLyricsTextChange("")
                    rawTextFieldValue = TextFieldValue("", TextRange(0))
                    onSaveSortedLines(emptyList())
                    return@launch
                }

                val finalLines: List<LrcLine> = when (currentEditTab) {
                    0 -> {
                        if (rawHasTimestamps) {
                            parsedRawLines
                        } else if (editingLines.isEmpty()) {
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

                val persisted = onPersistLines(finalLines)
                if (!persisted) return@launch

                onSaveSortedLines(finalLines)
            } finally {
                isPersistBusy = false
            }
        }
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
            Toast.makeText(context, context.getString(R.string.lyrics_editor_import_choose_lrc), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        isImportBusy = true
        scope.launch {
            try {
                val importedText = readImportedLrcText(context, pickedUri)
                if (importedText == null) {
                    Log.e("LRC_IMPORT", "failed to read uri=$pickedUri")
                    Toast.makeText(context, context.getString(R.string.lyrics_editor_import_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (importedText.isBlank()) {
                    Log.w("LRC_IMPORT", "empty file uri=$pickedUri")
                    Toast.makeText(context, context.getString(R.string.lyrics_editor_import_empty), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.lyrics_editor_import_failed), Toast.LENGTH_SHORT).show()
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
                        onClick = { switchEditTab(0) },
                        text = { Text(stringResource(mainTabLabelRes)) }
                    )
                    Tab(
                        selected = currentEditTab == 1,
                        onClick = { switchEditTab(1) },
                        text = { Text(stringResource(R.string.lyrics_editor_tab_sync)) }
                    )
                }
            }

            if (showImportButton) {
                TextButton(
                    enabled = currentTrackUri != null && !isImportBusy && !isPersistBusy,
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(stringResource(R.string.lyrics_editor_import_lrc), color = Color.White)
                }
            }

            IconButton(
                onClick = { handleSave() },
                enabled = !isPersistBusy,
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

                        if (displayedPalette.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            ChordPaletteChipsRow()
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    if (hasTimedLines) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showTimingsInLyricsTab = !showTimingsInLyricsTab }
                            ) {
                                Text(
                                    text = if (showTimingsInLyricsTab) {
                                        "Masquer les timings"
                                    } else {
                                        "Afficher les timings"
                                    },
                                    color = Color(0xFF80CBC4),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (showTimingsInLyricsTab) {
                        OutlinedTextField(
                            value = timingPreviewText,
                            onValueChange = {},
                            readOnly = true,
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
                    } else {
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

                    if (showChordPalette && displayedPalette.isNotEmpty()) {
                        ChordPaletteChipsRow()
                        Spacer(Modifier.height(8.dp))
                    }

                    // Reset TAGs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedSyncLineIndices.size > 1) {
                            TextButton(
                                onClick = { mergeSelectedSyncLines() }
                            ) {
                                Text(
                                    text = stringResource(R.string.lyrics_editor_merge),
                                    color = Color(0xFF80CBC4),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                if (showChordPalette) {
                                    val cleared = emptyList<LrcLine>()
                                    onEditingLinesChange(cleared)
                                    rawTextFieldValue = TextFieldValue("", TextRange(0))
                                    onRawLyricsTextChange("")
                                    selectedSyncLineIndices = emptySet()
                                    scope.launch {
                                        yield()
                                        onPersistLines(cleared)
                                    }
                                } else {
                                    onEditingLinesChange(editingLines.map { it.copy(timeMs = 0L) })
                                    selectedSyncLineIndices = emptySet()
                                }
                            }
                        ) {
                            Text(
                                text = if (showChordPalette) {
                                    stringResource(R.string.chords_editor_clear_all)
                                } else {
                                    stringResource(R.string.lyrics_editor_reset_tags)
                                },
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }
                    }

                    val cuesForTrack = if (currentTrackUri != null) {
                        SmpMidiCueBridge.getEditorCues(context, currentTrackUri, editingLines)
                            ?: CueMidiStore.getCuesForTrack(currentTrackUri)
                    } else {
                        emptyList()
                    }

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
                                val isSelected = index in selectedSyncLineIndices

                                val hasCueForThisLine =
                                    enableCueEditing && cuesForTrack.any { it.lineIndex == index }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .pointerInput(index, selectedSyncLineIndices) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    selectedSyncLineIndices = if (selectedSyncLineIndices.isEmpty()) {
                                                        setOf(index)
                                                    } else {
                                                        selectedSyncLineIndices + index
                                                    }
                                                }
                                            )
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Colonne gauche : TAG + time
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.lyrics_editor_tag_button),
                                            color = Color(0xFF80CBC4),
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = { tagLineAt(index) },
                                                    onLongClick = {
                                                        lineMenuIndex = index
                                                        lineMenuText = line.text
                                                    }
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
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
                                            color = when {
                                                isSelected -> Color(0xFF80CBC4)
                                                index == highlightedCapturedIndex -> Color(0xFFFFF176)
                                                else -> Color.White
                                            },
                                            fontSize = 16.sp,
                                            modifier = Modifier.pointerInput(index, line.text) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (selectedSyncLineIndices.isNotEmpty()) {
                                                            toggleSyncLineSelection(index)
                                                        } else {
                                                            lineMenuIndex = index
                                                            lineMenuText = line.text
                                                        }
                                                    },
                                                    onLongPress = {
                                                        if (selectedSyncLineIndices.isEmpty()) {
                                                            selectedSyncLineIndices = setOf(index)
                                                        } else {
                                                            selectedSyncLineIndices = selectedSyncLineIndices + index
                                                        }
                                                    }
                                                )
                                            }.padding(vertical = 4.dp)
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
                                        enabled = line.timeMs > 0L && selectedSyncLineIndices.isEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.lyrics_editor_cd_play_from_line),
                                            tint = if (line.timeMs > 0L && selectedSyncLineIndices.isEmpty()) Color.White else Color.DarkGray
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
                                            applyLinesToRawDraft(list)
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
                                                applyLinesToRawDraft(list)
                                                selectedSyncLineIndices = selectedSyncLineIndices.filter { it in list.indices }.toSet()
                                            } else if (BuildConfig.DEBUG) {
                                                Log.w("LrcDebug", "DELETE_LINE_SKIPPED invalidIndex idx=$idx size=${list.size}")
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

private fun buildLyricsTimingPreviewText(
    rawText: String,
    existingLines: List<LrcLine>
): String {
    val plainLines = rawText
        .lines()
        .map { line -> line.trim().replace(INLINE_LRC_TIME_TAG_REGEX, "").trim() }
        .filter { it.isNotEmpty() }

    if (plainLines.isEmpty()) return ""

    val previewLines = if (existingLines.isEmpty()) {
        plainLines.map { text -> LrcLine(timeMs = 0L, text = text) }
    } else {
        mergeLyricsWithOldTimings(
            newLines = plainLines,
            oldLines = existingLines
        )
    }

    return previewLines.joinToString("\n") { line ->
        if (line.timeMs > 0L) {
            "[${formatLrcTime(line.timeMs)}] ${line.text}"
        } else {
            line.text
        }
    }
}

private fun linesToEditorRawText(lines: List<LrcLine>): String {
    return lines.joinToString("\n") { line ->
        if (line.timeMs > 0L) {
            "[${formatLrcTime(line.timeMs)}] ${line.text}"
        } else {
            line.text
        }
    }
}

private fun plainLinesToEditorRawText(lines: List<LrcLine>): String {
    return lines.joinToString("\n") { line -> line.text }
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
