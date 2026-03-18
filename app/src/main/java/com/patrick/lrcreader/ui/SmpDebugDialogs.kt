package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpImportedSongDetail
import com.patrick.lrcreader.smp.SongUnit

@Composable
fun SmpImportedSongsDialog(
    songs: List<SongUnit>,
    onDismiss: () -> Unit,
    onSongSelected: (SongUnit) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "SMP importés",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Count: ${songs.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (songs.isEmpty()) {
                    Text(
                        text = "Aucun morceau SMP importé.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    songs.forEach { song ->
                        TextButton(
                            onClick = { onSongSelected(song) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = song.id,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
fun SmpImportedSongDetailDialog(
    detail: SmpImportedSongDetail,
    onDismiss: () -> Unit
) {
    val song = detail.song

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Détail SMP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                SelectionContainer {
                    Column {
                        SmpDebugField(label = "title", value = song.title)
                        SmpDebugField(label = "id", value = song.id)
                        SmpDebugField(label = "storageFolder", value = song.storageFolder)
                        SmpDebugField(label = "audioPath", value = song.audioPath)
                        SmpDebugField(label = "lyricsPath", value = song.lyricsPath)
                        SmpDebugField(label = "chordsPath", value = song.chordsPath)
                        SmpDebugField(label = "annotationsPath", value = song.annotationsPath)
                        SmpDebugField(label = "midiPath", value = song.midiPath)
                        SmpDebugField(label = "dmxPath", value = song.dmxPath)
                        SmpDebugField(label = "prompterPath", value = song.prompterPath)
                        SmpDebugField(label = "playback", value = formatPlayback(detail.playback))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmpDebugField(label: String, value: String?) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value ?: "absent",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatPlayback(playback: SmpConfig.PlaybackConfig?): String {
    playback ?: return "absent"

    return buildString {
        append("{")
        append("trimStartMs=")
        append(playback.trimStartMs ?: "null")
        append(", trimEndMs=")
        append(playback.trimEndMs ?: "null")
        append("}")
    }
}
