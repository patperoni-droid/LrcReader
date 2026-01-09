package com.patrick.lrcreader.ui.library

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.LibraryEntry

@Composable
fun LibraryList(
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    filteredEntries: List<LibraryEntry>,
    selectedSongs: Set<Uri>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onOpenFolder: (LibraryEntry) -> Unit,
    onPlay: (Uri) -> Unit,
    onAssignRequest: (Uri) -> Unit,
    onMoveRequest: (Uri) -> Unit,
    onRenameRequest: (LibraryEntry) -> Unit,
    onDeleteRequest: (Uri) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(min = 44.dp),
            placeholder = { Text("Rechercher…") },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            items(filteredEntries, key = { it.uri.toString() }) { entry ->
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
                    var menuOpen by remember(uri.toString()) { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(cardBg, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isSelected) accent else rowBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        Text(
                            entry.name,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (selectedSongs.isNotEmpty()) onToggleSelect(uri)
                                    else onPlay(uri)
                                }
                        )

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Attribuer à une playlist", color = Color.White) },
                                    onClick = { menuOpen = false; onAssignRequest(uri) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Déplacer vers un dossier", color = Color.White) },
                                    onClick = { menuOpen = false; onMoveRequest(uri) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Renommer", color = Color.White) },
                                    onClick = { menuOpen = false; onRenameRequest(entry) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer définitivement", color = Color(0xFFFF6464)) },
                                    onClick = { menuOpen = false; onDeleteRequest(uri) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}