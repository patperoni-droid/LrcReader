package com.patrick.lrcreader

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.readUsltFromUri
import com.patrick.lrcreader.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                val ctx = this
                val mediaPlayer = remember { MediaPlayer() }

                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

                var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Player) }

                // on peut la sauver, c’est juste un String?
                var openedPlaylist by rememberSaveable { mutableStateOf<String?>(null) }

                var currentPlayToken by remember { mutableStateOf(0L) }
                val repoVersion by PlaylistRepository.version

                val playWithCrossfade: (String, String?) -> Unit = remember {
                    { uriString, playlistName ->
                        val myToken = currentPlayToken + 1
                        currentPlayToken = myToken

                        crossfadePlay(
                            context = ctx,
                            mediaPlayer = mediaPlayer,
                            uriString = uriString,
                            playlistName = playlistName,
                            playToken = myToken,
                            getCurrentToken = { currentPlayToken },
                            onLyricsLoaded = { text ->
                                parsedLines = if (!text.isNullOrBlank()) parseLrc(text) else emptyList()
                            },
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false }
                        )

                        selectedTab = BottomTab.Player
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { mediaPlayer.release() }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab: BottomTab -> selectedTab = tab }
                        )
                    }
                ) { innerPadding ->

                    when (selectedTab) {

                        is BottomTab.Player -> PlayerScreen(
                            modifier = Modifier.padding(innerPadding),
                            mediaPlayer = mediaPlayer,
                            isPlaying = isPlaying,
                            onIsPlayingChange = { isPlaying = it },
                            parsedLines = parsedLines,
                            onParsedLinesChange = { parsedLines = it },
                        )

                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName ->
                                playWithCrossfade(uri, playlistName)
                            },
                            refreshKey = repoVersion
                        )

                        is BottomTab.Library -> LibraryScreen(
                            modifier = Modifier.padding(innerPadding)
                        )

                        is BottomTab.AllPlaylists -> {
                            val m = Modifier.padding(innerPadding)
                            if (openedPlaylist == null) {
                                AllPlaylistsScreen(
                                    modifier = m,
                                    onPlaylistClick = { name ->
                                        openedPlaylist = name
                                    }
                                )
                            } else {
                                PlaylistDetailScreen(
                                    modifier = m,
                                    playlistName = openedPlaylist!!,
                                    onBack = { openedPlaylist = null },
                                    onPlaySong = { uriString ->
                                        playWithCrossfade(uriString, openedPlaylist)
                                    }
                                )
                            }
                        }

                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = {
                                // ex: selectedTab = BottomTab.QuickPlaylists
                            }
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  LECTURE AVEC FONDU + SÉCURITÉ URI                                         */
/* -------------------------------------------------------------------------- */

private fun hasReadAccess(ctx: Context, uri: Uri): Boolean {
    return try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { _: ParcelFileDescriptor? -> }
        true
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        true
    }
}

private fun crossfadePlay(
    context: Context,
    mediaPlayer: MediaPlayer,
    uriString: String,
    playlistName: String?,
    playToken: Long,
    getCurrentToken: () -> Long,
    onLyricsLoaded: (String?) -> Unit,
    onStart: () -> Unit,
    onError: () -> Unit,
    fadeDurationMs: Long = 500
) {
    CoroutineScope(Dispatchers.Main).launch {
        if (mediaPlayer.isPlaying) {
            fadeVolume(mediaPlayer, 1f, 0f, fadeDurationMs)
        } else {
            mediaPlayer.setVolume(0f, 0f)
        }

        try {
            val uri = uriString.trim().toUri()

            if (!hasReadAccess(context, uri)) {
                Toast.makeText(
                    context,
                    "Accès refusé au fichier. Réautorise le dossier ou rechoisis le titre.",
                    Toast.LENGTH_LONG
                ).show()
                onError()
                return@launch
            }

            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepare()

            val lrcText = readUsltFromUri(context, uri)
            onLyricsLoaded(lrcText)

            mediaPlayer.start()
            onStart()

            fadeVolume(mediaPlayer, 0f, 1f, fadeDurationMs)

            if (playlistName != null) {
                val thisToken = playToken
                val thisSong = uriString
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10_000)
                    if (getCurrentToken() == thisToken && mediaPlayer.isPlaying) {
                        PlaylistRepository.markSongPlayed(playlistName, thisSong)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Lecture impossible : ${e.message ?: "erreur inconnue"}",
                Toast.LENGTH_LONG
            ).show()
            onError()
        }
    }
}

private suspend fun fadeVolume(
    player: MediaPlayer,
    from: Float,
    to: Float,
    durationMs: Long
) {
    val steps = 20
    val stepTime = durationMs / steps
    val delta = (to - from) / steps
    for (i in 0..steps) {
        val v = (from + delta * i).coerceIn(0f, 1f)
        player.setVolume(v, v)
        delay(stepTime)
    }
    player.setVolume(to, to)
}

