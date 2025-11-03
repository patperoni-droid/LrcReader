package com.patrick.lrcreader

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.patrick.lrcreader.core.BackupManager
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                // contexte Compose
                val ctx = this
                // player unique
                val mediaPlayer = remember { MediaPlayer() }

                var isPlaying by remember { mutableStateOf(false) }
                var parsedLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
                var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Player) }

                // détail d’une playlist (onglet “Toutes”)
                var openedPlaylist by remember { mutableStateOf<String?>(null) }

                // token de lecture courant (pour savoir si la chanson a changé)
                var currentPlayToken by remember { mutableStateOf(0L) }

                // ✅ rafraîchissement des playlists
                val repoVersion by PlaylistRepository.version

                // callback de lecture (utilisé par plusieurs écrans)
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
                                parsedLines = if (!text.isNullOrBlank()) {
                                    parseLrc(text)
                                } else {
                                    emptyList()
                                }
                            },
                            onStart = { isPlaying = true },
                            onError = { isPlaying = false }
                        )

                        // on revient sur le lecteur
                        selectedTab = BottomTab.Player
                    }
                }

                // libérer le player quand l’activity meurt
                DisposableEffect(Unit) {
                    onDispose { mediaPlayer.release() }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                // si on clique sur “Toutes”, on ferme l’éventuel détail
                                if (tab is BottomTab.AllPlaylists) {
                                    openedPlaylist = null
                                }
                                selectedTab = tab
                            }
                        )
                    }
                ) { innerPadding ->

                    when (selectedTab) {

                        // 🟢 lecteur
                        is BottomTab.Player -> PlayerScreen(
                            modifier = Modifier.padding(innerPadding),
                            mediaPlayer = mediaPlayer,
                            isPlaying = isPlaying,
                            onIsPlayingChange = { isPlaying = it },
                            parsedLines = parsedLines,
                            onParsedLinesChange = { parsedLines = it },
                        )

                        // 🟠 playlists rapides
                        is BottomTab.QuickPlaylists -> QuickPlaylistsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPlaySong = { uri, playlistName ->
                                playWithCrossfade(uri, playlistName)
                            },
                            refreshKey = repoVersion
                        )

                        // 📁 bibliothèque
                        is BottomTab.Library -> LibraryScreen(
                            modifier = Modifier.padding(innerPadding)
                        )

                        // 📜 toutes les playlists
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

                        // ➕ onglet “Plus…”
                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = {
                                // si tu veux revenir sur les playlists après import :
                                // selectedTab = BottomTab.QuickPlaylists
                            }
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  FONCTION DE LECTURE AVEC FONDU                                            */
/* -------------------------------------------------------------------------- */
private fun crossfadePlay(
    context: Context,
    mediaPlayer: MediaPlayer,
    uriString: String,
    playlistName: String?,                 // peut être null
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
            val uri = Uri.parse(uriString)
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.prepare()

            val lrcText = readUsltFromUri(context, uri)
            onLyricsLoaded(lrcText)

            mediaPlayer.start()
            onStart()

            fadeVolume(mediaPlayer, 0f, 1f, fadeDurationMs)

            // après 10 s → marquer comme joué
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
/*  ÉCRAN "PLUS..." AVEC EXPORT / IMPORT                                      */
/* -------------------------------------------------------------------------- */
// -----------------------------------------------
// ÉCRAN "PLUS..." AVEC EXPORT / IMPORT + FEEDBACK
// -----------------------------------------------
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    var exportText by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }

    // 🔔 feedback d’import
    var lastImportName by remember { mutableStateOf<String?>(null) }
    var lastImportWhen by remember { mutableStateOf<String?>(null) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }
    var lastImportError by remember { mutableStateOf<String?>(null) }

    // ✅ OpenDocument = on peut relancer l’import autant de fois qu’on veut
    // et accepter plusieurs types MIME (json + texte).
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // (optionnel) conserver la permission si besoin plus tard
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val json = context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (!json.isNullOrBlank()) {
                    // on récupère un nom lisible pour l’UI
                    val display = getDisplayName(context, uri) ?: "sauvegarde.json"

                    // on importe
                    BackupManager.importState(
                        context = context,
                        json = json
                    ) {
                        // récap après import (simple, à partir du repo)
                        val plCount = PlaylistRepository.getPlaylists().size
                        val songCount = PlaylistRepository.getPlaylists()
                            .sumOf { PlaylistRepository.getAllSongsRaw(it).size }

                        lastImportName = display
                        lastImportWhen = nowString()
                        lastImportSummary = "Playlists: $plCount  |  Titres: $songCount"
                        lastImportError = null

                        onAfterImport()
                    }
                } else {
                    lastImportError = "Le fichier est vide ou illisible."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lastImportError = "Échec de l’import: ${e.message ?: "erreur inconnue"}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Plus / paramètres", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))

        // 🔤 Nom de sauvegarde
        androidx.compose.material3.OutlinedTextField(
            value = saveName,
            onValueChange = { saveName = it },
            label = { Text("Nom de la sauvegarde (sans extension)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color(0xFF8888FF),
                unfocusedIndicatorColor = Color.Gray,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(Modifier.height(12.dp))

        // 1) Générer
        androidx.compose.material3.Button(
            onClick = {
                val json = BackupManager.exportState(
                    context = context,
                    lastPlayer = null,
                    libraryFolders = emptyList()
                )
                exportText = json
            }
        ) { Text("1️⃣ Générer la sauvegarde") }

        Spacer(Modifier.height(8.dp))

        val finalName = if (saveName.isNotBlank()) saveName.trim() else "lrc_backup"
        val fileName = "$finalName.json"

        // 2) Enregistrer
        androidx.compose.material3.Button(
            onClick = { saveJsonToDownloads(context, fileName, exportText) },
            enabled = exportText.isNotBlank()
        ) { Text("2️⃣ Enregistrer sous $fileName") }

        Spacer(Modifier.height(8.dp))

        // 3) Partager
        androidx.compose.material3.Button(
            onClick = { shareJson(context, fileName, exportText) },
            enabled = exportText.isNotBlank()
        ) { Text("3️⃣ Partager $fileName") }

        Spacer(Modifier.height(8.dp))

        // 4) Importer (multiples imports OK)
        androidx.compose.material3.Button(
            onClick = {
                // on accepte json + texte (au cas où le téléphone marque le fichier en text/plain)
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }
        ) { Text("📂 Importer un fichier JSON") }

        // 🔔 Feedback visuel (succès / erreur)
        Spacer(Modifier.height(16.dp))
        if (lastImportError != null) {
            Text(
                text = "❌ ${lastImportError}",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp
            )
        } else if (lastImportName != null) {
            Text(
                text = "✅ Import réussi",
                color = Color(0xFF5BD06A),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "Fichier : ${lastImportName}", color = Color.White, fontSize = 14.sp)
            if (lastImportWhen != null) {
                Text(text = "Heure   : ${lastImportWhen}", color = Color(0xFFBBBBBB), fontSize = 12.sp)
            }
            if (lastImportSummary != null) {
                Text(text = lastImportSummary!!, color = Color(0xFFBBBBBB), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "Aperçu JSON :", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (exportText.isBlank()) "(aucune donnée exportée)"
            else exportText.take(600) + if (exportText.length > 600) "..." else "",
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  UTILITAIRES EXPORT / PARTAGE                                              */
/* -------------------------------------------------------------------------- */

/**
 * Écrit un fichier .json dans Téléchargements
 */
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

/**
 * Ouvre le panneau de partage Android avec le JSON en mémoire
 */
fun shareJson(context: Context, fileName: String, json: String) {
    try {
        // on met le fichier dans le cache
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
// ---------- Fonctions utilitaires pour l'import ----------
private fun getDisplayName(context: Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun nowString(): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}