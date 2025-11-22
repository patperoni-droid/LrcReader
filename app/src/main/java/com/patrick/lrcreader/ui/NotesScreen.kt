package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.NotesPrefs
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/**
 * Bloc-notes simple :
 * - 1 page de texte
 * - Sauvegarde automatique dans NotesPrefs
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onClose: () -> Unit
) {
    // On charge le texte une seule fois au début
    var noteText by rememberSaveable { mutableStateOf(NotesPrefs.get(context)) }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
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

            Text(
                text = "Note tes idées de titres, ton matos à ne pas oublier, etc. " +
                        "Le texte est sauvegardé automatiquement.",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ---------- ZONE TEXTE ----------
            OutlinedTextField(
                value = noteText,
                onValueChange = { new ->
                    noteText = new
                    NotesPrefs.save(context, new)   // autosave
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

            // Petit bouton "OK" si tu veux fermer sans revenir en arrière
            Button(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.End)
            ) {
                Text("Fermer", fontSize = 13.sp)
            }
        }
    }
}