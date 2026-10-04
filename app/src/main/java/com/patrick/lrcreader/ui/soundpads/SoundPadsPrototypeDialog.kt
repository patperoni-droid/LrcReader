package com.patrick.lrcreader.ui.soundpads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeEngine
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import androidx.compose.runtime.saveable.rememberSaveable
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Diagnostic overlay only. The complete 6/12-pad screen and Bus tranche are intentionally deferred. */
@Composable
fun SoundPadsPrototypeDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SoundPadsStore(context) }
    val engine = remember { SoundPadsPrototypeEngine(context) }
    val state by engine.state.collectAsState()
    val source by PlaybackCoordinator.activeSource.collectAsState()
    var pads by remember { mutableStateOf<List<SoundPad>>(emptyList()) }
    var importing by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SoundPad?>(null) }
    var deleting by remember { mutableStateOf<SoundPad?>(null) }
    var importError by remember { mutableStateOf(false) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var globalUi by remember { mutableFloatStateOf(0.5f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(engine, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.stopAll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.release()
        }
    }
    LaunchedEffect(Unit) {
        try { pads = store.load(); loaded = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { importError = true }
        finally { importing = false }
    }
    fun mutate(action: suspend () -> List<SoundPad>, after: () -> Unit = {}) {
        engine.stopAll()
        importing = true
        importError = false
        scope.launch {
            try { pads = action(); after() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { importError = true }
            finally { importing = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = targetId
        targetId = null
        if (uri != null && id != null && loaded) mutate({ store.import(id, uri) })
    }
    val canEdit = loaded && !importing
    AlertDialog(
        onDismissRequest = { if (!importing) onClose() },
        title = { Text(stringResource(R.string.soundpads_prototype_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.soundpads_prototype_description))
                Text(stringResource(R.string.soundpads_prototype_source, source.name))
                val phase = when (state.phase) {
                    SoundPadsPrototypeEngine.Phase.IDLE -> R.string.soundpads_prototype_idle
                    SoundPadsPrototypeEngine.Phase.PREPARING -> R.string.soundpads_prototype_preparing
                    SoundPadsPrototypeEngine.Phase.PLAYING -> R.string.soundpads_prototype_playing
                    SoundPadsPrototypeEngine.Phase.ERROR -> R.string.soundpads_prototype_error
                }
                Text(stringResource(phase))
                Button(enabled = canEdit, onClick = {
                    val name = context.getString(R.string.soundpads_prototype_pad, pads.size + 1)
                    mutate({ store.add(name) })
                }) { Text(stringResource(R.string.soundpads_add)) }
                if (pads.isEmpty() && loaded) Text(stringResource(R.string.soundpads_empty))
                pads.forEach { pad ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = canEdit && pad.audioPath.isNotEmpty(), onClick = { engine.trigger(pad) }) {
                            Text(pad.name)
                        }
                        TextButton(enabled = canEdit, onClick = {
                            targetId = pad.padId
                            picker.launch(arrayOf("audio/*"))
                        }) { Text(stringResource(R.string.soundpads_prototype_choose)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = canEdit, onClick = { editing = pad }) {
                            Text(stringResource(R.string.soundpads_edit))
                        }
                        TextButton(enabled = canEdit, onClick = { deleting = pad }) {
                            Text(stringResource(R.string.soundpads_delete))
                        }
                    }
                    if (pad.outMs != null) {
                        Text(stringResource(R.string.soundpads_prototype_clip, pad.inMs, pad.outMs))
                    } else Text(stringResource(R.string.soundpads_to_end, pad.inMs))
                }
                Text(stringResource(R.string.soundpads_prototype_level))
                Slider(value = globalUi, onValueChange = {
                    globalUi = it
                    engine.setGlobalUiLevel(it)
                })
                if (importing) Text(stringResource(R.string.soundpads_prototype_importing))
                if (importError) Text(stringResource(R.string.soundpads_operation_error))
                state.samples.lastOrNull()?.let {
                    Text(stringResource(R.string.soundpads_prototype_measure, it.readyMs ?: -1, it.sinkCallbackMs))
                }
                Text(stringResource(R.string.soundpads_prototype_samples, state.samples.size, state.underruns))
                Text(stringResource(R.string.soundpads_prototype_measure_warning))
            }
        },
        confirmButton = { Button(onClick = { engine.stopAll() }) { Text(stringResource(R.string.soundpads_prototype_stop)) } },
        dismissButton = { TextButton(enabled = !importing, onClick = onClose) { Text(stringResource(R.string.soundpads_prototype_close)) } }
    )
    editing?.let { pad ->
        SoundPadEditor(pad, enabled = canEdit, onClose = { editing = null }, onSave = { name, start, end, volume ->
            mutate({ store.update(pad.padId, name, start, end, volume) }, { editing = null })
        }, error = importError)
    }
    deleting?.let { pad ->
        AlertDialog(
            onDismissRequest = { if (!importing) deleting = null },
            title = { Text(stringResource(R.string.soundpads_delete)) },
            text = { Text(stringResource(R.string.soundpads_delete_confirm, pad.name)) },
            confirmButton = {
                TextButton(enabled = canEdit, onClick = {
                    mutate({ store.delete(pad.padId) }, { deleting = null })
                }) { Text(stringResource(R.string.soundpads_delete)) }
            },
            dismissButton = { TextButton(enabled = !importing, onClick = { deleting = null }) {
                Text(stringResource(R.string.soundpads_cancel))
            } }
        )
    }
}

@Composable
private fun SoundPadEditor(
    pad: SoundPad, enabled: Boolean, error: Boolean, onClose: () -> Unit,
    onSave: (String, Long, Long?, Float) -> Unit
) {
    var name by remember(pad.padId) { mutableStateOf(pad.name) }
    var start by remember(pad.padId) { mutableStateOf(pad.inMs.toString()) }
    var end by remember(pad.padId) { mutableStateOf(pad.outMs?.toString().orEmpty()) }
    var volume by remember(pad.padId) { mutableFloatStateOf(pad.volume) }
    val startMs = start.toLongOrNull()
    val endMs = end.toLongOrNull()
    val valid = name.isNotBlank() && startMs != null && startMs >= 0 &&
        (end.isBlank() || endMs != null && endMs > startMs) &&
        (pad.audioPath.isNotEmpty() || startMs == 0L && end.isBlank())
    AlertDialog(
        onDismissRequest = { if (enabled) onClose() },
        title = { Text(stringResource(R.string.soundpads_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, enabled = enabled,
                    label = { Text(stringResource(R.string.soundpads_name)) })
                OutlinedTextField(value = start, onValueChange = { start = it }, enabled = enabled,
                    label = { Text(stringResource(R.string.soundpads_in)) })
                OutlinedTextField(value = end, onValueChange = { end = it }, enabled = enabled,
                    label = { Text(stringResource(R.string.soundpads_out)) })
                Text(stringResource(R.string.soundpads_volume))
                Slider(value = volume, onValueChange = { volume = it }, enabled = enabled)
                if (error) Text(stringResource(R.string.soundpads_operation_error))
            }
        },
        confirmButton = { TextButton(enabled = enabled && valid, onClick = {
            onSave(name, requireNotNull(startMs), endMs, volume)
        }) { Text(stringResource(R.string.soundpads_save)) } },
        dismissButton = { TextButton(enabled = enabled, onClick = onClose) {
            Text(stringResource(R.string.soundpads_cancel))
        } }
    )
}
