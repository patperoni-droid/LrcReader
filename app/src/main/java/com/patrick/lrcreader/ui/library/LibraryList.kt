package com.patrick.lrcreader.ui.library

import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@Composable
fun LibraryList(
    entries: List<LibraryEntry>,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    canImportBackupJson: Boolean,
    selectedSongs: Set<Uri>,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        items(entries, key = { "$aliasVersion:${it.uri}" }) { entry ->
            if (entry.isDirectory) {
                val uri = entry.uri
                var menuOpen by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                        .clickable { onOpenFolder(entry) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, color = Color.White, fontSize = 15.sp)
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
                val titleAlias = if ((canPlay || isSmp) && !isPrompter) {
                    TitleAliasesStore.getTitleForTrack(context, uri.toString())
                        ?: PlaylistRepository.getAnyCustomTitleForUri(uri.toString())
                } else {
                    null
                }
                val displayName = titleAlias ?: entry.name

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, if (isSelected) accent else rowBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // carré select
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onToggleSelect(uri) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Text("✕", color = accent, fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(10.dp))

                    // clic sur le titre :
                    // - si sélection -> toggle
                    // - sinon -> ouvre lecteur UNIQUEMENT si media
                    // - sinon -> si .lrc -> ouvre éditeur
                    // - sinon (json/etc) -> rien
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (selectionMode) onToggleSelect(uri)
                                else if (canOpenPlayer) onOpenPlayer(uri)
                                else if (canImportBackupJson && isJson) onImportBackupJson(uri)
                                else if (isLrc) onOpenLrcEditor(uri)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isSmp) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2E7D32), shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SMP",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
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
                        // ▶️ bouton PLAY : quick play UNIQUEMENT si media
                        IconButton(
                            onClick = {
                                if (selectionMode) onToggleSelect(uri)
                                else if (canPlay) onQuickPlay(uri)
                                else if (isSmp) onOpenPlayer(uri)
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.library_list_cd_play),
                                tint = if (canPlay || isSmp) accent else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // menu ⋮
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
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
                                // ✅ uniquement pour les .lrc
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

                                // ✅ uniquement pour les .json
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
            }
        }
    }
}
