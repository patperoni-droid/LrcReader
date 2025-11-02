package com.patrick.lrcreader

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.parseLrc
import com.patrick.lrcreader.core.readUsltFromUri
import com.patrick.lrcreader.ui.*
import kotlinx.coroutines.*
import java.io.File

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

                // détail d’une playlist (onglet “Toutes”)
                var openedPlaylist by remember { mutableStateOf<String?>(null) }

                // token de lecture courant
                var currentPlayToken by remember { mutableStateOf(0L) }

                // ✅ lire directement la valeur Int
                val repoVersion by PlaylistRepository.version

                // callback unique
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

                        // on bascule sur l’onglet “Lecteur”
                        selectedTab = BottomTab.Player
                    }
                }

                // libération du player
                DisposableEffect(Unit) {
                    onDispose { mediaPlayer.release() }
                }

                Scaffold(
                    containerColor = Color.Black,
                    bottomBar = {
                        BottomTabsBar(
                            selected = selectedTab,
                            onSelected = { tab ->
                                // si on clique sur “Toutes” on ferme le détail
                                if (tab is BottomTab.AllPlaylists) {
                                    openedPlaylist = null
                                }
                                selectedTab = tab
                            }
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

                        // 🟠 playlists “live”
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

                        // 🆕 onglet “Plus…”
                        is BottomTab.More -> MoreScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = ctx,
                            onAfterImport = {
                                // si tu veux recharger l’écran après import
                                // selectedTab = BottomTab.QuickPlaylists
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------- lecture avec fondu + marquage “joué” après 10 s --------
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

            // ⏱️ après 10 s → marque joué
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

// 👇 petit écran “Plus…” avec export/import JSON + ENREGISTRER + PARTAGER + IMPORTER FICHIER
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    var exportText by remember { mutableStateOf("") }

    // sélecteur de fichier JSON
    val launcher = rememberLauncherForActivityResult(
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
                        onAfterImport()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

        Button(
            onClick = {
                val json = BackupManager.exportState(
                    context = context,
                    lastPlayer = null,
                    libraryFolders = emptyList()
                )
                exportText = json
            }
        ) {
            Text("1️⃣ Générer la sauvegarde")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                saveJsonToDownloads(context, "lrc_backup.json", exportText)
            },
            enabled = exportText.isNotBlank()
        ) {
            Text("2️⃣ Enregistrer dans Téléchargements")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                shareJson(context, "lrc_backup.json", exportText)
            },
            enabled = exportText.isNotBlank()
        ) {
            Text("3️⃣ Partager…")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = { launcher.launch("application/json") }) {
            Text("📂 Importer un fichier JSON")
        }

        Spacer(Modifier.height(16.dp))
        Text(text = "JSON actuel :", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (exportText.isBlank()) "(aucune donnée exportée)" else exportText.take(600) + if (exportText.length > 600) "..." else "",
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp
        )
    }
}

/**
 * Écrit un fichier .json dans Téléchargements
 */
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
        context.startActivity(
            Intent.createChooser(intent, "Partager la sauvegarde")
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}