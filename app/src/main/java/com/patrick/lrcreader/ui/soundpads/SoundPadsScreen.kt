package com.patrick.lrcreader.ui.soundpads

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.patrick.lrcreader.core.PadsBusController
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeEngine
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.MusicControlAccent
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Cockpit destination; the host-owned Pads player survives navigation to the sound bus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPadsScreen(onClose: () -> Unit, modifier: Modifier = Modifier, tabletMode: Boolean? = null,
                    sharedEngine: SoundPadsPrototypeEngine? = null) {
    val context = LocalContext.current
    val tablet = tabletMode ?: rememberSmpAdaptiveTokens().tabletMode
    val scope = rememberCoroutineScope()
    val store = remember { SoundPadsStore(context) }
    val engine = sharedEngine ?: remember { SoundPadsPrototypeEngine(context) }
    val audio by engine.state.collectAsState()
    var pads by remember { mutableStateOf<List<SoundPad>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var operations by remember { mutableIntStateOf(1) }
    val busy = operations > 0
    var error by remember { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    val canEdit = loaded && !busy
    val allowDismissSheet by rememberUpdatedState(!busy)
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(engine, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) engine.stopAll() }
        if (sharedEngine == null) lifecycle.lifecycle.addObserver(observer)
        onDispose {
            if (sharedEngine == null) { lifecycle.lifecycle.removeObserver(observer); engine.release() }
        }
    }
    LaunchedEffect(Unit) {
        try { PadsBusController.initialize(context); pads = store.load(); loaded = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { operations-- }
    }
    fun mutate(action: suspend () -> List<SoundPad>, after: (List<SoundPad>) -> Unit = {}) {
        engine.stopAll()
        operations++
        error = false
        scope.launch {
            try { val updated = action(); pads = updated; after(updated) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { operations-- }
        }
    }
    fun add(slot: Int = pads.size) {
        val names = (pads.size..slot).map { context.getString(R.string.soundpads_prototype_pad, it + 1) }
        mutate({ store.add(names) }) { editingId = it.last().padId }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pickingId
        pickingId = null
        if (uri != null && id != null) mutate({ store.import(id, uri) })
    }
    BackHandler {
        if (!busy) {
            when {
                deletingId != null -> deletingId = null
                editingId != null -> editingId = null
                else -> onClose()
            }
        }
    }
    val editorPad = pads.firstOrNull { it.padId == editingId }
    val colors = darkColorScheme(primary = MusicControlAccent, onPrimary = Color(0xFF171717),
        surface = Color(0xFF1A1C1E), onSurface = Color(0xFFF2F1ED),
        background = Color(0xFF101112), onBackground = Color(0xFFF2F1ED))
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            BoxWithConstraints(modifier.fillMaxSize().testTag("soundpads-screen")
                .background(Brush.verticalGradient(listOf(Color(0xFF171717), Color(0xFF101010), Color(0xFF101112))))
                ) {
                // A sibling behind the content catches only blank-area touches. Consuming on an
                // ancestor's Final pass cancels combinedClickable as soon as a finger moves.
                Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                })
                val landscape = maxWidth > maxHeight
                val screenHeight = maxHeight
                val screenWidth = maxWidth
                val sideBySide = tablet && maxWidth >= 900.dp
                val columns = if (tablet) if (landscape) 4 else 3 else 2
                val rows = if (tablet && !landscape) 4 else 3
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = if (tablet) 20.dp else 12.dp)) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = !busy, onClick = onClose, modifier = Modifier.testTag("soundpads-back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.soundpads_back))
                        }
                        Text(stringResource(if (tablet) R.string.soundpads_title else R.string.soundpads_title_short),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = Color(0xFFF2F1ED))
                        OutlinedButton(onClick = { engine.stopAll() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(44.dp).testTag("soundpads-stop")) {
                            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.soundpads_stop_short))
                        }
                        OutlinedIconButton(enabled = canEdit, onClick = { add() }, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(start = 8.dp).size(44.dp).testTag("soundpads-add")) {
                            Icon(Icons.Filled.Add, stringResource(R.string.soundpads_add), tint = MusicControlAccent)
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    if (error || audio.phase == SoundPadsPrototypeEngine.Phase.ERROR) {
                        Text(stringResource(if (error) R.string.soundpads_operation_error else R.string.soundpads_prototype_error),
                            style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Row(Modifier.weight(1f).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SoundPadsGrid(pads, columns, rows, audio, editingId, canEdit,
                            Modifier.weight(1f).fillMaxHeight(),
                            onTrigger = { pad, slot ->
                                if (pad == null) add(slot)
                                else if (pad.audioPath.isEmpty()) { error = false; editingId = pad.padId }
                                else {
                                    SoundPadsTouchTrace.event("AUDIO_CALLBACK", pad.padId, slot)
                                    engine.trigger(pad)
                                }
                            },
                            onEdit = { pad, slot ->
                                error = false
                                if (pad == null) add(slot) else editingId = pad.padId
                            })
                        if (sideBySide && editorPad != null) {
                            SoundPadSettings(editorPad, busy, error, Modifier.width(320.dp).fillMaxHeight(),
                                onClose = { editingId = null }, onPick = { pickingId = editorPad.padId; picker.launch(arrayOf("audio/*")) },
                                onDelete = { deletingId = editorPad.padId },
                                onSave = { name, start, end, volume, color ->
                                    mutate({ store.update(editorPad.padId, name, start, end, volume, color) }) { editingId = null }
                                }, onTest = engine::trigger, onStop = engine::stopAll)
                        }
                    }
                }
                if (tablet && !sideBySide && editorPad != null) {
                    // Portrait tablet keeps a full-size grid under a dismissible side panel.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                    SoundPadSettings(editorPad, busy, error,
                        Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp).width(340.dp).fillMaxHeight(),
                        onClose = { editingId = null }, onPick = { pickingId = editorPad.padId; picker.launch(arrayOf("audio/*")) },
                        onDelete = { deletingId = editorPad.padId },
                        onSave = { name, start, end, volume, color ->
                            mutate({ store.update(editorPad.padId, name, start, end, volume, color) }) { editingId = null }
                        }, onTest = engine::trigger, onStop = engine::stopAll)
                }
                if (!tablet && editorPad != null) {
                    ModalBottomSheet(onDismissRequest = { if (!busy) editingId = null }, sheetMaxWidth = screenWidth,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
                            confirmValueChange = { it != SheetValue.Hidden || allowDismissSheet }),
                        containerColor = colors.surface, contentColor = colors.onSurface) {
                        SoundPadSettings(editorPad, busy, error,
                            Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.85f).imePadding(),
                            onClose = { editingId = null }, onPick = { pickingId = editorPad.padId; picker.launch(arrayOf("audio/*")) },
                            onDelete = { deletingId = editorPad.padId },
                            onSave = { name, start, end, volume, color ->
                                mutate({ store.update(editorPad.padId, name, start, end, volume, color) }) { editingId = null }
                            }, onTest = engine::trigger, onStop = engine::stopAll)
                    }
                }
                pads.firstOrNull { it.padId == deletingId }?.let { pad ->
                    AlertDialog(onDismissRequest = { if (!busy) deletingId = null },
                        title = { Text(stringResource(R.string.soundpads_delete)) },
                        text = { Text(stringResource(R.string.soundpads_delete_confirm, pad.name)) },
                        confirmButton = { TextButton(enabled = canEdit, onClick = {
                            mutate({ store.delete(pad.padId) }) { deletingId = null; editingId = null }
                        }, modifier = Modifier.testTag("soundpads-confirm-delete")) { Text(stringResource(R.string.soundpads_delete)) } },
                        dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text(stringResource(R.string.soundpads_cancel)) } })
                }
            }
        }
    }
}

