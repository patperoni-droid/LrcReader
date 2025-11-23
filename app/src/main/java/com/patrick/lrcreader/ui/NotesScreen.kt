package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesPrefs

/**
 * Bloc-notes simple avec fond gris foncé (sans dégradé)
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onClose: () -> Unit
) {
    // Charge le texte
    var noteText by rememberSaveable { mutableStateOf(NotesPrefs.get(context)) }

    // 👉 FOND GRIS FONCÉ UNIFORME
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)) // <<< NOUVEAU fond gris foncé
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 10.dp
            )
    ) {

        // ---------- HEADER ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("← Retour", color = Color(0xFFEEEEEE))
            }

            Text(
                text = "Bloc-notes",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    noteText = ""
                    NotesPrefs.clear(context)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Effacer tout",
                    tint = Color(0xFFFF8A80)
                )
            }
        }


        // ---------- ZONE TEXTE ----------
        OutlinedTextField(
            value = noteText,
            onValueChange = { new ->
                noteText = new
                NotesPrefs.save(context, new) // autosave
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontSize = 14.sp
            ),
            label = {
                Text("Tes notes pour les lives", color = Color(0xFFB0BEC5))
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Fermer", fontSize = 13.sp)
        }
    }
}