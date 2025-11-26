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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onClose: () -> Unit
) {
    val ctx = context

    var notes by remember { mutableStateOf(NotesRepository.getAll(ctx)) }

    // note en cours d’édition (null = mode liste)
    var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editContent by rememberSaveable { mutableStateOf("") }

    // pour le menu ⋮
    var menuForNoteId by remember { mutableStateOf<Long?>(null) }

    // dialog "Attribuer à une playlist"
    var assignDialogNote by remember { mutableStateOf<NotesRepository.Note?>(null) }
    var selectedPlaylistForAssign by remember { mutableStateOf<String?>(null) }

    // pour suivre les changements de playlists (couleur / création / rename...)
    val playlistVersion by PlaylistRepository.version
    val playlists = remember(playlistVersion) {
        PlaylistRepository.getPlaylists()
    }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // ── HEADER ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Fermer",
                        tint = Color.White
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = if (editingNoteId == null) "Mes notes" else "Éditer la note",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                // Bouton + (uniquement en mode liste)
                if (editingNoteId == null) {
                    IconButton(
                        onClick = {
                            editingNoteId = null
                            editTitle = ""
                            editContent = ""
                            // on passe en mode édition vide (nouvelle note)
                            editingNoteId = -1L // id spécial = nouvelle note
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nouvelle note",
                            tint = Color(0xFF81C784)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── CONTENU : soit liste, soit éditeur ─────────────────
            if (editingNoteId == null) {
                // MODE LISTE
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune note.\nAppuie sur + pour en créer une.",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteRow(
                                note = note,
                                onClick = {
                                    // ouvrir en édition
                                    editingNoteId = note.id
                                    editTitle = note.title
                                    editContent = note.content
                                },
                                onOpenMenu = {
                                    menuForNoteId = note.id
                                },
                                isMenuOpen = (menuForNoteId == note.id),
                                onDismissMenu = { menuForNoteId = null },
                                onMenuAttribuer = {
                                    menuForNoteId = null
                                    assignDialogNote = note
                                    selectedPlaylistForAssign =
                                        playlists.firstOrNull()
                                },
                                onMenuEdit = {
                                    menuForNoteId = null
                                    editingNoteId = note.id
                                    editTitle = note.title
                                    editContent = note.content
                                },
                                onMenuDelete = {
                                    menuForNoteId = null
                                    NotesRepository.delete(ctx, note.id)
                                    notes = NotesRepository.getAll(ctx)
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            } else {
                // MODE ÉDITEUR
                EditNoteContent(
                    title = editTitle,
                    content = editContent,
                    onTitleChange = { editTitle = it },
                    onContentChange = { editContent = it },
                    onSave = {
                        val idToUse = if (editingNoteId == -1L) null else editingNoteId
                        NotesRepository.upsert(ctx, idToUse, editTitle, editContent)
                        notes = NotesRepository.getAll(ctx)
                        // retour auto à la liste
                        editingNoteId = null
                        editTitle = ""
                        editContent = ""
                    },
                    onCancel = {
                        editingNoteId = null
                        editTitle = ""
                        editContent = ""
                    }
                )
            }
        }
    }

    // ── DIALOG : ATTRIBUER À UNE PLAYLIST ─────────────────────────
    if (assignDialogNote != null) {
        val note = assignDialogNote!!

        AlertDialog(
            onDismissRequest = {
                assignDialogNote = null
            },
            title = {
                Text(
                    text = "Attribuer à une playlist",
                    color = Color.White
                )
            },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "Aucune playlist disponible.\nCrée une playlist dans l’onglet \"Toutes\".",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Choisis la playlist pour cette note.\nElle sera lue en mode prompteur.",
                            color = Color(0xFFCFD8DC),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        // petit “pseudo dropdown” simple (liste de boutons)
                        playlists.forEach { plName ->
                            val selected = plName == selectedPlaylistForAssign
                            TextButton(
                                onClick = { selectedPlaylistForAssign = plName }
                            ) {
                                Text(
                                    text = if (selected) "▶ $plName" else plName,
                                    color = if (selected) Color(0xFFE86FFF) else Color.White
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = playlists.isNotEmpty() && selectedPlaylistForAssign != null,
                    onClick = {
                        val playlistName = selectedPlaylistForAssign ?: return@TextButton

                        // 1) prompterId : on réutilise si déjà stocké, sinon on crée
                        val existingPrompterId = note.prompterId
                        val prompterId = existingPrompterId ?: TextSongRepository.create(
                            context = ctx,
                            title = note.title.ifBlank { "Note ${note.id}" },
                            content = note.content
                        )

                        // 2) on mémorise le lien dans la note
                        NotesRepository.setPrompterId(ctx, note.id, prompterId)
                        notes = NotesRepository.getAll(ctx)

                        // 3) on ajoute dans la playlist comme titre "prompteur"
                        val uri = "prompter://$prompterId"
                        PlaylistRepository.assignSongToPlaylist(playlistName, uri)

                        assignDialogNote = null
                    }
                ) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { assignDialogNote = null }) {
                    Text("Annuler", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

/* ------------------------------------------------------------------ */
/*  ROW D’UNE NOTE                                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun NoteRow(
    note: NotesRepository.Note,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    isMenuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onMenuAttribuer: () -> Unit,
    onMenuEdit: () -> Unit,
    onMenuDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101421)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (note.title.isNotBlank()) note.title else "(Sans titre)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.content
                        .lineSequence()
                        .firstOrNull()
                        ?.take(80)
                        ?.let { if (it.length == 80) "$it…" else it }
                        ?: "",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    maxLines = 1
                )
                if (note.prompterId != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Attribuée à une playlist (prompteur)",
                        color = Color(0xFFE86FFF),
                        fontSize = 11.sp
                    )
                }
            }

            Box {
                IconButton(onClick = onOpenMenu) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    DropdownMenuItem(
                        text = { Text("Attribuer à une playlist", color = Color.White) },
                        onClick = onMenuAttribuer
                    )
                    DropdownMenuItem(
                        text = { Text("Renommer / Modifier", color = Color.White) },
                        onClick = onMenuEdit
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = Color(0xFFFF6F6F)) },
                        onClick = onMenuDelete
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  ÉDITEUR DE NOTE                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun EditNoteContent(
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Titre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text(text = "Texte de la note / paroles") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 8
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Annuler", color = Color(0xFFB0BEC5))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSave) {
                Text("Enregistrer", color = Color(0xFFE86FFF))
            }
        }
    }
}