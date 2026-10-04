package com.patrick.lrcreader.ui.soundpads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeFiles
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.ThinMusicSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Local draft; saving reuses the existing atomic store transaction, never edits source audio. */
@Composable
internal fun SoundPadSettings(
    pad: SoundPad, busy: Boolean, error: Boolean, modifier: Modifier = Modifier,
    onClose: () -> Unit, onPick: () -> Unit, onDelete: () -> Unit,
    onSave: (String, Long, Long?, Float, Long) -> Unit, onTest: (SoundPad) -> Unit, onStop: () -> Unit
) {
    var name by rememberSaveable(pad.padId) { mutableStateOf(pad.name) }
    var start by rememberSaveable(pad.padId) { mutableStateOf(pad.inMs.toString()) }
    var end by rememberSaveable(pad.padId) { mutableStateOf(pad.outMs?.toString().orEmpty()) }
    var volume by rememberSaveable(pad.padId) { mutableFloatStateOf(pad.volume) }
    var colorArgb by rememberSaveable(pad.padId) { mutableLongStateOf(soundPadColor(pad)) }
    var duration by remember(pad.audioPath) { mutableStateOf<Long?>(null) }
    var previousAudio by rememberSaveable(pad.padId) { mutableStateOf(pad.audioPath) }
    LaunchedEffect(pad.audioPath) {
        if (previousAudio != pad.audioPath) {
            start = pad.inMs.toString()
            end = pad.outMs?.toString().orEmpty()
            previousAudio = pad.audioPath
        }
        if (pad.audioPath.isNotEmpty()) duration = withContext(Dispatchers.IO) {
            runCatching { SoundPadsPrototypeFiles.audioDurationMs(File(pad.audioPath)) }.getOrNull()
        }
    }
    val inMs = start.toLongOrNull()
    val outMs = end.toLongOrNull()
    val hasAudio = pad.audioPath.isNotEmpty()
    val valid = name.isNotBlank() && inMs != null && inMs >= 0 &&
        (end.isBlank() || outMs != null && outMs > inMs) &&
        (if (hasAudio) duration?.let { inMs < it && (outMs == null || outMs <= it) } == true
            else inMs == 0L && end.isBlank())
    val accent = Color(colorArgb)
    val labels = listOf(R.string.soundpads_red, R.string.soundpads_amber, R.string.soundpads_blue,
        R.string.soundpads_purple, R.string.soundpads_green, R.string.soundpads_pink,
        R.string.soundpads_cyan, R.string.soundpads_orange)
    Column(modifier.background(Color(0xFF1A1C1E), RoundedCornerShape(20.dp))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
        .testTag("soundpads-settings").padding(16.dp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.soundpads_settings), modifier = Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium)
            IconButton(enabled = !busy, onClick = onClose) {
                Icon(Icons.Filled.Close, stringResource(R.string.soundpads_prototype_close))
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("soundpads-name"), singleLine = true,
            label = { Text(stringResource(R.string.soundpads_name)) })
        OutlinedButton(enabled = !busy, onClick = onPick, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("soundpads-choose")) {
            Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (hasAudio) R.string.soundpads_replace else R.string.soundpads_prototype_choose))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.soundpads_volume), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.common_percent_value, (volume * 100).toInt()), color = accent, style = MaterialTheme.typography.labelLarge)
        }
        ThinMusicSlider(value = volume, onValueChange = { volume = it }, enabled = !busy, accent = accent,
            modifier = Modifier.fillMaxWidth().testTag("soundpads-volume"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = start, onValueChange = { start = it }, enabled = !busy,
                modifier = Modifier.weight(1f).testTag("soundpads-in"), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.soundpads_in)) })
            OutlinedTextField(value = end, onValueChange = { end = it }, enabled = !busy,
                modifier = Modifier.weight(1f).testTag("soundpads-out"), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.soundpads_out_short)) })
        }
        Text(stringResource(R.string.soundpads_out_hint), style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.48f))
        Text(stringResource(R.string.soundpads_color), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SoundPadsPalette.forEachIndexed { index, value ->
                val description = stringResource(labels[index])
                Box(Modifier.size(32.dp).border(if (value == colorArgb) 2.dp else 1.dp,
                    if (value == colorArgb) Color.White else Color.Transparent, CircleShape)
                    .padding(4.dp).background(Color(value), CircleShape)
                    .clickable(enabled = !busy, role = Role.RadioButton) { colorArgb = value }
                    .testTag("soundpads-color-$index").semantics { contentDescription = description; selected = value == colorArgb })
            }
        }
        if (error) Text(stringResource(R.string.soundpads_operation_error), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && valid && hasAudio, shape = RoundedCornerShape(12.dp), onClick = {
                onTest(pad.copy(name = name.trim(), inMs = requireNotNull(inMs), outMs = outMs, volume = volume, colorArgb = colorArgb))
            }, modifier = Modifier.weight(1f).testTag("soundpads-test")) {
                Icon(Icons.Filled.PlayArrow, null)
                Text(stringResource(R.string.soundpads_test))
            }
            OutlinedButton(onClick = onStop, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("soundpads-settings-stop")) {
                Icon(Icons.Filled.Stop, stringResource(R.string.soundpads_stop_short), modifier = Modifier.size(18.dp))
            }
        }
        Button(enabled = !busy && valid, shape = RoundedCornerShape(12.dp), onClick = {
            onSave(name, requireNotNull(inMs), outMs, volume, colorArgb)
        }, modifier = Modifier.fillMaxWidth().testTag("soundpads-save")) { Text(stringResource(R.string.soundpads_save)) }
        TextButton(enabled = !busy, onClick = onDelete, modifier = Modifier.fillMaxWidth().testTag("soundpads-delete")) {
            Text(stringResource(R.string.soundpads_delete), color = Color(0xFFED9384))
        }
    }
}
