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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/**
 * Bloc-notes multi-notes.
 *
 * - Colonne gauche : liste des notes
 * - Colonne droite : éditeur (titre + contenu)
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onClose: () -> Unit
) {
    var notes by remember { mutableStateOf<List<NotesRepository.Note>>(emptyList()) }

    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }

    val contentScroll = rememberScrollState()

    fun reloadNotes() {
        notes = NotesRepository.getAll(context)
    }

    LaunchedEffect(Unit) {
        reloadNotes()
        // si tu veux ouvrir automatiquement la dernière modifiée :
        selectedNoteId = notes.firstOrNull()?.id
        notes.firstOrNull()?.let { note ->
            titleText = note.title
            contentText = note.content
        }
    }

    fun openNote(note: NotesRepository.Note) {
        selectedNoteId = note.id
        titleText = note.title
        contentText = note.content
    }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // HEADER
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
                    text = "Bloc-notes",
                    color = Color.White,
                    fontSize = 20.sp
                )

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        // nouvelle note vide
                        selectedNoteId = null
                        titleText = ""
                        contentText = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Nouvelle note",
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Nouvelle note")
                }
            }

            Spacer(Modifier.height(12.dp))

            // CONTENU : liste + éditeur
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // --- Colonne gauche : liste de notes ---
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Vos notes",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Text(
                            text = "Aucune note pour l’instant.\nCrée ta première note avec le bouton +.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                val isSelected = note.id == selectedNoteId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected)
                                                Color(0x33FFFFFF)
                                            else
                                                Color(0x22FFFFFF)
                                        )
                                        .clickable { openNote(note) }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = note.title.ifBlank { "Sans titre" },
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.content.isNotBlank()) {
                                        Text(
                                            text = note.content.replace("\n", " "),
                                            color = Color(0xFFB0BEC5),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // --- Colonne droite : éditeur ---
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Édition",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Titre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF050912), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        OutlinedTextField(
                            value = contentText,
                            onValueChange = { contentText = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(contentScroll),
                            label = { Text("Texte de la note") },
                            minLines = 8
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // bouton supprimer si une note existe
                        if (selectedNoteId != null) {
                            IconButton(
                                onClick = {
                                    selectedNoteId?.let { id ->
                                        NotesRepository.delete(context, id)
                                    }
                                    // reset éditeur
                                    selectedNoteId = null
                                    titleText = ""
                                    contentText = ""
                                    reloadNotes()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFFFF6F6F)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                val t = titleText.trim()
                                val c = contentText

                                if (selectedNoteId == null) {
                                    // création
                                    val created = NotesRepository.create(context, t, c)
                                    selectedNoteId = created.id
                                } else {
                                    // mise à jour
                                    selectedNoteId?.let { id ->
                                        NotesRepository.update(context, id, t, c)
                                    }
                                }

                                reloadNotes()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Sauvegarder",
                                tint = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Sauvegarder")
                        }
                    }
                }
            }
        }
    }
}