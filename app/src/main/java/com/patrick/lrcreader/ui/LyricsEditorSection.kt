package com.patrick.lrcreader.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrick.lrcreader.core.ChordPaletteStore
import com.patrick.lrcreader.core.CueMidiStore
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.LrcCleaner
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LyricsViewMode
import com.patrick.lrcreader.core.appendCapturedChordLineSorted
import com.patrick.lrcreader.core.captureLiveChord
import com.patrick.lrcreader.core.clearAllChordsKeepingPaletteAndLyrics
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
private const val FIRST_CHORD_TIME_MS = 10L
private const val LYRICS_PLAYER_SYNC_DIAG_TAG = "LYRICS_PLAYER_SYNC_DIAG"
private const val LYRICS_PERSIST_DIAG_TAG = "LYRICS_PERSIST_DIAG"
private const val LYRICS_PIPELINE_TRACE_TAG = "LYRICS_PIPELINE_TRACE"
private const val LYRICS_AUTOSAVE_CRASH_DIAG_TAG = "LYRICS_AUTOSAVE_CRASH_DIAG"

private object LyricsEditorHintPrefs {
    private const val PREFS_NAME = "lyrics_editor_hint_prefs"
    private const val KEY_DISMISSED = "lyrics_editor_hint_dismissed"

    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISMISSED, false)

    fun setDismissed(context: Context, dismissed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISSED, dismissed)
            .apply()
    }
}

