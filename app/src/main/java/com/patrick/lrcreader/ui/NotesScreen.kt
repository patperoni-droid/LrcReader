package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/**
 * Bloc-notes multi-notes :
 * - Écran 1 : liste des notes
 * - Écran 2 : édition d’une note
 *
 * Depuis la liste :
 * - clic sur la note → ouvrir l’édition
 * - menu (3 points) → Attribuer à / Renommer / Supprimer
 *
 * Quand on ENREGISTRE → retour automatique à la liste.
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onClose: () -> Unit
) {
    // Liste complète des notes
    var notes by remember { mutableStateOf(NotesRepository.getAll(context)) }

    // null = on est sur la liste
    // -1L = nouvelle note
    // autre valeur = édition d’une note existante
    var editingId by remember { mutableStateOf<Long?>(null) }

    // États d’édition
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    // Dialog de suppression
    var noteToDelete by remember { mutableStateOf<NotesRepository.Note?>(null) }

    // À brancher plus tard pour "Attribuer à…"
    fun assignNote(note: NotesRepository.Note) {
        // TODO : ouvrir un dialog de playlists et créer un prompteur
    }

    DarkBlueGradientBackground {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                // Bouton "nouvelle note" uniquement quand on est sur la liste
                if (editingId == null) {
                    FloatingActionButton(
                        onClick = {
                            // 👉 on passe en mode "nouvelle note"
                            editingId = -1L
                            editTitle = ""
                            editContent = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nouvelle note"
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp)
            ) {
                // HEADER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (editingId == null) "Mes notes" else "Éditer la note",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (editingId == null) {
                    // ─────────────────────────────────────
                    //   MODE LISTE
                    // ─────────────────────────────────────
                    if (notes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucune note.\nAppuie sur + pour en créer une.",
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                NoteRow(
                                    note = note,
                                    onClick = {
                                        // ouvrir en édition
                                        editingId = note.id
                                        editTitle = note.title
                                        editContent = note.content
                                    },
                                    onAssign = { assignNote(note) },
                                    onRename = {
                                        editingId = note.id
                                        editTitle = note.title
                                        editContent = note.content
                                    },
                                    onDelete = {
                                        noteToDelete = note
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                } else {
                    // ─────────────────────────────────────
                    //   MODE ÉDITION (nouvelle ou existante)
                    // ─────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Titre") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            label = { Text("Contenu de la note") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    // Annuler → retour à la liste
                                    editingId = null
                                }
                            ) {
                                Text("Annuler")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val title = if (editTitle.isBlank()) "Sans titre" else editTitle

                                    // 👉 si editingId == -1L → création
                                    val idForUpsert: Long? =
                                        if (editingId == -1L) null else editingId

                                    NotesRepository.upsert(
                                        context = context,
                                        id = idForUpsert,
                                        title = title,
                                        content = editContent
                                    )

                                    // Recharge la liste
                                    notes = NotesRepository.getAll(context)

                                    // Retour automatique à la liste
                                    editingId = null
                                    editTitle = ""
                                    editContent = ""
                                }
                            ) {
                                Text("Enregistrer")
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────
            //  DIALOG SUPPRESSION
            // ─────────────────────────────────────
            noteToDelete?.let { n ->
                AlertDialog(
                    onDismissRequest = { noteToDelete = null },
                    title = { Text("Supprimer cette note ?") },
                    text = { Text(n.title.ifBlank { "Sans titre" }) },
                    confirmButton = {
                        TextButton(onClick = {
                            NotesRepository.delete(context, n.id)
                            notes = NotesRepository.getAll(context)
                            noteToDelete = null
                            if (editingId == n.id) {
                                editingId = null
                                editTitle = ""
                                editContent = ""
                            }
                        }) {
                            Text("Supprimer")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { noteToDelete = null }) {
                            Text("Annuler")
                        }
                    }
                )
            }
        }
    }
}

/* ---------------------------------------------------------------- */
/*  LIGNE D’UNE NOTE                                                */
/* ---------------------------------------------------------------- */

@Composable
private fun NoteRow(
    note: NotesRepository.Note,
    onClick: () -> Unit,
    onAssign: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF101624))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = note.title.ifBlank { "Sans titre" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.content
                            .replace("\n", " ")
                            .take(80)
                            .let { if (it.length == 80) "$it…" else it },
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        maxLines = 2
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Attribuer à…") },
                            onClick = {
                                menuOpen = false
                                onAssign()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Renommer / éditer") },
                            onClick = {
                                menuOpen = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}