package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.patrick.lrcreader.core.PlaylistRepository
import androidx.documentfile.provider.DocumentFile

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

    // chanson sélectionnée pour l’affectation
    var songToAssign by remember { mutableStateOf<Uri?>(null) }

    // picker de dossier
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                folders = folders + uri
                songs = listSongsInFolder(context, uri)
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
                Text(
                    text = folderUri.toString(),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songs = listSongsInFolder(context, folderUri)
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
            modifier = Modifier.weight(1f)
        ) {
            items(songs) { songUri ->
                // Nettoyage du nom de la chanson
                val fullName = songUri.lastPathSegment ?: "inconnu.mp3"
                val displayName = fullName
                    .substringAfterLast('/')
                    .substringAfterLast(':')
                    .replace("%20", " ")
                    .replace(".mp3", "", ignoreCase = true)
                    .trim()

                Text(
                    text = displayName,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .combinedClickable(
                            onClick = {
                                // Lecture directe (à venir)
                            },
                            onLongClick = {
                                songToAssign = songUri
                            }
                        )
                )
            }
        }
    }

    // dialog d’affectation à une playlist
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