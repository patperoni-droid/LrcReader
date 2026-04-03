package com.patrick.lrcreader.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.MidiCue
import com.patrick.lrcreader.smp.SmpMidiCueBridge

@Composable
fun TimelineMidiCueEditorPopup(
    trackUri: String,
    markerTimeMs: Long,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val safeMarkerTimeMs = markerTimeMs.coerceAtLeast(0L)
    val existing = remember(context, trackUri, safeMarkerTimeMs) {
        SmpMidiCueBridge.getCueAtTime(context, trackUri, safeMarkerTimeMs)
    }

    CueMidiEditorDialog(
        title = stringResource(
            R.string.cue_midi_timeline_dialog_title,
            formatCueMidiTimelineTime(safeMarkerTimeMs)
        ),
        initialChannel = existing?.channel ?: 1,
        initialProgram = existing?.value ?: 1,
        onConfirm = { ch, prg ->
            SmpMidiCueBridge.upsertCueAtTime(
                context = context,
                trackUriString = trackUri,
                cue = MidiCue(
                    time = safeMarkerTimeMs / 1000.0,
                    type = "PC",
                    value = prg,
                    channel = ch
                )
            )
            onClose()
        },
        onDelete = {
            SmpMidiCueBridge.deleteCueAtTime(
                context = context,
                trackUriString = trackUri,
                timeMs = safeMarkerTimeMs
            )
            onClose()
        },
        onClose = onClose
    )
}

@Composable
private fun CueMidiEditorDialog(
    title: String,
    initialChannel: Int,
    initialProgram: Int,
    onConfirm: (channel: Int, program: Int) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var channelText by remember(initialChannel) { mutableStateOf(initialChannel.toString()) }
    var programText by remember(initialProgram) { mutableStateOf(initialProgram.toString()) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, color = Color.White) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = channelText,
                    onValueChange = { channelText = it },
                    label = { Text(stringResource(R.string.cue_midi_channel_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = programText,
                    onValueChange = { programText = it },
                    label = { Text(stringResource(R.string.cue_midi_program_change_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ch = channelText.toIntOrNull()?.coerceIn(1, 16) ?: 1
                val prg = programText.toIntOrNull()?.coerceIn(1, 128) ?: 1
                onConfirm(ch, prg)
            }) {
                Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF8A80))
            }
        },
        containerColor = Color(0xFF222222)
    )
}

private fun formatCueMidiTimelineTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
