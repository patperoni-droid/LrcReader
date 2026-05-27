package com.patrick.lrcreader.ui

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.R
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

    fun shareCurrentNote() {
        val shareText = buildString {
            val title = titleText.trim()
            val content = contentText.trim()
            if (title.isNotEmpty()) {
                append(title)
            }
            if (title.isNotEmpty() && content.isNotEmpty()) {
                append("\n\n")
            }
            if (content.isNotEmpty()) {
                append(content)
            }
        }.trim()
        if (shareText.isBlank()) return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, titleText.trim().ifBlank {
                context.getString(R.string.notes_title)
            })
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.backup_share))
        )
    }

    fun shareAllNotes() {
        if (notes.isEmpty()) return
        val shareText = notes.joinToString(separator = "\n\n") { note ->
            buildString {
                val title = note.title.trim().ifBlank {
                    context.getString(R.string.common_untitled)
                }
                append(title)
                val content = note.content.trim()
                if (content.isNotEmpty()) {
                    append("\n")
                    append(content)
                }
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.notes_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.backup_share))
        )
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    DarkBlueGradientBackground {
        val canShareCurrentNote = titleText.isNotBlank() || contentText.isNotBlank()
        when (mode) {

            NotesUiMode.LIST -> {
                // ─────────────────────────────
                //  ÉCRAN LISTE DES NOTES
                // ─────────────────────────────
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = topInset + 12.dp,
                            bottom = 12.dp
                        )
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
                                    contentDescription = stringResource(R.string.common_cd_close),
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = stringResource(R.string.notes_title),
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { shareAllNotes() },
                                enabled = notes.isNotEmpty()
                            ) {
                                Text(stringResource(R.string.backup_share))
                            }
                            IconButton(onClick = { openNewNote() }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.common_cd_new_note),
                                    tint = Color(0xFF81C784)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.notes_empty),
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
                                                    text = if (note.title.isBlank()) stringResource(R.string.common_untitled) else note.title,
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
                                                        contentDescription = stringResource(R.string.common_cd_note_options),
                                                        tint = Color.White
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = rowMenuOpen,
                                                    onDismissRequest = { rowMenuOpen = false },
                                                    modifier = Modifier.background(Color(0xFF222222))
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.common_assign_to_playlist), color = Color.White) },
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
                                                        text = { Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF6F6F)) },
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
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = topInset + 6.dp,
                            bottom = 4.dp
                        )
                ) {
                    // HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    // retour à la liste
                                    mode = NotesUiMode.LIST
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_cd_back),
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = if (editingNoteId == null) {
                                    stringResource(R.string.notes_new_note_title)
                                } else {
                                    stringResource(R.string.notes_edit_note_title)
                                },
                                color = Color.White,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { shareCurrentNote() },
                                enabled = canShareCurrentNote,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.backup_share),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            // À terme : autosave pour supprimer le besoin d’un bouton Enregistrer.
                            TextButton(
                                onClick = { saveCurrentNote() },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.notes_save_short),
                                    color = Color(0xFF81C784),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { editMenuOpen = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.common_cd_note_options),
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = editMenuOpen,
                                    onDismissRequest = { editMenuOpen = false },
                                    modifier = Modifier.background(Color(0xFF222222))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_assign_to_playlist), color = Color.White) },
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
                                        text = { Text(stringResource(R.string.notes_delete_note), color = Color(0xFFFF6F6F)) },
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

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text(stringResource(R.string.common_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        label = { Text(stringResource(R.string.common_text_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        minLines = 4
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
                title = { Text(stringResource(R.string.common_assign_to_playlist), color = Color.White) },
                text = {
                    Column {
                        if (allPlaylists.isEmpty()) {
                            Text(
                                stringResource(R.string.notes_no_playlist_available),
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
                        Text(stringResource(R.string.common_ok), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAssignDialog = false
                        assignNoteId = null
                    }) {
                        Text(stringResource(R.string.common_cancel), color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}