/* -------------------------------------------------------------------------- */
/*  ÉCRAN "PLUS..." (EXPORT / IMPORT + SON)                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    var exportText by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }
    var lastImportFile by remember { mutableStateOf<String?>(null) }
    var lastImportTime by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    // import fichier .json
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (!json.isNullOrBlank()) {
                    BackupManager.importState(
                        context = context,
                        json = json
                    ) {
                        lastImportFile = getDisplayName(context, uri)
                        lastImportTime = nowString()
                        lastImportSummary = "Import réussi"
                        onAfterImport()
                    }
                }
            } catch (e: Exception) {
                lastImportSummary = "Échec de l’import (${e.message ?: "erreur inconnue"})"
            }
        }
    }

    // ré-autoriser un dossier
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Toast.makeText(context, "Accès au dossier autorisé", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    // 🎧 sélecteur pour le son de remplissage
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerUri(context)) }
    var fillerName by remember {
        mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné")
    }
    val fillerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            FillerSoundPrefs.saveFillerUri(context, uri)
            fillerUri = uri
            fillerName = uri.lastPathSegment ?: "Son choisi"
            Toast.makeText(context, "Son enregistré : $fillerName", Toast.LENGTH_SHORT).show()
        }
    }

    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)
    val accent = Color(0xFFB06CFF)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Plus", color = onBg, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("Sauvegarde & restauration", color = sub, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // EXPORT
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Exporter l’état", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Nom du fichier", color = sub, fontSize = 11.sp) },
                    placeholder = { Text("lrc_backup", color = sub, fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(color = onBg, fontSize = 13.sp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = accent,
                        unfocusedIndicatorColor = Color(0xFF3A3A3A),
                        cursorColor = onBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        exportText = BackupManager.exportState(context, null, emptyList())
                    }) { Text("Générer", fontSize = 12.sp) }

                    val finalName = (saveName.trim().ifBlank { "lrc_backup" }) + ".json"

                    FilledTonalButton(
                        onClick = { saveJsonToDownloads(context, finalName, exportText) },
                        enabled = exportText.isNotBlank(),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E1E1E))
                    ) { Text("Enregistrer", fontSize = 12.sp) }

                    TextButton(
                        onClick = { shareJson(context, finalName, exportText) },
                        enabled = exportText.isNotBlank()
                    ) { Text("Partager", fontSize = 12.sp, color = accent) }
                }

                Spacer(Modifier.height(8.dp))
                Text("Aperçu", color = sub, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (exportText.isBlank()) "—"
                    else exportText.take(280) + if (exportText.length > 280) "…" else "",
                    color = onBg,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // IMPORT
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Importer une sauvegarde", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { fileLauncher.launch("application/json") }) {
                    Text("Choisir un fichier…", fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { treeLauncher.launch(null) }) {
                    Text("🔓 Ré-autoriser l’accès à un dossier", fontSize = 12.sp, color = accent)
                }

                Spacer(Modifier.height(10.dp))
                if (lastImportFile != null || lastImportTime != null || lastImportSummary != null) {
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(Modifier.height(8.dp))
                    Text("Dernier import", color = sub, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    lastImportFile?.let { Text("• Fichier : $it", color = onBg, fontSize = 12.sp) }
                    lastImportTime?.let { Text("• Heure : $it", color = onBg, fontSize = 12.sp) }
                    lastImportSummary?.let {
                        Text(
                            "• État : $it",
                            color = if (it.startsWith("Import réussi")) Color(0xFF6CFF9C) else Color(0xFFFF8A80),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text("Aucun import réalisé pour l’instant.", color = sub, fontSize = 12.sp)
                }
            }
        }

        // 🔊 SON DE REMPLISSAGE
        Spacer(Modifier.height(16.dp))
        Text("Son de remplissage", color = sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = card)) {
            Column(Modifier.padding(12.dp)) {
                Text("Sélection du son de remplissage", color = onBg, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ce son pourra être joué automatiquement quand un morceau se termine, pour éviter le silence.",
                    color = sub,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { fillerLauncher.launch("audio/*") }) {
                    Text("Choisir un fichier audio…", fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Fichier actuel :", color = sub, fontSize = 11.sp)
                Text(
                    fillerName,
                    color = if (fillerUri != null) Color(0xFFE040FB) else Color.Gray,
                    fontSize = 12.sp
                )
                if (fillerUri != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        FillerSoundPrefs.clear(context)
                        fillerUri = null
                        fillerName = "Aucun son sélectionné"
                    }) {
                        Text("🗑 Supprimer le son", fontSize = 12.sp, color = Color(0xFFFF8A80))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

/* -------------------------------------------------------------------------- */
/*  UTILITAIRES EXPORT / PARTAGE                                              */
/* -------------------------------------------------------------------------- */

@SuppressLint("InlinedApi")
fun saveJsonToDownloads(context: Context, fileName: String, json: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return false

        resolver.openOutputStream(itemUri)?.use { out ->
            out.write(json.toByteArray())
            out.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun shareJson(context: Context, fileName: String, json: String) {
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(json)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la sauvegarde"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/* -------------------------------------------------------------------------- */
/*  HELPERS “Dernier import”                                                  */
/* -------------------------------------------------------------------------- */

private fun getDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }
}

private fun nowString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}