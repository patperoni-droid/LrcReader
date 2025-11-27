package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

    // Palette console
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )
    val accent = Color(0xFFFFC107)
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ---------- HEADER ----------
            Text(
                text = "PLAYLISTS",
                color = accent,
                fontSize = 18.sp,
                letterSpacing = 3.sp
            )
            Text(
                text = "Vos listes de lecture",
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // ---- bouton "nouvelle liste" ----
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = onBg
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.9f),
                            accent.copy(alpha = 0.4f)
                        )
                    )
                )
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Nouvelle liste de lecture",
                    color = onBg,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Text(
                    text = "Aucune liste pour l’instant.",
                    color = sub,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                title = { Text("Nouvelle liste", color = onBg) },
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
                        Text("OK", color = onBg)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Annuler", color = sub)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }

        // ---- dialog renommage ----
        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Renommer la liste", color = onBg) },
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
                        val ok = PlaylistRepository.renamePlaylist(oldName, renameText.trim())
                        if (ok) {
                            renameTarget = null
                        }
                        // si le nom existe déjà, on ne ferme pas → l’utilisateur corrige
                    }) {
                        Text("OK", color = onBg)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text("Annuler", color = sub)
                    }
                },
                containerColor = Color(0xFF222222)
            )
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

    val cardColor = Color(0xFF1B1B1B)
    val borderColor = Color(0x33FFFFFF)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    color = Color(0xFFFFF8E1),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Playlist live",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFFFFF8E1)
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
}