package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.PlaylistRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // dossiers enregistrés par l’utilisateur
    var folders by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // chansons du dossier sélectionné
    var songs by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // chanson sélectionnée pour l’affectation (appui long = ancien comportement)
    var songToAssign by remember { mutableStateOf<Uri?>(null) }

    // sélection multiple (nouveau)
    val selectedSongs = remember { mutableStateListOf<Uri>() }

    // dialog d’affectation pour la sélection multiple
    var showAssignDialog by remember { mutableStateOf(false) }

    // picker de dossier
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                folders = folders + uri
                songs = listSongsInFolder(context, uri)
                selectedSongs.clear()
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("Bibliothèque", color = Color.White)
        Spacer(Modifier.height(12.dp))

        // bouton : ajouter un dossier
        Button(
            onClick = { pickFolderLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ajouter un dossier", color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        // dossiers enregistrés
        if (folders.isNotEmpty()) {
            Text("Dossiers enregistrés :", color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            folders.forEach { folderUri ->
                // on n’affiche plus le chemin complet
                val folderName = DocumentFile.fromTreeUri(context, folderUri)?.name
                    ?: "Dossier"

                Text(
                    text = "📁 $folderName",
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songs = listSongsInFolder(context, folderUri)
                            selectedSongs.clear()
                        }
                        .padding(vertical = 4.dp)
                )
            }
        } else {
            Text(
                "Aucun dossier pour l’instant.\nAjoute ton dossier Music → puis tes MP3.",
                color = Color.Gray
            )
        }

        Spacer(Modifier.height(16.dp))

        // liste des chansons avec noms nettoyés
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                // si la barre de sélection est affichée, on laisse un peu de place en bas
                .padding(bottom = if (selectedSongs.isNotEmpty()) 56.dp else 0.dp)
        ) {
            items(songs) { songUri ->
                // Nettoyage du nom
                val fullName = songUri.lastPathSegment ?: "inconnu.mp3"
                val displayName = fullName
                    .substringAfterLast('/')
                    .substringAfterLast(':')
                    .replace("%20", " ")
                    .replace(".mp3", "", ignoreCase = true)
                    .trim()

                val isSelected = songUri in selectedSongs

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .combinedClickable(
                            onClick = {
                                // toggle sélection
                                if (isSelected) {
                                    selectedSongs.remove(songUri)
                                } else {
                                    selectedSongs.add(songUri)
                                }
                            },
                            onLongClick = {
                                // ancien comportement : affecter UNE chanson
                                songToAssign = songUri
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // petit carré sélection
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(1.dp, Color(0xFF1DB954))
                            .background(if (isSelected) Color(0xFF1DB954) else Color.Transparent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = displayName,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // barre basse quand il y a des titres sélectionnés
        if (selectedSongs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${selectedSongs.size} sélectionné(s)", color = Color.White)
                Row {
                    TextButton(onClick = { selectedSongs.clear() }) {
                        Text("Tout effacer", color = Color.Gray)
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { showAssignDialog = true }) {
                        Text("Attribuer", color = Color.White)
                    }
                }
            }
        }
    }

    // -------- dialog d’affectation (multi) --------
    if (showAssignDialog) {
        val playlists = PlaylistRepository.getPlaylists()
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Ajouter à une playlist", color = Color.White) },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        "Aucune playlist. Va dans l’onglet “Toutes” pour en créer.",
                        color = Color.Gray
                    )
                } else {
                    Column {
                        playlists.forEach { plName ->
                            Text(
                                text = plName,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        selectedSongs.forEach { uri ->
                                            PlaylistRepository.assignSongToPlaylist(
                                                plName,
                                                uri.toString()
                                            )
                                        }
                                        selectedSongs.clear()
                                        showAssignDialog = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Fermer", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // -------- dialog d’affectation (1 seule chanson, appui long) --------
    if (songToAssign != null) {
        val currentSong = songToAssign!!
        val playlists = PlaylistRepository.getPlaylists()

        AlertDialog(
            onDismissRequest = { songToAssign = null },
            title = { Text("Ajouter à une playlist", color = Color.White) },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        "Aucune playlist.\nVa dans l’onglet “Toutes” pour en créer.",
                        color = Color.Gray
                    )
                } else {
                    Column {
                        playlists.forEach { plName ->
                            Text(
                                text = plName,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        PlaylistRepository.assignSongToPlaylist(
                                            plName,
                                            currentSong.toString()
                                        )
                                        songToAssign = null
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAssign = null }) {
                    Text("Fermer", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

// ---------- utils très simples pour l’instant ----------
private fun listSongsInFolder(context: Context, folderUri: Uri): List<Uri> {
    val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    return docFile
        .listFiles()
        .filter { it.isFile && (it.name?.endsWith(".mp3", ignoreCase = true) == true) }
        .mapNotNull { it.uri }
}