package com.patrick.lrcreader.ui.library

import androidx.compose.ui.unit.Dp
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.patrick.lrcreader.ui.LibraryEntry

@Composable
fun LibraryHeader(
    titleColor: Color,
    subtitleColor: Color,
    currentFolderName: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    actionsExpanded: Boolean,
    onActionsExpanded: (Boolean) -> Unit,
    onPickRoot: () -> Unit,
    onRescan: () -> Unit,
    onForgetRoot: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Bibliothèque", color = titleColor, fontSize = 20.sp)
            Text(currentFolderName, color = subtitleColor, fontSize = 11.sp)
        }

        IconButton(onClick = { onActionsExpanded(true) }) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White)
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { onActionsExpanded(false) }
        ) {
            DropdownMenuItem(
                text = { Text("Choisir dossier Music") },
                onClick = { onActionsExpanded(false); onPickRoot() }
            )
            DropdownMenuItem(
                text = { Text("Rescan bibliothèque") },
                onClick = { onActionsExpanded(false); onRescan() }
            )
            DropdownMenuItem(
                text = { Text("Oublier le dossier") },
                onClick = { onActionsExpanded(false); onForgetRoot() }
            )
        }
    }
}

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
    onPlay: (Uri) -> Unit,
    onAssignOne: (Uri) -> Unit,
    onMoveOne: (Uri) -> Unit,
    onRenameOne: (LibraryEntry) -> Unit,
    onDeleteOne: (Uri) -> Unit
) {
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(cardBg, RoundedCornerShape(10.dp))
                        .border(1.dp, if (isSelected) accent else rowBorder, RoundedCornerShape(10.dp))
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
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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

@Composable
fun LibraryBottomBar(
    bottomBarHeight: Dp,
    selectedCount: Int,
    onAssign: () -> Unit,
    onClear: () -> Unit
) {
    BottomAppBar(
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .height(bottomBarHeight)
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(28.dp)
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(selectedCount.toString(), color = Color.White, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onAssign) { Text("Attribuer", color = Color.White) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClear) { Text("Désélect.", color = Color(0xFFB0B0B0)) }
    }
}

@Composable
fun LibraryLoadingOverlay(
    isLoading: Boolean,
    moveProgress: Float?,
    moveLabel: String?
) {
    if (!isLoading) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val p = moveProgress
            if (p == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.72f).height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(moveLabel ?: "Déplacement…", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            } else {
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(0.72f).height(10.dp)
                )
                Spacer(Modifier.height(12.dp))
                val pct = (p.coerceIn(0f, 1f) * 100f).roundToInt()
                Text(
                    (moveLabel ?: "Copie…") + " $pct%",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp
                )
            }
        }
    }
}