package com.patrick.lrcreader.ui.library

import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.LibraryEntry

private fun isPlayableByName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac") ||
            n.endsWith(".flac") || n.endsWith(".ogg") ||
            n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
            n.endsWith(".mov") || n.endsWith(".avi")
}

private fun isSmpUri(uriString: String): Boolean = uriString.startsWith("smp://")
private fun isJsonByName(name: String): Boolean = name.lowercase().endsWith(".json")
private fun isLrcByName(name: String): Boolean = name.lowercase().endsWith(".lrc")
private fun isConvertibleToSmpByName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".wave")
}

private fun audioTypeLabel(name: String): String? {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp3") -> "MP3"
        lower.endsWith(".wav") || lower.endsWith(".wave") -> "WAV"
        else -> null
    }
}

private fun isLegacyBackingTracksFolderName(name: String): Boolean {
    val normalized = name.trim()
    return normalized.equals("BackingTracks", ignoreCase = true) ||
        normalized.equals("BackingTrack", ignoreCase = true)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryList(
    entries: List<LibraryEntry>,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    isExplorerMode: Boolean,
    canImportBackupJson: Boolean,
    selectedSongs: Set<Uri>,
    selectionOnLongPress: Boolean = false,
    onToggleSelect: (Uri) -> Unit,
    onOpenFolder: (LibraryEntry) -> Unit,

    // ✅ ouvre le vrai lecteur (ton écran lecteur)
    onOpenPlayer: (Uri) -> Unit,

    // ✅ quick play dans la bibliothèque (sans ouvrir le lecteur)
    onQuickPlay: (Uri) -> Unit,

    // ✅ import d’un backup JSON (ne doit pas lancer le lecteur)
    onImportBackupJson: (Uri) -> Unit,

    // ✅ ouvre l’éditeur LRC
    onOpenLrcEditor: (Uri) -> Unit,
    onConvertOneToSmp: (Uri) -> Unit,

    onAssignOne: (Uri) -> Unit,
    onShareOne: (Uri) -> Unit,
    onCopyOne: (Uri) -> Unit,
    onMoveOne: (Uri) -> Unit,
    onRenameOne: (LibraryEntry) -> Unit,
    onDeleteOne: (Uri) -> Unit
) {
    val context = LocalContext.current
    val aliasVersion = TitleAliasesStore.version.intValue
    val selectionMode = selectedSongs.isNotEmpty()
    val liveTracksLabel = stringResource(R.string.live_tracks_label)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        items(entries, key = { "$aliasVersion:${it.uri}" }) { entry ->
            if (entry.isDirectory) {
                val uri = entry.uri
                val isSelected = selectedSongs.contains(uri)
                var menuOpen by remember { mutableStateOf(false) }
                val displayName =
                    if (isLegacyBackingTracksFolderName(entry.name)) liveTracksLabel else entry.name

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isExplorerMode) {
                                    Modifier
                                } else {
                                    Modifier
                                        .padding(vertical = 3.dp)
                                        .background(cardBg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) accent else rowBorder,
                                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                        )
                                }
                            )
                            .combinedClickable(
                                onClick = { onOpenFolder(entry) },
                                onLongClick = { onToggleSelect(uri) }
                            )
                            .padding(horizontal = 12.dp, vertical = if (isExplorerMode) 8.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable { onToggleSelect(uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Text("✕", color = accent, fontSize = 13.sp)
                        }

                        Spacer(Modifier.width(10.dp))

                        Icon(
                            Icons.Default.Folder,
                            null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayName,
                                color = Color.White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.library_list_copy_to_folder), color = Color.White)
                                    },
                                    onClick = { menuOpen = false; onCopyOne(uri) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.library_list_move_to_folder), color = Color.White)
                                    },
                                    onClick = { menuOpen = false; onMoveOne(uri) }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.library_delete_action),
                                            color = Color(0xFFFF6464)
                                        )
                                    },
                                    onClick = { menuOpen = false; onDeleteOne(uri) }
                                )
                            }
                        }
                    }
                    if (isExplorerMode) {
                        HorizontalDivider(color = rowBorder.copy(alpha = 0.5f))
                    }
                }

            } else {
                val uri = entry.uri
                val uriString = uri.toString()
                val isSelected = selectedSongs.contains(uri)
                var menuOpen by remember { mutableStateOf(false) }

                val isPrompter = uriString.startsWith("prompter://")
                val isSmp = isSmpUri(uriString)
                val canPlay = isPlayableByName(entry.name)
                val canOpenPlayer = canPlay || isPrompter || isSmp
                val isJson = isJsonByName(entry.name)
                val isLrc = isLrcByName(entry.name)
                val isConvertibleToSmp = isConvertibleToSmpByName(entry.name)
                val fileTypeLabel = audioTypeLabel(entry.name)
                val fileTypeTint = when (fileTypeLabel) {
                    "MP3" -> accent
                    "WAV" -> Color(0xFF64B5F6)
                    else -> if (canPlay || isSmp) accent else Color.White.copy(alpha = 0.75f)
                }
                val titleAlias = if ((canPlay || isSmp) && !isPrompter) {
                    TitleAliasesStore.getTitleForTrack(context, uri.toString())
                        ?: PlaylistRepository.getAnyCustomTitleForUri(uri.toString())
                } else {
                    null
                }
                val displayName = titleAlias ?: entry.name
                val usesLongPressSelection = selectionOnLongPress && isPrompter

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isExplorerMode) {
                                    Modifier
                                } else {
                                    Modifier
                                        .padding(vertical = 3.dp)
                                        .background(cardBg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) accent else rowBorder,
                                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                        )
                                }
                            )
                            .then(
                                if (usesLongPressSelection) {
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (selectionMode) onToggleSelect(uri)
                                            else if (canOpenPlayer) onOpenPlayer(uri)
                                            else if (canImportBackupJson && isJson) onImportBackupJson(uri)
                                            else if (isLrc) onOpenLrcEditor(uri)
                                        },
                                        onLongClick = { onToggleSelect(uri) }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = if (isExplorerMode) 8.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (usesLongPressSelection && selectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelect(uri) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accent,
                                    uncheckedColor = Color.White.copy(alpha = 0.7f),
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                        } else if (!usesLongPressSelection) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onToggleSelect(uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) Text("✕", color = accent, fontSize = 13.sp)
                            }

                            Spacer(Modifier.width(10.dp))
                        }

                        if (isExplorerMode && !isPrompter) {
                            Icon(
                                Icons.Default.MusicNote,
                                null,
                                tint = fileTypeTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (usesLongPressSelection) {
                                        Modifier
                                    } else {
                                        Modifier.clickable {
                                            if (selectionMode) onToggleSelect(uri)
                                            else if (canOpenPlayer) onOpenPlayer(uri)
                                            else if (canImportBackupJson && isJson) onImportBackupJson(uri)
                                            else if (isLrc) onOpenLrcEditor(uri)
                                        }
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp
                                )
                            }

                        }

                        if (isPrompter) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📝",
                                    color = accent,
                                    fontSize = 16.sp
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (selectionMode) onToggleSelect(uri)
                                    else if (canPlay) onQuickPlay(uri)
                                    else if (isSmp) onOpenPlayer(uri)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.library_list_cd_play),
                                    tint = if (canPlay || isSmp) accent else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (!usesLongPressSelection || !selectionMode) Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {

                                if (isPrompter) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.library_list_assign_to_playlist), color = Color.White)
                                        },
                                        onClick = { menuOpen = false; onAssignOne(uri) }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.quickplaylists_edit_prompter_title), color = Color.White)
                                        },
                                        onClick = { menuOpen = false; onRenameOne(entry) }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.library_list_delete_permanently),
                                                color = Color(0xFFFF6464)
                                            )
                                        },
                                        onClick = { menuOpen = false; onDeleteOne(uri) }
                                    )
                                } else {
                                    if (isLrc) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.library_list_edit_lrc), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onOpenLrcEditor(uri) }
                                        )
                                    }

                                    if (isConvertibleToSmp) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.library_list_convert_to_smp), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onConvertOneToSmp(uri) }
                                        )
                                    }

                                    if (isJson && canImportBackupJson) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.library_list_import_backup), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onImportBackupJson(uri) }
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.library_list_assign_to_playlist), color = Color.White)
                                        },
                                        onClick = { menuOpen = false; onAssignOne(uri) }
                                    )
                                    if (isSmp) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.backup_share), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onShareOne(uri) }
                                        )
                                    }
                                    if (!isSmp) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.library_list_copy_to_folder), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onCopyOne(uri) }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.library_list_move_to_folder), color = Color.White)
                                            },
                                            onClick = { menuOpen = false; onMoveOne(uri) }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.library_delete_action),
                                                    color = Color(0xFFFF6464)
                                                )
                                            },
                                            onClick = { menuOpen = false; onDeleteOne(uri) }
                                        )
                                    }
                                    if (canPlay || isSmp) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.library_list_rename), color = Color.White) },
                                            onClick = { menuOpen = false; onRenameOne(entry) }
                                        )
                                    }

                                    if (isSmp) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.library_delete_action),
                                                    color = Color(0xFFFF6464)
                                                )
                                            },
                                            onClick = { menuOpen = false; onDeleteOne(uri) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isExplorerMode) {
                        HorizontalDivider(color = rowBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
