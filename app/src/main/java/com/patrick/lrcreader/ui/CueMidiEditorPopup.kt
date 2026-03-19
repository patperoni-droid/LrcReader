package com.patrick.lrcreader.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.patrick.lrcreader.core.CueMidi
import com.patrick.lrcreader.core.CueMidiStore
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.smp.SmpMidiCueBridge

@Composable
fun CueMidiEditorPopup(
    trackUri: String,
    lines: List<LrcLine>,
    lineIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val existing = remember(context, trackUri, lines, lineIndex) {
        SmpMidiCueBridge.getEditorCues(context, trackUri, lines)
            ?.firstOrNull { it.lineIndex == lineIndex }
            ?: CueMidiStore.getCuesForTrack(trackUri).firstOrNull { it.lineIndex == lineIndex }
    }

    var channelText by remember(existing) { mutableStateOf((existing?.channel ?: 1).toString()) }
    var programText by remember(existing) { mutableStateOf((existing?.program ?: 1).toString()) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.cue_midi_dialog_title, lineIndex + 1), color = Color.White) },
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

                val cue = CueMidi(lineIndex = lineIndex, channel = ch, program = prg)
                when (SmpMidiCueBridge.upsertCue(context, trackUri, lines, cue)) {
                    null -> CueMidiStore.upsertCue(trackUri, cue)
                    else -> Unit
                }
                onClose()
            }) {
                Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                when (SmpMidiCueBridge.deleteCue(context, trackUri, lines, lineIndex)) {
                    null -> CueMidiStore.deleteCue(trackUri, lineIndex)
                    else -> Unit
                }
                onClose()
            }) {
                Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF8A80))
            }
        },
        containerColor = Color(0xFF222222)
    )
}
