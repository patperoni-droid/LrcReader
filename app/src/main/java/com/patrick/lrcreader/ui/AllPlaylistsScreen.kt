package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository

@Composable
fun AllPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (String) -> Unit
) {
    // on écoute les changements du repo
    val version by PlaylistRepository.version
    val playlists = remember(version) { PlaylistRepository.getPlaylists() }

    // dialog création
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Vos listes de lecture",
                color = Color(0xFF1DB954),
                fontSize = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            // ---- bouton "nouvelle liste" TOUJOURS visible ----
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle liste de lecture")
            }

            Spacer(Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "Aucune liste pour l’instant.",
                    color = Color.Gray
                )
            } else {
                // ✅ liste des listes
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E))
                                .clickable { onPlaylistClick(name) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ---- dialog création ----
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Nouvelle liste", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nom de la liste") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clean = newName.trim()
                        if (clean.isNotEmpty()) {
                            PlaylistRepository.addPlaylist(clean)
                            // pas besoin de rafraîchir à la main : version a changé
                            newName = ""
                        }
                        showDialog = false
                    }) {
                        Text("OK", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Annuler", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}