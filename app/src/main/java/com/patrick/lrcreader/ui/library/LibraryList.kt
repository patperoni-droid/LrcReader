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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.LibraryEntry

private fun isPlayableByName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac") ||
            n.endsWith(".flac") || n.endsWith(".ogg") ||
            n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
            n.endsWith(".mov") || n.endsWith(".avi")
}

private fun isJsonByName(name: String): Boolean = name.lowercase().endsWith(".json")
private fun isLrcByName(name: String): Boolean = name.lowercase().endsWith(".lrc")

@Composable
fun LibraryList(
    entries: List<LibraryEntry>,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
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

    onAssignOne: (Uri) -> Unit,
    onMoveOne: (Uri) -> Unit,
    onRenameOne: (LibraryEntry) -> Unit,
    onDeleteOne: (Uri) -> Unit
) {
    val selectionMode = selectedSongs.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        items(entries, key = { it.uri.toString() }) { entry ->

            if (entry.isDirectory) {
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
                    Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(entry.name, color = Color.White, fontSize = 15.sp)
                }

            } else {
                val uri = entry.uri
                val isSelected = selectedSongs.contains(uri)
                var menuOpen by remember { mutableStateOf(false) }

                val canPlay = isPlayableByName(entry.name)
                val isJson = isJsonByName(entry.name)
                val isLrc = isLrcByName(entry.name)

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
                    Text(
                        text = entry.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (selectionMode) onToggleSelect(uri)
                                else if (canPlay) onOpenPlayer(uri)
                                else if (isLrc) onOpenLrcEditor(uri)
                            }
                    )

                    // ▶️ bouton PLAY : quick play UNIQUEMENT si media
                    IconButton(
                        onClick = {
                            if (selectionMode) onToggleSelect(uri)
                            else if (canPlay) onQuickPlay(uri)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lire (bibliothèque)",
                            tint = if (canPlay) accent else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // menu ⋮
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {

                            // ✅ uniquement pour les .lrc
                            if (isLrc) {
                                DropdownMenuItem(
                                    text = { Text("Éditer ce .lrc", color = Color.White) },
                                    onClick = { menuOpen = false; onOpenLrcEditor(uri) }
                                )
                            }

                            // ✅ uniquement pour les .json
                            if (isJson) {
                                DropdownMenuItem(
                                    text = { Text("Importer ce backup", color = Color.White) },
                                    onClick = { menuOpen = false; onImportBackupJson(uri) }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Attribuer à une playlist", color = Color.White) },
                                onClick = { menuOpen = false; onAssignOne(uri) }
                            )
                            DropdownMenuItem(
                                text = { Text("Déplacer vers un dossier", color = Color.White) },
                                onClick = { menuOpen = false; onMoveOne(uri) }
                            )
                            DropdownMenuItem(
                                text = { Text("Renommer", color = Color.White) },
                                onClick = { menuOpen = false; onRenameOne(entry) }
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer définitivement", color = Color(0xFFFF6464)) },
                                onClick = { menuOpen = false; onDeleteOne(uri) }
                            )
                        }
                    }
                }
            }
        }
    }
}