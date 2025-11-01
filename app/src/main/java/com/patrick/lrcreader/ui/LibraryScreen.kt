package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.border
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
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.PlaylistRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // dossiers choisis
    var folders by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // chansons du dossier courant
    var songs by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // sélection multiple
    var selectedSongs by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    // dialog "ajouter à playlist"
    var showAssignDialog by remember { mutableStateOf(false) }

    // picker de dossier SAF
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                folders = folders + uri
                songs = listSongsInFolder(context, uri)
                // on vide la sélection quand on change de dossier
                selectedSongs = emptySet()
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
                val folderName = DocumentFile.fromTreeUri(context, folderUri)?.name ?: "Dossier"
                Text(
                    text = "📁 $folderName",
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            songs = listSongsInFolder(context, folderUri)
                            selectedSongs = emptySet()
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

        // on met la liste dans un Box pour pouvoir coller un petit bandeau en bas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // liste des morceaux
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(songs) { songUri ->
                    val isSelected = selectedSongs.contains(songUri)

                    val fullName = songUri.lastPathSegment ?: "inconnu.mp3"
                    val displayName = fullName
                        .substringAfterLast('/')
                        .substringAfterLast(':')
                        .replace("%20", " ")
                        .replace(".mp3", "", ignoreCase = true)
                        .trim()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    // toggle sélection
                                    selectedSongs =
                                        if (isSelected) selectedSongs - songUri
                                        else selectedSongs + songUri
                                },
                                onLongClick = {
                                    // ancien comportement : une seule
                                    selectedSongs = setOf(songUri)
                                    showAssignDialog = true
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // petit carré
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (isSelected) Color(0xFF2ECC71) else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2ECC71)
                                )
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

            // 👉 petit bandeau de sélection, HAUT FIXE
            if (selectedSongs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1E1E1E)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedSongs.size} sélectionné(s)",
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Row {
                        Text(
                            text = "Tout effacer",
                            color = Color(0xFFE386FF),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { selectedSongs = emptySet() }
                        )
                        Text(
                            text = "Attribuer",
                            color = Color(0xFFE386FF),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { showAssignDialog = true }
                        )
                    }
                }
            }
        }
    }

    // dialog d’affectation à une playlist (multi)
    if (showAssignDialog) {
        val playlists = PlaylistRepository.getPlaylists()
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
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
                                        selectedSongs.forEach { uri ->
                                            PlaylistRepository.assignSongToPlaylist(
                                                plName,
                                                uri.toString()
                                            )
                                        }
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
}

// utils
private fun listSongsInFolder(context: Context, folderUri: Uri): List<Uri> {
    val docFile = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    return docFile
        .listFiles()
        .filter { it.isFile && (it.name?.endsWith(".mp3", ignoreCase = true) == true) }
        .mapNotNull { it.uri }
}