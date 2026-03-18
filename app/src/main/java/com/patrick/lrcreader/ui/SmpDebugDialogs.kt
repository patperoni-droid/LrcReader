package com.patrick.lrcreader.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
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
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpImportedSongDetail
import com.patrick.lrcreader.smp.SmpImporter
import com.patrick.lrcreader.smp.SongUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SMP_PLAYER_TAG = "SMP_PLAYER"
private const val SMP_DEBUG_TAG = "SMP"
private const val SMP_TEST_TAG = "SMP_TEST"
private const val SMP_PLAYER_POLL_MS = 75L
private const val SMP_DEBUG_PLAYLIST_NAME = "SMP Debug"

private data class SmpRoundTripSnapshot(
    val trimStartMs: Long?,
    val trimEndMs: Long?,
    val audioPresent: Boolean,
    val lyricsPresent: Boolean,
    val chordsPresent: Boolean,
    val annotationsPresent: Boolean,
    val midiPresent: Boolean,
    val dmxPresent: Boolean,
    val prompterPresent: Boolean
)

private data class SmpRoundTripCheck(
    val label: String,
    val ok: Boolean,
    val detail: String? = null
)

private data class SmpRoundTripResult(
    val exportPath: String?,
    val importedSongId: String?,
    val checks: List<SmpRoundTripCheck>,
    val failureReason: String? = null
) {
    val isSuccessful: Boolean
        get() = failureReason == null && checks.all { it.ok }
}

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
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val song = detail.song
    val canPlay = remember(song.audioPath) {
        song.audioPath?.let { File(it).isFile() } == true
    }
    var isPlayerDialogOpen by remember(detail.song.id) { mutableStateOf(false) }
    var generatedConfigJson by remember(detail.song.id) { mutableStateOf<String?>(null) }
    var lastExportPath by remember(detail.song.id) { mutableStateOf<String?>(null) }
    var isExporting by remember(detail.song.id) { mutableStateOf(false) }
    var isRoundTripRunning by remember(detail.song.id) { mutableStateOf(false) }
    var roundTripResult by remember(detail.song.id) { mutableStateOf<SmpRoundTripResult?>(null) }

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
                        SmpDebugField(label = "lastExportPath", value = lastExportPath)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (isPlayerDialogOpen) {
                    SmpPlayerDialog(
                        detail = detail,
                        onDismiss = { isPlayerDialogOpen = false }
                    )
                }
                generatedConfigJson?.let { json ->
                    SmpGeneratedConfigDialog(
                        json = json,
                        onDismiss = { generatedConfigJson = null }
                    )
                }
                roundTripResult?.let { result ->
                    SmpRoundTripResultDialog(
                        result = result,
                        onDismiss = { roundTripResult = null }
                    )
                }
                Column {
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
                        Button(
                            onClick = {
                                scope.launch {
                                    val generatedJson = withContext(Dispatchers.IO) {
                                        SmpConfig.fromSongUnit(appContext, song).toJsonString()
                                    }
                                    Log.i(
                                        SMP_DEBUG_TAG,
                                        "Config SMP généré: songId=${song.id} title=${song.title}\n$generatedJson"
                                    )
                                    generatedConfigJson = generatedJson
                                }
                            }
                        ) {
                            Text("Config SMP")
                        }
                        Button(
                            onClick = {
                                isExporting = true
                                scope.launch {
                                    val exportedFile = withContext(Dispatchers.IO) {
                                        SmpExporter.exportSongUnitToSmp(appContext, song)
                                    }
                                    isExporting = false
                                    if (exportedFile != null) {
                                        lastExportPath = exportedFile.absolutePath
                                        Toast.makeText(context, "Export SMP réussi", Toast.LENGTH_SHORT).show()
                                    } else {
                                        lastExportPath = null
                                        Toast.makeText(context, "Export SMP échoué", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Text(if (isExporting) "Export..." else "Exporter SMP")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                val smpMarker = buildSmpItem(song.id)
                                PlaylistRepository.createIfNotExists(SMP_DEBUG_PLAYLIST_NAME)
                                PlaylistRepository.assignSongToPlaylist(SMP_DEBUG_PLAYLIST_NAME, smpMarker)
                                PlaylistRepository.renameSongInPlaylist(
                                    playlistName = SMP_DEBUG_PLAYLIST_NAME,
                                    uri = smpMarker,
                                    newTitle = song.title
                                )
                                Log.i(
                                    SMP_DEBUG_TAG,
                                    "SMP ajouté à la playlist debug: playlist=$SMP_DEBUG_PLAYLIST_NAME songId=${song.id} title=${song.title} marker=$smpMarker"
                                )
                                Toast.makeText(
                                    context,
                                    "Ajouté à la playlist $SMP_DEBUG_PLAYLIST_NAME",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text("Ajouter playlist")
                        }
                        Button(
                            onClick = {
                                isRoundTripRunning = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runSmpRoundTripTest(appContext, song)
                                    }
                                    isRoundTripRunning = false
                                    lastExportPath = result.exportPath ?: lastExportPath
                                    roundTripResult = result
                                    Log.i(
                                        SMP_TEST_TAG,
                                        "Round-trip SMP terminé: songId=${song.id} success=${result.isSuccessful} exportPath=${result.exportPath ?: "null"} importedSongId=${result.importedSongId ?: "null"}"
                                    )
                                }
                            },
                            enabled = !isRoundTripRunning
                        ) {
                            Text(if (isRoundTripRunning) "Test..." else "Test round-trip SMP")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Fermer")
                        }
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
private fun SmpRoundTripResultDialog(
    result: SmpRoundTripResult,
    onDismiss: () -> Unit
) {
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
                    text = "ROUND-TRIP RESULT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                SmpDebugField(label = "status", value = if (result.isSuccessful) "OK" else "KO")
                SmpDebugField(label = "exportPath", value = result.exportPath)
                SmpDebugField(label = "importedSongId", value = result.importedSongId)
                result.failureReason?.let {
                    SmpDebugField(label = "failureReason", value = it)
                }
                result.checks.forEach { check ->
                    SmpDebugField(
                        label = check.label,
                        value = buildString {
                            append(if (check.ok) "OK" else "KO")
                            check.detail?.takeIf { it.isNotBlank() }?.let {
                                append(" - ")
                                append(it)
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
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

private fun runSmpRoundTripTest(
    context: android.content.Context,
    sourceSong: SongUnit
): SmpRoundTripResult {
    val sourceSnapshot = buildSmpRoundTripSnapshot(context, sourceSong)
    Log.i(
        SMP_TEST_TAG,
        "Round-trip SMP démarré: songId=${sourceSong.id} title=${sourceSong.title}"
    )

    val exportedFile = SmpExporter.exportSongUnitToSmp(context, sourceSong)
    if (exportedFile == null) {
        Log.e(SMP_TEST_TAG, "Round-trip SMP impossible: export KO songId=${sourceSong.id}")
        return SmpRoundTripResult(
            exportPath = null,
            importedSongId = null,
            failureReason = "export SMP impossible",
            checks = listOf(
                SmpRoundTripCheck(label = "EXPORT", ok = false, detail = "fichier .smp non créé")
            )
        )
    }

    val importer = SmpImporter(context)
    val importedSong = importer.importSmp(Uri.fromFile(exportedFile))
    if (importedSong == null) {
        val reason = importer.lastFailureReason ?: "import SMP impossible"
        Log.e(
            SMP_TEST_TAG,
            "Round-trip SMP impossible: import KO songId=${sourceSong.id} exportPath=${exportedFile.absolutePath} reason=$reason"
        )
        return SmpRoundTripResult(
            exportPath = exportedFile.absolutePath,
            importedSongId = null,
            failureReason = reason,
            checks = listOf(
                SmpRoundTripCheck(label = "EXPORT", ok = true, detail = exportedFile.absolutePath),
                SmpRoundTripCheck(label = "IMPORT", ok = false, detail = reason)
            )
        )
    }

    val importedSnapshot = buildSmpRoundTripSnapshot(context, importedSong)
    val checks = buildList {
        add(SmpRoundTripCheck(label = "EXPORT", ok = true, detail = exportedFile.absolutePath))
        add(SmpRoundTripCheck(label = "IMPORT", ok = true, detail = importedSong.id))
        add(
            buildTrimCheck(
                label = "TRIM START",
                sourceValue = sourceSnapshot.trimStartMs,
                importedValue = importedSnapshot.trimStartMs
            )
        )
        add(
            buildTrimCheck(
                label = "TRIM END",
                sourceValue = sourceSnapshot.trimEndMs,
                importedValue = importedSnapshot.trimEndMs
            )
        )
        add(buildBucketCheck("AUDIO", sourceSnapshot.audioPresent, importedSong.audioPath, importedSong.storageFolder))
        add(buildBucketCheck("LYRICS", sourceSnapshot.lyricsPresent, importedSong.lyricsPath, importedSong.storageFolder))
        add(buildBucketCheck("CHORDS", sourceSnapshot.chordsPresent, importedSong.chordsPath, importedSong.storageFolder))
        add(buildBucketCheck("ANNOTATIONS", sourceSnapshot.annotationsPresent, importedSong.annotationsPath, importedSong.storageFolder))
        add(buildBucketCheck("MIDI", sourceSnapshot.midiPresent, importedSong.midiPath, importedSong.storageFolder))
        add(buildBucketCheck("DMX", sourceSnapshot.dmxPresent, importedSong.dmxPath, importedSong.storageFolder))
        add(buildBucketCheck("PROMPTER", sourceSnapshot.prompterPresent, importedSong.prompterPath, importedSong.storageFolder))
        add(buildStorageFolderCheck(importedSong))
    }

    checks.forEach { check ->
        Log.i(
            SMP_TEST_TAG,
            "${check.label}: ${if (check.ok) "OK" else "KO"}${check.detail?.let { " ($it)" } ?: ""}"
        )
    }

    return SmpRoundTripResult(
        exportPath = exportedFile.absolutePath,
        importedSongId = importedSong.id,
        checks = checks
    )
}

private fun buildSmpRoundTripSnapshot(
    context: android.content.Context,
    song: SongUnit
): SmpRoundTripSnapshot {
    val config = SmpConfig.fromSongUnit(context, song)
    return SmpRoundTripSnapshot(
        trimStartMs = config.playback?.trimStartMs,
        trimEndMs = config.playback?.trimEndMs,
        audioPresent = hasExistingFile(song.audioPath),
        lyricsPresent = hasExistingFile(song.lyricsPath),
        chordsPresent = hasExistingFile(song.chordsPath),
        annotationsPresent = hasExistingFile(song.annotationsPath),
        midiPresent = hasExistingFile(song.midiPath),
        dmxPresent = hasExistingFile(song.dmxPath),
        prompterPresent = hasExistingFile(song.prompterPath)
    )
}

private fun buildTrimCheck(
    label: String,
    sourceValue: Long?,
    importedValue: Long?
): SmpRoundTripCheck {
    return SmpRoundTripCheck(
        label = label,
        ok = sourceValue == importedValue,
        detail = "source=${sourceValue ?: "absent"} imported=${importedValue ?: "absent"}"
    )
}

private fun buildBucketCheck(
    label: String,
    sourcePresent: Boolean,
    importedPath: String?,
    importedStorageFolder: String?
): SmpRoundTripCheck {
    val importedPresent = hasExistingFile(importedPath)
    val insideStorageFolder = if (importedPresent) {
        isPathInsideStorageFolder(importedPath, importedStorageFolder)
    } else {
        true
    }

    return SmpRoundTripCheck(
        label = label,
        ok = sourcePresent == importedPresent && insideStorageFolder,
        detail = buildString {
            append("source=")
            append(if (sourcePresent) "present" else "absent")
            append(" imported=")
            append(if (importedPresent) "present" else "absent")
            if (importedPresent) {
                append(" folder=")
                append(if (insideStorageFolder) "ok" else "ko")
            }
        }
    )
}

private fun buildStorageFolderCheck(importedSong: SongUnit): SmpRoundTripCheck {
    val folderPath = importedSong.storageFolder
    val folderOk = !folderPath.isNullOrBlank() && File(folderPath).isDirectory
    return SmpRoundTripCheck(
        label = "STORAGE FOLDER",
        ok = folderOk,
        detail = folderPath ?: "absent"
    )
}

private fun hasExistingFile(path: String?): Boolean {
    return !path.isNullOrBlank() && File(path).isFile
}

private fun isPathInsideStorageFolder(path: String?, storageFolder: String?): Boolean {
    if (path.isNullOrBlank() || storageFolder.isNullOrBlank()) {
        return false
    }

    return runCatching {
        val filePath = File(path).canonicalPath
        val folderPath = File(storageFolder).canonicalPath.trimEnd(File.separatorChar)
        filePath == folderPath || filePath.startsWith("$folderPath${File.separator}")
    }.getOrDefault(false)
}

@Composable
private fun SmpGeneratedConfigDialog(
    json: String,
    onDismiss: () -> Unit
) {
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
                    text = "config.json généré",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        text = json,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
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
    val lyricsPath = detail.song.lyricsPath
    val trimStartMs = detail.playback?.trimStartMs ?: 0L
    val trimEndMs = detail.playback?.trimEndMs
    var status by remember(detail.song.id) { mutableStateOf("Prêt") }
    var parsedLyrics by remember(detail.song.id) { mutableStateOf<List<LrcLine>>(emptyList()) }
    var currentPositionMs by remember(detail.song.id) { mutableStateOf(trimStartMs) }
    var currentLrcIndex by remember(detail.song.id) { mutableStateOf(0) }
    var playbackWatcherJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(lyricsPath) {
        if (lyricsPath.isNullOrBlank()) {
            parsedLyrics = emptyList()
            currentLrcIndex = 0
            return@LaunchedEffect
        }

        val lyricsFile = File(lyricsPath)
        if (!lyricsFile.isFile) {
            parsedLyrics = emptyList()
            currentLrcIndex = 0
            Log.w(
                SMP_PLAYER_TAG,
                "Lyrics SMP absentes: songId=${detail.song.id} lyricsPath=$lyricsPath"
            )
            return@LaunchedEffect
        }

        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                parseLrc(lyricsFile.readText(Charsets.UTF_8))
            }.onFailure { error ->
                Log.e(
                    SMP_PLAYER_TAG,
                    "Lecture lyrics SMP impossible: songId=${detail.song.id} lyricsPath=$lyricsPath",
                    error
                )
            }.getOrDefault(emptyList())
        }

        parsedLyrics = parsed
        currentLrcIndex = if (parsed.isEmpty()) 0 else {
            findActiveLrcIndex(parsed, trimStartMs).coerceAtLeast(0)
        }
    }

    fun cancelPlaybackWatcher() {
        playbackWatcherJob?.cancel()
        playbackWatcherJob = null
    }

    fun updateLyricsPosition(positionMs: Long) {
        currentPositionMs = positionMs
        if (parsedLyrics.isEmpty()) {
            currentLrcIndex = 0
            return
        }

        val newIndex = findActiveLrcIndex(parsedLyrics, positionMs)
        currentLrcIndex = newIndex.coerceAtLeast(0)
    }

    fun stopPlayback(cancelWatcher: Boolean = true) {
        if (cancelWatcher) {
            cancelPlaybackWatcher()
        }
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
        updateLyricsPosition(trimStartMs)
        status = "Arrêté"
    }

    fun startPlaybackWatcher() {
        cancelPlaybackWatcher()
        playbackWatcherJob = scope.launch {
            while (true) {
                val positionMs = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)
                updateLyricsPosition(positionMs)

                if (trimEndMs != null && trimEndMs > 0L && positionMs >= trimEndMs) {
                    stopPlayback(cancelWatcher = false)
                    playbackWatcherJob = null
                    return@launch
                }

                val isPlaying = runCatching { exoPlayer.isPlaying }.getOrDefault(false)
                if (!isPlaying) {
                    delay(SMP_PLAYER_POLL_MS)
                    continue
                }

                delay(SMP_PLAYER_POLL_MS)
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
        cancelPlaybackWatcher()

        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
            exoPlayer.prepare()
            exoPlayer.seekTo(trimStartMs)
            exoPlayer.play()
        }.onSuccess {
            updateLyricsPosition(trimStartMs)
            status = "Lecture"
            Log.i(
                SMP_PLAYER_TAG,
                "Lecture SMP démarrée: songId=${detail.song.id} title=${detail.song.title} trimStartMs=$trimStartMs trimEndMs=${trimEndMs ?: 0L}"
            )
            startPlaybackWatcher()
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
                    cancelPlaybackWatcher()
                    updateLyricsPosition(runCatching { exoPlayer.currentPosition }.getOrDefault(currentPositionMs))
                    status = "Terminé"
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            cancelPlaybackWatcher()
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
                if (parsedLyrics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SmpLyricsWindow(
                        previous = parsedLyrics.getOrNull(currentLrcIndex - 1),
                        current = parsedLyrics.getOrNull(currentLrcIndex),
                        next = parsedLyrics.getOrNull(currentLrcIndex + 1)
                    )
                }
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

@Composable
private fun SmpLyricsWindow(
    previous: LrcLine?,
    current: LrcLine?,
    next: LrcLine?
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        previous?.let {
            Text(
                text = it.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
            text = current?.text?.ifBlank { "..." } ?: "...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        next?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
