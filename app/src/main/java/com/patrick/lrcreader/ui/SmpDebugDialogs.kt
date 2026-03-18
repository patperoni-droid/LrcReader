package com.patrick.lrcreader.ui

import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpImportedSongDetail
import com.patrick.lrcreader.smp.SongUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val SMP_PLAYER_TAG = "SMP_PLAYER"

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
    val canPlay = remember(song.audioPath) {
        song.audioPath?.let { File(it).isFile() } == true
    }
    var isPlayerDialogOpen by remember(detail.song.id) { mutableStateOf(false) }

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
                if (isPlayerDialogOpen) {
                    SmpPlayerDialog(
                        detail = detail,
                        onDismiss = { isPlayerDialogOpen = false }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { isPlayerDialogOpen = true },
                        enabled = canPlay
                    ) {
                        Text("Lire ce SMP")
                    }
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

@Composable
private fun SmpPlayerDialog(
    detail: SmpImportedSongDetail,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val exoPlayer = remember(appContext) {
        ExoPlayer.Builder(appContext).build().apply {
            playWhenReady = false
        }
    }
    val audioPath = detail.song.audioPath
    val trimStartMs = detail.playback?.trimStartMs ?: 0L
    val trimEndMs = detail.playback?.trimEndMs
    var status by remember(detail.song.id) { mutableStateOf("Prêt") }
    var trimWatcherJob by remember { mutableStateOf<Job?>(null) }

    fun cancelTrimWatcher() {
        trimWatcherJob?.cancel()
        trimWatcherJob = null
    }

    fun stopPlayback() {
        cancelTrimWatcher()
        runCatching {
            exoPlayer.stop()
            exoPlayer.seekTo(trimStartMs)
        }.onFailure { error ->
            Log.e(
                SMP_PLAYER_TAG,
                "Stop SMP isolé impossible: songId=${detail.song.id} audioPath=$audioPath",
                error
            )
        }
        status = "Arrêté"
    }

    fun startTrimWatcher() {
        cancelTrimWatcher()
        if (trimEndMs == null || trimEndMs <= 0L) {
            return
        }

        trimWatcherJob = scope.launch {
            while (true) {
                val isPlaying = runCatching { exoPlayer.isPlaying }.getOrDefault(false)
                if (!isPlaying) {
                    delay(50L)
                    continue
                }

                val positionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                if (positionMs >= trimEndMs) {
                    stopPlayback()
                    return@launch
                }

                delay(50L)
            }
        }
    }

    fun play() {
        if (audioPath.isNullOrBlank()) {
            status = "Audio absent"
            return
        }

        val audioFile = File(audioPath)
        if (!audioFile.isFile) {
            status = "Audio introuvable"
            Log.w(
                SMP_PLAYER_TAG,
                "Lecture SMP impossible: fichier audio absent songId=${detail.song.id} audioPath=$audioPath"
            )
            return
        }

        val audioUri = Uri.fromFile(audioFile)
        cancelTrimWatcher()

        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
            exoPlayer.prepare()
            exoPlayer.seekTo(trimStartMs)
            exoPlayer.play()
        }.onSuccess {
            status = "Lecture"
            Log.i(
                SMP_PLAYER_TAG,
                "Lecture SMP démarrée: songId=${detail.song.id} title=${detail.song.title} trimStartMs=$trimStartMs trimEndMs=${trimEndMs ?: 0L}"
            )
            startTrimWatcher()
        }.onFailure { error ->
            status = "Erreur de lecture"
            Log.e(
                SMP_PLAYER_TAG,
                "Lecture SMP impossible: songId=${detail.song.id} audioPath=$audioPath",
                error
            )
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    cancelTrimWatcher()
                    status = "Terminé"
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            cancelTrimWatcher()
            runCatching { exoPlayer.stop() }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = {
            stopPlayback()
            onDismiss()
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Text(
                    text = detail.song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "trimStartMs=$trimStartMs trimEndMs=${trimEndMs ?: "absent"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { play() }) {
                        Text("PLAY")
                    }
                    Button(onClick = { stopPlayback() }) {
                        Text("STOP")
                    }
                    TextButton(
                        onClick = {
                            stopPlayback()
                            onDismiss()
                        }
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}