// ─────────────────────────────
//  ÉDITEUR DE PAROLES
// ─────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsEditorSection(
    highlightColor: Color,
    currentTrackUri: String?,
    currentSongId: String? = null,
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
    onPersistSucceeded: (List<LrcLine>) -> Unit,
    onPersistLines: suspend (List<LrcLine>) -> Boolean,
    onDeletePersisted: suspend () -> Boolean,
    onDraftLinesPrepared: (LyricsViewMode, List<LrcLine>) -> Unit = { _, _ -> },
    onSaveEditorSession: suspend (LyricsViewMode, List<LrcLine>, Boolean) -> Boolean = { _, lines, closeAfterSave ->
        val persisted = if (lines.isEmpty()) {
            onDeletePersisted()
        } else {
            onPersistLines(lines)
        }
        if (persisted) {
            if (closeAfterSave) {
                onSaveSortedLines(lines)
            } else {
                onPersistSucceeded(lines)
            }
        }
        persisted
    },
    currentContentMode: LyricsViewMode = LyricsViewMode.LYRICS,
    onContentModeChange: (LyricsViewMode) -> Unit = {},
    compactEditorTabs: Boolean = false,
    inputLabelRes: Int = R.string.lyrics_editor_input_label,
    enableCueEditing: Boolean = true,
    showChordPalette: Boolean = false,
    saveAndCloseRequestToken: Int = 0,
    chordPaletteStorageKey: String? = null,
    tabletFocusEditingMode: Boolean = false,
    onTabletFocusModeChange: (TabletPlayerFocusMode) -> Unit = {},
    playbackControlContent: @Composable () -> Unit = {},
    headerEndContent: @Composable RowScope.() -> Unit = {}
) {
    if (!isEditingLyrics) return

    BackHandler(onBack = onCloseEditor)

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
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

    var rawTextFieldFocused by remember { mutableStateOf(false) }
    var lineMenuIndex by remember { mutableStateOf<Int?>(null) }
    var lineMenuText by remember { mutableStateOf("") }
    var lineMenuColorArgb by remember { mutableStateOf<Int?>(null) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val tabletLineEditFocusActive = tabletFocusEditingMode &&
        currentEditTab != 2 &&
        rawTextFieldFocused &&
        keyboardVisible
    var selectedSyncLineIndices by remember(currentTrackUri) { mutableStateOf<Set<Int>>(emptySet()) }
    var previousEditingLines by remember(currentTrackUri) { mutableStateOf<List<LrcLine>?>(null) }
    var showEditorHintDialog by remember { mutableStateOf(false) }
    var editorHintDoNotShowAgain by remember { mutableStateOf(false) }
    var hasShownEditorHintThisSession by remember { mutableStateOf(false) }
    var showChordHelpDialog by remember { mutableStateOf(false) }
    var lastAutoSavedLyricsSignature by remember(currentTrackUri, showChordPalette) { mutableStateOf<String?>(null) }
    val displayedPalette = paletteChords
    LaunchedEffect(tabletLineEditFocusActive) {
        onTabletFocusModeChange(
            if (tabletLineEditFocusActive) {
                TabletPlayerFocusMode.LYRICS
            } else {
                TabletPlayerFocusMode.NONE
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { onTabletFocusModeChange(TabletPlayerFocusMode.NONE) }
    }
    val editorHintTitleRes = if (showChordPalette) {
        R.string.chords_editor_hint_title
    } else {
        R.string.lyrics_editor_hint_title
    }
    val editorHintMessageRes = if (showChordPalette) {
        R.string.chords_editor_hint_message
    } else {
        R.string.lyrics_editor_hint_message
    }
    val editorHintDoNotShowAgainRes = if (showChordPalette) {
        R.string.chords_editor_hint_do_not_show_again
    } else {
        R.string.lyrics_editor_hint_do_not_show_again
    }
    val lyricsTabLabelRes = if (compactEditorTabs) {
        R.string.lyrics_editor_tab_lyrics_short
    } else {
        R.string.lyrics_editor_tab_lyrics
    }
    val chordsTabLabelRes = if (compactEditorTabs) {
        R.string.lyrics_editor_tab_chords_short
    } else {
        R.string.player_view_chords
    }
    val syncTabLabelRes = if (compactEditorTabs) {
        R.string.lyrics_editor_tab_sync_short
    } else {
        R.string.lyrics_editor_tab_sync
    }

    LaunchedEffect(currentEditTab) {
        if (
            currentEditTab == 2 &&
            !hasShownEditorHintThisSession &&
            !LyricsEditorHintPrefs.isDismissed(context)
        ) {
            hasShownEditorHintThisSession = true
            showEditorHintDialog = true
        }
    }

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

    fun rememberUndoSnapshot() {
        previousEditingLines = editingLines.map { it.copy() }
    }

    fun applyEditingLinesWithUndo(
        lines: List<LrcLine>,
        updateRawDraft: Boolean = true
    ) {
        rememberUndoSnapshot()
        onEditingLinesChange(lines)
        if (updateRawDraft) {
            applyLinesToRawDraft(lines)
        }
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

    fun buildPersistableLinesForCurrentDraft(): List<LrcLine> {
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
        if (simpleLines.isEmpty()) return emptyList()

        return when (currentEditTab) {
            0, 1 -> {
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
    }

    fun lyricsSignature(lines: List<LrcLine>): String =
        lines.joinToString(separator = "\n") { line ->
            "${line.timeMs}\u0001${line.colorArgb ?: ""}\u0001${line.text}"
        }

    fun switchEditTab(targetTab: Int) {
        if (targetTab == currentEditTab) return

        when (targetTab) {
            2 -> {
                val normalizedLines = buildLinesFromRawDraft()
                applyEditingLinesWithUndo(normalizedLines, updateRawDraft = false)
            }
            0, 1 -> {
                applyLinesToRawDraft(editingLines)
            }
        }

        onCurrentEditTabChange(targetTab)
    }

    fun switchContentTab(targetTab: Int, targetMode: LyricsViewMode) {
        if (targetMode == currentContentMode) {
            switchEditTab(targetTab)
            return
        }
        val finalLines = buildPersistableLinesForCurrentDraft()
        onDraftLinesPrepared(currentContentMode, finalLines)
        previousEditingLines = null
        onContentModeChange(targetMode)
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
        if (showChordPalette && currentEditTab == 2) {
            if (!isPlaying) {
                val firstChordLine = captureLiveChord(
                    chord = chord,
                    playerPositionMs = FIRST_CHORD_TIME_MS,
                    compensationMs = 0L
                )
                val existingStartIndex = editingLines.indexOfFirst { it.timeMs in 0L..FIRST_CHORD_TIME_MS }
                val updated = if (existingStartIndex >= 0) {
                    editingLines.toMutableList().apply {
                        this[existingStartIndex] = firstChordLine
                    }
                } else {
                    appendCapturedChordLineSorted(
                        current = editingLines,
                        newLine = firstChordLine
                    )
                }
                applyEditingLinesWithUndo(updated)
                pendingCapturedLine = firstChordLine
                runCatching { seekToMs(0L) }
                if (durationMs > 0) {
                    onIsPlayingChange(true)
                    runCatching { FillerSoundManager.fadeOutAndStop(200) }
                }
                scope.launch {
                    yield()
                    if (onPersistLines(updated)) {
                        onPersistSucceeded(updated)
                    }
                }
                return
            }

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
            applyEditingLinesWithUndo(updated)
            scope.launch {
                // Let Compose render the new tag first, then persist.
                yield()
                if (onPersistLines(updated)) {
                    onPersistSucceeded(updated)
                }
            }
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
        if (!showChordPalette || currentEditTab != 2) return@LaunchedEffect
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
        applyEditingLinesWithUndo(
            editingLines.mapIndexed { i, old ->
                if (i == index) old.copy(timeMs = now.toLong()) else old
            },
            updateRawDraft = false
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
        applyEditingLinesWithUndo(nextLines)
        selectedSyncLineIndices = emptySet()
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

    suspend fun persistCurrentDraft(closeAfterSave: Boolean) {
        val finalLines = buildPersistableLinesForCurrentDraft()
        if (!showChordPalette) {
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "EDITOR_EXIT_FLUSH_START"
            )
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_REQUEST reason=manual_or_exit"
            )
            Log.d(
                LYRICS_PIPELINE_TRACE_TAG,
                "SAVE_REQUEST songId=${currentSongId ?: currentTrackUri.orEmpty()} reason=manual_or_exit lineCount=${finalLines.size} colorCount=${finalLines.count { it.colorArgb != null }}"
            )
            Log.d(
                LYRICS_PLAYER_SYNC_DIAG_TAG,
                "FORCE_SAVE_BEFORE_EXIT_EDITOR songId=${currentSongId ?: currentTrackUri.orEmpty()} lineCount=${finalLines.size}"
            )
        }
        while (isPersistBusy) {
            if (!showChordPalette) {
                Log.d(
                    LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                    "AUTOSAVE_CONCURRENT_BLOCKED"
                )
            }
            delay(100L)
        }
        isPersistBusy = true
        try {
            if (finalLines.isEmpty()) {
                val saved = onSaveEditorSession(currentContentMode, finalLines, closeAfterSave)
                if (!saved) return
                previousEditingLines = editingLines.map { it.copy() }
                rawTextFieldValue = TextFieldValue("", TextRange(0))
                if (!showChordPalette) {
                    Log.d(LYRICS_AUTOSAVE_CRASH_DIAG_TAG, "EDITOR_EXIT_FLUSH_OK")
                }
                return
            }

            Log.d("LrcDebug", "EDITOR_SAVE currentTrackUri=$currentTrackUri lines=${finalLines.size}")
            if (!showChordPalette) {
                Log.d(
                    LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                    "AUTOSAVE_START pendingChangeCount=${finalLines.size}"
                )
                Log.d(
                    LYRICS_PLAYER_SYNC_DIAG_TAG,
                    "AUTOSAVE_START reason=manual_or_exit songId=${currentSongId ?: currentTrackUri.orEmpty()} lineCount=${finalLines.size}"
                )
            }

            val persisted = onSaveEditorSession(currentContentMode, finalLines, closeAfterSave)
            if (!persisted) {
                if (!showChordPalette) {
                    Log.e(
                        LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                        "AUTOSAVE_WRITE_FAIL exception=onPersistLines_false"
                    )
                    Log.e(
                        LYRICS_PLAYER_SYNC_DIAG_TAG,
                        "AUTOSAVE_FAIL error=onPersistLines_false reason=manual_or_exit songId=${currentSongId ?: currentTrackUri.orEmpty()}"
                    )
                }
                return
            }

            previousEditingLines = null
            if (!showChordPalette) {
                Log.d(LYRICS_AUTOSAVE_CRASH_DIAG_TAG, "EDITOR_EXIT_FLUSH_OK")
                Log.d(
                    LYRICS_PLAYER_SYNC_DIAG_TAG,
                    "AUTOSAVE_SUCCESS songId=${currentSongId ?: currentTrackUri.orEmpty()} lineCount=${finalLines.size} colorCount=${finalLines.count { it.colorArgb != null }}"
                )
            }
        } finally {
            isPersistBusy = false
        }
    }

    // 🔹 Enregistrer
    fun handleSave() {
        scope.launch { persistCurrentDraft(closeAfterSave = false) }
    }

    val autoSaveLyricsLines = remember(
        showChordPalette,
        rawTextFieldValue.text,
        editingLines,
        currentEditTab
    ) {
        if (showChordPalette) emptyList() else buildPersistableLinesForCurrentDraft()
    }
    val autoSaveLyricsSignature = remember(autoSaveLyricsLines) {
        lyricsSignature(autoSaveLyricsLines)
    }

    LaunchedEffect(currentTrackUri, showChordPalette, autoSaveLyricsSignature) {
        if (showChordPalette || currentTrackUri.isNullOrBlank()) return@LaunchedEffect

        if (lastAutoSavedLyricsSignature == null) {
            lastAutoSavedLyricsSignature = autoSaveLyricsSignature
            return@LaunchedEffect
        }
        if (lastAutoSavedLyricsSignature == autoSaveLyricsSignature) return@LaunchedEffect

        Log.d(
            LYRICS_PLAYER_SYNC_DIAG_TAG,
            "AUTOSAVE_SCHEDULED reason=editor_change songId=${currentSongId ?: currentTrackUri.orEmpty()}"
        )
        Log.d(
            LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
            "AUTOSAVE_REQUEST reason=editor_change"
        )
        Log.d(
            LYRICS_PIPELINE_TRACE_TAG,
            "SAVE_REQUEST songId=${currentSongId ?: currentTrackUri.orEmpty()} reason=editor_change_scheduled lineCount=${autoSaveLyricsLines.size} colorCount=${autoSaveLyricsLines.count { it.colorArgb != null }}"
        )
        delay(900L)
        while (isPersistBusy) {
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_CONCURRENT_BLOCKED"
            )
            delay(100L)
        }

        val finalLines = buildPersistableLinesForCurrentDraft()
        val finalSignature = lyricsSignature(finalLines)
        if (lastAutoSavedLyricsSignature == finalSignature) return@LaunchedEffect

        isPersistBusy = true
        try {
            if (finalLines.isEmpty()) {
                val deleted = onDeletePersisted()
                if (!deleted) return@LaunchedEffect
                previousEditingLines = editingLines.map { it.copy() }
                onEditingLinesChange(emptyList())
                onRawLyricsTextChange("")
                rawTextFieldValue = TextFieldValue("", TextRange(0))
                onPersistSucceeded(emptyList())
                lastAutoSavedLyricsSignature = finalSignature
                return@LaunchedEffect
            }

            Log.d("LrcDebug", "EDITOR_AUTOSAVE currentTrackUri=$currentTrackUri lines=${finalLines.size}")
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_START pendingChangeCount=${finalLines.size}"
            )
            Log.d(
                LYRICS_PLAYER_SYNC_DIAG_TAG,
                "AUTOSAVE_START reason=editor_change songId=${currentSongId ?: currentTrackUri.orEmpty()} lineCount=${finalLines.size}"
            )
            val persisted = onPersistLines(finalLines)
            if (!persisted) {
                Log.e(
                    LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                    "AUTOSAVE_WRITE_FAIL exception=onPersistLines_false"
                )
                Log.e(
                    LYRICS_PLAYER_SYNC_DIAG_TAG,
                    "AUTOSAVE_FAIL error=onPersistLines_false reason=editor_change songId=${currentSongId ?: currentTrackUri.orEmpty()}"
                )
                return@LaunchedEffect
            }

            onPersistSucceeded(finalLines)
            lastAutoSavedLyricsSignature = finalSignature
            Log.d(
                LYRICS_PLAYER_SYNC_DIAG_TAG,
                "AUTOSAVE_SUCCESS songId=${currentSongId ?: currentTrackUri.orEmpty()} lineCount=${finalLines.size} colorCount=${finalLines.count { it.colorArgb != null }}"
            )
        } finally {
            isPersistBusy = false
        }
    }

    var consumedSaveAndCloseRequestToken by remember {
        mutableIntStateOf(saveAndCloseRequestToken)
    }
    LaunchedEffect(saveAndCloseRequestToken) {
        if (
            saveAndCloseRequestToken > 0 &&
            saveAndCloseRequestToken != consumedSaveAndCloseRequestToken
        ) {
            consumedSaveAndCloseRequestToken = saveAndCloseRequestToken
            persistCurrentDraft(closeAfterSave = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (showEditorHintDialog || showChordHelpDialog) {
            val isManualChordHelp = showChordHelpDialog && showChordPalette
            AlertDialog(
                onDismissRequest = {
                    showEditorHintDialog = false
                    showChordHelpDialog = false
                },
                title = {
                    Text(
                        text = stringResource(
                            if (isManualChordHelp) {
                                R.string.chords_editor_help_title
                            } else {
                                editorHintTitleRes
                            }
                        ),
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(
                                if (isManualChordHelp) {
                                    R.string.chords_editor_help_message
                                } else {
                                    editorHintMessageRes
                                }
                            ),
                            color = Color(0xFFB0BEC5)
                        )
                        if (!isManualChordHelp) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = editorHintDoNotShowAgain,
                                    onCheckedChange = { editorHintDoNotShowAgain = it }
                                )
                                Text(
                                    text = stringResource(editorHintDoNotShowAgainRes),
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!isManualChordHelp && editorHintDoNotShowAgain) {
                                LyricsEditorHintPrefs.setDismissed(context, true)
                            }
                            showEditorHintDialog = false
                            showChordHelpDialog = false
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (isManualChordHelp) {
                                    R.string.common_close
                                } else {
                                    R.string.common_ok
                                }
                            ),
                            color = Color(0xFF80CBC4)
                        )
                    }
                }
            )
        }

        if (!tabletLineEditFocusActive) {
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
                            onClick = { switchContentTab(0, LyricsViewMode.LYRICS) },
                            text = { Text(stringResource(lyricsTabLabelRes)) }
                        )
                        Tab(
                            selected = currentEditTab == 1,
                            onClick = { switchContentTab(1, LyricsViewMode.CHORDS) },
                            text = { Text(stringResource(chordsTabLabelRes)) }
                        )
                        Tab(
                            selected = currentEditTab == 2,
                            onClick = { switchEditTab(2) },
                            text = { Text(stringResource(syncTabLabelRes)) }
                        )
                    }
                }

                if (showChordPalette && currentEditTab == 2) {
                    TextButton(
                        onClick = { showChordHelpDialog = true }
                    ) {
                        Text(
                            text = stringResource(R.string.chords_editor_help_action),
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp
                        )
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

                headerEndContent()
            }

            Spacer(Modifier.height(8.dp))
        }

        when (currentEditTab) {
            0, 1 -> {
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

                    if (hasTimedLines && !tabletLineEditFocusActive) {
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
                                if (rawContainsLrcTimestamps(value.text)) {
                                    val parsed = parseLrc(value.text).filter { it.text.isNotBlank() }
                                    if (parsed.isNotEmpty()) {
                                        applyEditingLinesWithUndo(parsed)
                                    } else {
                                        rawTextFieldValue = value
                                        onRawLyricsTextChange(value.text)
                                    }
                                } else {
                                    rawTextFieldValue = value
                                    onRawLyricsTextChange(value.text)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    rawTextFieldFocused = focusState.isFocused
                                },
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

            2 -> {
                // Onglet SYNCHRO
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
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
                        TextButton(
                            onClick = {
                                val previous = previousEditingLines ?: return@TextButton
                                onEditingLinesChange(previous)
                                applyLinesToRawDraft(previous)
                                selectedSyncLineIndices = emptySet()
                                previousEditingLines = null
                            },
                            enabled = previousEditingLines != null
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_editor_undo),
                                color = if (previousEditingLines != null) Color(0xFF80CBC4) else Color.DarkGray,
                                fontSize = 11.sp
                            )
                        }
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
                                    val cleared = clearAllChordsKeepingPaletteAndLyrics(
                                        palette = displayedPalette,
                                        lyrics = editingLines
                                    ).clearedChordLines
                                    applyEditingLinesWithUndo(cleared, updateRawDraft = false)
                                    selectedSyncLineIndices = emptySet()
                                    scope.launch {
                                        yield()
                                        if (onPersistLines(cleared)) {
                                            onPersistSucceeded(cleared)
                                        }
                                    }
                                } else {
                                    applyEditingLinesWithUndo(
                                        editingLines.map { it.copy(timeMs = 0L) },
                                        updateRawDraft = false
                                    )
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
                                                        selectedSyncLineIndices = if (index in selectedSyncLineIndices) {
                                                            selectedSyncLineIndices - index
                                                        } else {
                                                            selectedSyncLineIndices + index
                                                        }
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
                                                else -> line.colorArgb?.let(::Color) ?: Color.White
                                            },
                                            fontSize = 16.sp,
                                            modifier = Modifier.pointerInput(index, line.text) {
                                                detectTapGestures(
                                                    onTap = {
                                                        lineMenuIndex = index
                                                        lineMenuText = line.text
                                                        lineMenuColorArgb = line.colorArgb
                                                    },
                                                    onLongPress = {
                                                        selectedSyncLineIndices = if (index in selectedSyncLineIndices) {
                                                            selectedSyncLineIndices - index
                                                        } else {
                                                            selectedSyncLineIndices + index
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
                        val colorOptions = listOf(
                            null to stringResource(R.string.lyrics_editor_color_none),
                            0xFFFFFF00.toInt() to stringResource(R.string.lyrics_editor_color_yellow),
                            0xFFFF5252.toInt() to stringResource(R.string.lyrics_editor_color_red),
                            0xFF64B5F6.toInt() to stringResource(R.string.lyrics_editor_color_blue),
                            0xFF66BB6A.toInt() to stringResource(R.string.lyrics_editor_color_green),
                            0xFFBA68C8.toInt() to stringResource(R.string.lyrics_editor_color_violet)
                        )

                        Dialog(
                            onDismissRequest = {
                                lineMenuIndex = null
                                lineMenuColorArgb = null
                            },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .wrapContentHeight(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                tonalElevation = 8.dp,
                                color = Color(0xFF161616)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp)
                                ) {
                                    Text(
                                        text = if (showChordPalette) {
                                            "Modifier l’accord"
                                        } else {
                                            stringResource(R.string.lyrics_editor_line_dialog_title, idx + 1)
                                        },
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    OutlinedTextField(
                                        value = lineMenuText,
                                        onValueChange = { lineMenuText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 116.dp),
                                        label = {
                                            Text(
                                                if (showChordPalette) {
                                                    "Saisir un accord (ex: Am, F#maj7, Am/G)"
                                                } else {
                                                    stringResource(R.string.lyrics_editor_line_text_label)
                                                }
                                            )
                                        },
                                        singleLine = false,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 18.sp
                                        )
                                    )
                                    if (!showChordPalette) {
                                        Spacer(Modifier.height(18.dp))
                                        Text(
                                            text = stringResource(R.string.lyrics_editor_color_section),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            colorOptions.forEach { (colorArgb, label) ->
                                                val isSelected = lineMenuColorArgb == colorArgb
                                                TextButton(
                                                    onClick = { lineMenuColorArgb = colorArgb },
                                                    modifier = Modifier.size(42.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(
                                                                color = colorArgb?.let(::Color) ?: Color.Transparent,
                                                                shape = androidx.compose.foundation.shape.CircleShape
                                                            )
                                                            .border(
                                                                width = if (isSelected) 3.dp else 1.dp,
                                                                color = if (isSelected) Color(0xFF80CBC4) else Color(0xFF607D8B),
                                                                shape = androidx.compose.foundation.shape.CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Check,
                                                                contentDescription = label,
                                                                tint = if (colorArgb == null) Color(0xFF80CBC4) else Color.Black,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val list = editingLines.toMutableList()
                                                if (idx in list.indices) {
                                                    list.removeAt(idx)
                                                    applyEditingLinesWithUndo(list)
                                                    selectedSyncLineIndices = selectedSyncLineIndices.filter { it in list.indices }.toSet()
                                                } else if (BuildConfig.DEBUG) {
                                                    Log.w("LrcDebug", "DELETE_LINE_SKIPPED invalidIndex idx=$idx size=${list.size}")
                                                }

                                                lineMenuIndex = null
                                                lineMenuColorArgb = null
                                            }
                                        ) {
                                            Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF8A80))
                                        }
                                        Row {
                                            TextButton(onClick = {
                                                lineMenuIndex = null
                                                lineMenuColorArgb = null
                                            }) {
                                                Text(stringResource(R.string.lyrics_editor_cancel), color = Color.LightGray)
                                            }
                                            TextButton(
                                                onClick = {
                                                    val list = editingLines.toMutableList()
                                                    if (idx in list.indices) {
                                                        Log.d(
                                                            LYRICS_PLAYER_SYNC_DIAG_TAG,
                                                            "EDIT_TEXT lineIndex=$idx oldText=${list[idx].text} newText=${lineMenuText.trim()}"
                                                        )
                                                        Log.d(
                                                            LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                                                            "EDIT_TEXT_CHANGED lineIndex=$idx timestamp=${list[idx].timeMs} oldText=${list[idx].text} newText=${lineMenuText.trim()}"
                                                        )
                                                        Log.d(
                                                            LYRICS_PERSIST_DIAG_TAG,
                                                            "EDIT_TEXT songId=${currentSongId ?: currentTrackUri.orEmpty()} lineIndex=$idx oldText=${list[idx].text} newText=${lineMenuText.trim()}"
                                                        )
                                                        Log.d(
                                                            LYRICS_PIPELINE_TRACE_TAG,
                                                            "EDIT_TEXT songId=${currentSongId ?: currentTrackUri.orEmpty()} lineIndex=$idx old=${list[idx].text} new=${lineMenuText.trim()}"
                                                        )
                                                        Log.d(
                                                            LYRICS_PLAYER_SYNC_DIAG_TAG,
                                                            "EDIT_COLOR lineIndex=$idx oldColor=${list[idx].colorArgb} newColor=$lineMenuColorArgb"
                                                        )
                                                        Log.d(
                                                            LYRICS_PERSIST_DIAG_TAG,
                                                            "EDIT_COLOR songId=${currentSongId ?: currentTrackUri.orEmpty()} lineIndex=$idx oldColor=${list[idx].colorArgb} newColor=$lineMenuColorArgb"
                                                        )
                                                        Log.d(
                                                            LYRICS_PIPELINE_TRACE_TAG,
                                                            "EDIT_COLOR songId=${currentSongId ?: currentTrackUri.orEmpty()} lineIndex=$idx old=${list[idx].colorArgb} new=$lineMenuColorArgb"
                                                        )
                                                        list[idx] = list[idx].copy(
                                                            text = lineMenuText.trim(),
                                                            colorArgb = if (showChordPalette) list[idx].colorArgb else lineMenuColorArgb
                                                        )
                                                        applyEditingLinesWithUndo(list)
                                                    } else if (BuildConfig.DEBUG) {
                                                        Log.w("LrcDebug", "EDIT_LINE_SKIPPED invalidIndex idx=$idx size=${list.size}")
                                                    }
                                                    lineMenuIndex = null
                                                    lineMenuColorArgb = null
                                                }
                                            ) {
                                                Text(stringResource(R.string.lyrics_editor_edit), color = Color(0xFF80CBC4))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // <-- ferme Column onglet Synchro
            }     // <-- ferme "1 -> {"
        }         // <-- ferme when

        Spacer(Modifier.height(8.dp))
        playbackControlContent()
    }             // <-- ferme Column principale
}                 // <-- ferme composable
// ─────────────────────────────
//  FONCTIONS UTILITAIRES
// ─────────────────────────────

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
