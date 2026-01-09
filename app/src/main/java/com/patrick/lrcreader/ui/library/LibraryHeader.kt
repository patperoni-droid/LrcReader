package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.platform.LocalContext

@Composable
fun LibraryHeader(
    titleColor: Color,
    subtitleColor: Color,
    currentFolderUri: Uri?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onPickRoot: () -> Unit,
    onRescan: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var actionsExpanded by remember { mutableStateOf(false) }

    val folderName = remember(currentFolderUri) {
        currentFolderUri?.let {
            (DocumentFile.fromTreeUri(context, it)
                ?: DocumentFile.fromSingleUri(context, it))?.name
        } ?: "Aucun dossier sélectionné"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Bibliothèque", color = titleColor, fontSize = 20.sp)
            Text(folderName, color = subtitleColor, fontSize = 11.sp)
        }

        IconButton(onClick = { actionsExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.White)
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { actionsExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Choisir dossier Music") },
                onClick = { actionsExpanded = false; onPickRoot() }
            )
            DropdownMenuItem(
                text = { Text("Rescan bibliothèque") },
                onClick = { actionsExpanded = false; onRescan() }
            )
            DropdownMenuItem(
                text = { Text("Oublier le dossier") },
                onClick = { actionsExpanded = false; onForget() }
            )
        }
    }
}