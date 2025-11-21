package com.patrick.lrcreader.ui

import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    // écoute les changements du repo
    val version by PlaylistRepository.version
    val playlists = remember(version) { PlaylistRepository.getPlaylists() }

    // dialog création
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // dialog renommage
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    DarkBlueGradientBackground {
        Box(
            modifier = modifier
                .fillMaxSize()
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

                // ---- bouton "nouvelle liste" ----
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists) { name ->
                            PlaylistRow(
                                name = name,
                                onClick = { onPlaylistClick(name) },
                                onRename = {
                                    renameTarget = name
                                    renameText = name
                                }
                            )
                        }
                    }
                }
            }

            // ---- dialog création ----
            if (showCreateDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateDialog = false },
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
                                newName = ""
                            }
                            showCreateDialog = false
                        }) {
                            Text("OK", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Annuler", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF222222)
                )
            }

            // ---- dialog renommage ----
            if (renameTarget != null) {
                AlertDialog(
                    onDismissRequest = { renameTarget = null },
                    title = { Text("Renommer la liste", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Nouveau nom") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val oldName = renameTarget ?: return@TextButton
                            val ok = PlaylistRepository.renamePlaylist(oldName, renameText)
                            if (ok) {
                                // on ferme seulement si le renommage a marché
                                renameTarget = null
                            }
                            // si le nom existe déjà, on ne ferme pas → l’utilisateur corrige
                        }) {
                            Text("OK", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameTarget = null }) {
                            Text("Annuler", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF222222)
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    onClick: () -> Unit,
    onRename: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() }
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(Color(0xFF1E1E1E))
            ) {
                DropdownMenuItem(
                    text = { Text("Renommer", color = Color.White) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
            }
        }
    }
}