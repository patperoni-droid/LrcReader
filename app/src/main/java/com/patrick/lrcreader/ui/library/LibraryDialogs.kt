package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.ui.LibraryEntry

@Composable
fun AssignDialog(
    show: Boolean,
    selectedSongs: Set<Uri>,
    onDismiss: () -> Unit,
    onAssignedDone: () -> Unit
) {
    if (!show) return

    val playlists = PlaylistRepository.getPlaylists()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter à une playlist", color = Color.White) },
        text = {
            if (playlists.isEmpty()) {
                Text("Aucune playlist.\nVa dans l’onglet “Toutes” pour en créer.", color = Color.Gray)
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
                                    selectedSongs.forEach { u ->
                                        PlaylistRepository.assignSongToPlaylist(plName, u.toString())
                                    }
                                    onAssignedDone()
                                }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Color.White) } },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun DeleteConfirmDialog(
    show: Boolean,
    pendingDeleteUri: Uri?,
    onCancel: () -> Unit,
    onConfirmDelete: (Uri) -> Unit
) {
    if (!show || pendingDeleteUri == null) return

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Supprimer le fichier", color = Color.White) },
        text = { Text("Supprimer définitivement ce fichier ?", color = Color.White) },
        confirmButton = {
            TextButton(onClick = { onConfirmDelete(pendingDeleteUri) }) {
                Text("Supprimer", color = Color(0xFFFF6464))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annuler", color = Color(0xFFB0BEC5)) }
        },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun RenameDialog(
    show: Boolean,
    renameText: String,
    onRenameText: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Renommer", color = Color.White) },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = onRenameText,
                label = { Text("Nouveau nom", color = Color.LightGray) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) { Text("OK", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annuler", color = Color(0xFFB0BEC5)) }
        },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun MoveBrowserDialog(
    show: Boolean,
    indexAll: List<LibraryIndexCache.CachedEntry>,
    root: Uri?,
    moveBrowserFolder: Uri?,
    moveBrowserStack: List<Uri>,
    onGoUp: () -> Unit,
    onEnterFolder: (Uri) -> Unit,
    onMoveHere: () -> Unit,
    onDismiss: () -> Unit,
    onOtherFolder: () -> Unit
) {
    if (!show || root == null) return

    val currentDest = moveBrowserFolder ?: root

    val destFolders = androidx.compose.runtime.remember(indexAll, currentDest) {
        if (currentDest == null) emptyList()
        else LibraryIndexCache.childrenOf(indexAll, currentDest)
            .filter { it.isDirectory }
            .map { e -> LibraryEntry(Uri.parse(e.uriString), e.name, true) }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déplacer vers…", color = Color.White) },
        text = {
            Column {
                if (moveBrowserStack.isNotEmpty()) {
                    Text(
                        "⬅ Retour",
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onGoUp() }
                    )
                }

                Text(
                    "📥 Déplacer ici",
                    color = Color(0xFFFFC107),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clickable { onMoveHere() }
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn {
                    items(destFolders, key = { it.uri.toString() }) { folder ->
                        Text(
                            text = "📁 ${folder.name}",
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onEnterFolder(folder.uri) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onOtherFolder) { Text("Autre dossier…", color = Color.Gray) }
        },
        containerColor = Color(0xFF222222)
    )
}