@Composable
private fun SoundPadsGrid(
    pads: List<SoundPad>, columns: Int, rows: Int, audio: SoundPadsPrototypeEngine.State,
    editingId: String?, enabled: Boolean, modifier: Modifier,
    onTrigger: (SoundPad?, Int) -> Unit, onEdit: (SoundPad?, Int) -> Unit
) {
    val scroll = rememberLazyGridState()
    LaunchedEffect(editingId, pads.size) {
        val index = pads.indexOfFirst { it.padId == editingId }
        if (index >= 0) scroll.animateScrollToItem(index)
    }
    BoxWithConstraints(modifier) {
        val gap = 12.dp
        val width = (maxWidth - gap * (columns - 1)) / columns
        val height = minOf((maxHeight - gap * (rows - 1)) / rows, width * 1.25f).coerceAtLeast(112.dp)
        LazyVerticalGrid(columns = GridCells.Fixed(columns), state = scroll,
            modifier = Modifier.fillMaxSize().testTag("soundpads-grid"),
            verticalArrangement = Arrangement.spacedBy(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            items(count = maxOf(columns * rows, pads.size), key = { pads.getOrNull(it)?.padId ?: "slot-$it" }) { index ->
                val pad = pads.getOrNull(index)
                val active = pad != null && audio.padId == pad.padId &&
                    (audio.phase == SoundPadsPrototypeEngine.Phase.PLAYING || audio.phase == SoundPadsPrototypeEngine.Phase.PREPARING)
                SoundPadTile(pad, index, active, pad != null && editingId == pad.padId, enabled,
                    Modifier.fillMaxWidth().height(height), onTrigger = { onTrigger(pad, index) }, onEdit = { onEdit(pad, index) })
            }
        }
    }
}
