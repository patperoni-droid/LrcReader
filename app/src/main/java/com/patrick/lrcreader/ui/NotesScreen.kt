package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

private enum class NotesUiMode {
    LIST,
    EDIT
}

/**
 * Bloc-notes multi-notes :
 * - Liste des notes
 * - Édition d'une note
 * - Attribution d'une note à une playlist (prompteur://id)
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onClose: () -> Unit
) {
    var mode by remember { mutableStateOf(NotesUiMode.LIST) }

    var notes by remember { mutableStateOf<List<NotesRepository.Note>>(emptyList()) }

    // Note en cours d'édition (écran EDIT)
    var editingNoteId by remember { mutableStateOf<Long?>(null) }
    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }

    // Menu "…" dans l'écran d'édition
    var editMenuOpen by remember { mutableStateOf(false) }

    // Attribution à une playlist (depuis LIST ou EDIT)
    var showAssignDialog by remember { mutableStateOf(false) }
    var allPlaylists by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedPlaylistForAssign by remember { mutableStateOf<String?>(null) }
    var assignNoteId by remember { mutableStateOf<Long?>(null) }

    // Chargement initial des notes
    LaunchedEffect(Unit) {
        notes = NotesRepository.getAll(context)
    }

    fun refreshNotes() {
        notes = NotesRepository.getAll(context)
    }

    fun openNewNote() {
        editingNoteId = null
        titleText = ""
        contentText = ""
        mode = NotesUiMode.EDIT
    }

    fun openExistingNote(note: NotesRepository.Note) {
        editingNoteId = note.id.takeIf { it > 0 }
        titleText = note.title
        contentText = note.content
        mode = NotesUiMode.EDIT
    }

    fun saveCurrentNote() {
        val title = titleText.ifBlank { "Sans titre" }
        val content = contentText

        val id = NotesRepository.upsert(
            context = context,
            id = editingNoteId,
            title = title,
            content = content
        )
        editingNoteId = id
        refreshNotes()
        // Après sauvegarde → retour à la liste (comme tu voulais)
        mode = NotesUiMode.LIST
    }

    fun deleteCurrentNote() {
        val id = editingNoteId ?: return
        NotesRepository.delete(context, id)
        refreshNotes()
        mode = NotesUiMode.LIST
    }

    DarkBlueGradientBackground {
        when (mode) {

            NotesUiMode.LIST -> {
                // ─────────────────────────────
                //  ÉCRAN LISTE DES NOTES
                // ─────────────────────────────
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Fermer",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "Mes notes",
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        }

                        IconButton(onClick = { openNewNote() }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Nouvelle note",
                                tint = Color(0xFF81C784)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucune note.\nAppuie sur + pour en créer une.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes, key = { it.id }) { note ->
                                var rowMenuOpen by remember { mutableStateOf(false) }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = Color(0x22000000),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .clickable { openExistingNote(note) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = if (note.title.isBlank()) "Sans titre" else note.title,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (note.content.isNotBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = note.content,
                                                        color = Color(0xFFB0BEC5),
                                                        fontSize = 13.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Box {
                                                IconButton(
                                                    onClick = { rowMenuOpen = true }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.MoreVert,
                                                        contentDescription = "Options note",
                                                        tint = Color.White
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = rowMenuOpen,
                                                    onDismissRequest = { rowMenuOpen = false },
                                                    modifier = Modifier.background(Color(0xFF222222))
                                                ) {
                                                    // Renommer = ouvrir en édition
                                                    DropdownMenuItem(
                                                        text = { Text("Renommer / éditer", color = Color.White) },
                                                        onClick = {
                                                            rowMenuOpen = false
                                                            openExistingNote(note)
                                                        }
                                                    )

                                                    DropdownMenuItem(
                                                        text = { Text("Attribuer à une playlist", color = Color.White) },
                                                        onClick = {
                                                            rowMenuOpen = false

                                                            // On s'assure que la note a un id valide
                                                            val fixed = NotesRepository.ensureValidId(context, note)
                                                            assignNoteId = fixed.id

                                                            // On recharge la liste locale avec la version fixée
                                                            refreshNotes()

                                                            allPlaylists = PlaylistRepository.getPlaylists()
                                                            selectedPlaylistForAssign = allPlaylists.firstOrNull()
                                                            showAssignDialog = allPlaylists.isNotEmpty()
                                                        }
                                                    )

                                                    DropdownMenuItem(
                                                        text = { Text("Supprimer", color = Color(0xFFFF6F6F)) },
                                                        onClick = {
                                                            rowMenuOpen = false
                                                            NotesRepository.delete(context, note.id)
                                                            refreshNotes()
                                                        }
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
            }

            NotesUiMode.EDIT -> {
                // ─────────────────────────────
                //  ÉCRAN ÉDITION NOTE
                // ─────────────────────────────
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                // retour à la liste
                                mode = NotesUiMode.LIST
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Retour",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = if (editingNoteId == null) "Nouvelle note" else "Modifier la note",
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { saveCurrentNote() }) {
                                Text("Enregistrer", color = Color(0xFF81C784))
                            }

                            Box {
                                IconButton(onClick = { editMenuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Menu note",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = editMenuOpen,
                                    onDismissRequest = { editMenuOpen = false },
                                    modifier = Modifier.background(Color(0xFF222222))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Attribuer à une playlist", color = Color.White) },
                                        onClick = {
                                            editMenuOpen = false
                                            if (editingNoteId != null) {
                                                allPlaylists = PlaylistRepository.getPlaylists()
                                                selectedPlaylistForAssign = allPlaylists.firstOrNull()
                                                assignNoteId = editingNoteId
                                                showAssignDialog = allPlaylists.isNotEmpty()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Supprimer la note", color = Color(0xFFFF6F6F)) },
                                        onClick = {
                                            editMenuOpen = false
                                            if (editingNoteId != null) {
                                                deleteCurrentNote()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Titre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        label = { Text("Texte") },
                        modifier = Modifier
                            .fillMaxSize(),
                        minLines = 8
                    )
                }
            }
        }

        // ─────────────────────────────
        //  DIALOG ATTRIBUTION PLAYLIST
        // ─────────────────────────────
        if (showAssignDialog && assignNoteId != null) {
            AlertDialog(
                onDismissRequest = {
                    showAssignDialog = false
                    assignNoteId = null
                },
                title = { Text("Attribuer à une playlist", color = Color.White) },
                text = {
                    Column {
                        if (allPlaylists.isEmpty()) {
                            Text(
                                "Aucune playlist disponible.",
                                color = Color.Gray
                            )
                        } else {
                            allPlaylists.forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPlaylistForAssign = name }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isSelected = name == selectedPlaylistForAssign
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                if (isSelected) Color(0xFF81C784) else Color.Transparent,
                                                RoundedCornerShape(50)
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color(0xFF81C784) else Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val noteId = assignNoteId
                        val playlistName = selectedPlaylistForAssign
                        if (noteId != null && !playlistName.isNullOrBlank()) {
                            val note = NotesRepository.get(context, noteId)
                            if (note != null) {
                                // On s'assure ENCORE que l'id est valide (au cas où ça vienne d'une vieille note)
                                val fixed = NotesRepository.ensureValidId(context, note)
                                val uri = "prompter://${fixed.id}"
                                PlaylistRepository.assignSongToPlaylist(playlistName, uri)
                            }
                        }
                        showAssignDialog = false
                        assignNoteId = null
                    }) {
                        Text("OK", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAssignDialog = false
                        assignNoteId = null
                    }) {
                        Text("Annuler", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}