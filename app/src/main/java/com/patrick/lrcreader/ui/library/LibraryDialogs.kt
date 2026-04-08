package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.LibraryEntry

data class AuthorizedTransferFolderOption(
    val uri: Uri,
    val title: String,
    val subtitle: String
)

@Composable
fun AssignDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    if (!show) return

    val playlists = PlaylistRepository.getPlaylists()
    var newPlaylistName by remember(show) { mutableStateOf("") }
    val canCreatePlaylist = newPlaylistName.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_assign_to_playlist_title), color = Color.White) },
        text = {
            if (playlists.isEmpty()) {
                Column {
                    Text(stringResource(R.string.library_assign_to_playlist_empty), color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(stringResource(R.string.all_playlists_name_label), color = Color.LightGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (canCreatePlaylist) {
                                    onCreatePlaylist(newPlaylistName.trim())
                                }
                            }
                        )
                    )
                }
            } else {
                Column {
                    playlists.forEach { plName ->
                        Text(
                            text = plName,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onPlaylistSelected(plName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (playlists.isEmpty()) {
                TextButton(
                    enabled = canCreatePlaylist,
                    onClick = { onCreatePlaylist(newPlaylistName.trim()) }
                ) {
                    Text(stringResource(R.string.all_playlists_new_playlist_button), color = Color.White)
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = Color.White) }
            }
        },
        dismissButton = {
            if (playlists.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            }
        },
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
        title = { Text(stringResource(R.string.library_delete_file_title), color = Color.White) },
        text = { Text(stringResource(R.string.library_delete_file_confirm_text), color = Color.White) },
        confirmButton = {
            TextButton(onClick = { onConfirmDelete(pendingDeleteUri) }) {
                Text(stringResource(R.string.library_delete_action), color = Color(0xFFFF6464))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5)) }
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
        title = { Text(stringResource(R.string.library_rename_title), color = Color.White) },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = onRenameText,
                label = { Text(stringResource(R.string.library_new_name_label), color = Color.LightGray) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() })
            )
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) {
                Text(stringResource(R.string.common_ok), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5)) }
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
    titleText: String,
    actionText: String,
    otherFolderText: String,
    onGoUp: () -> Unit,
    onEnterFolder: (Uri) -> Unit,
    onMoveHere: () -> Unit,
    onDismiss: () -> Unit,
    onOtherFolder: () -> Unit
) {
    if (!show || root == null) return

    val currentDest = moveBrowserFolder ?: root

    val destFolders = androidx.compose.runtime.remember(indexAll, currentDest) {
        LibraryIndexCache.childrenOf(indexAll, currentDest)
            .filter { it.isDirectory }
            .map { e -> LibraryEntry(Uri.parse(e.uriString), e.name, true) }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText, color = Color.White) },
        text = {
            Column {
                if (moveBrowserStack.isNotEmpty()) {
                    Text(
                        stringResource(R.string.library_move_browser_back),
                        color = Color(0xFFB0BEC5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onGoUp() }
                    )
                }

                Text(
                    actionText,
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
                            text = stringResource(R.string.library_move_browser_folder_entry, folder.name),
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
            TextButton(onClick = onOtherFolder) {
                Text(otherFolderText, color = Color.Gray)
            }
        },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun AuthorizedTransferFoldersDialog(
    show: Boolean,
    folders: List<AuthorizedTransferFolderOption>,
    onDismiss: () -> Unit,
    onChooseFolder: (Uri) -> Unit,
    onChooseAnotherFolder: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_transfer_reuse_title), color = Color.White) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.library_transfer_reuse_message),
                    color = Color(0xFFB0BEC5)
                )

                Spacer(Modifier.height(12.dp))

                LazyColumn {
                    items(folders, key = { it.uri.toString() }) { folder ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onChooseFolder(folder.uri) }
                        ) {
                            Text(folder.title, color = Color.White)
                            Text(folder.subtitle, color = Color(0xFFB0BEC5))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseAnotherFolder) {
                Text(stringResource(R.string.library_move_browser_other_folder), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
            }
        },
        containerColor = Color(0xFF222222)
    )
}
