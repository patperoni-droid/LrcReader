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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.exo.R
import java.io.File

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
    onImportBackingTracks: () -> Unit,
    onConvertFolderToSmp: () -> Unit,
    onImportSmp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var actionsExpanded by remember { mutableStateOf(false) }
    val sNoFolderSelected = stringResource(R.string.library_no_folder_selected)
    val sPrompter = stringResource(R.string.main_menu_prompter)
    val sSmpFolder = stringResource(R.string.library_smp_folder)

    val folderName = remember(currentFolderUri, sNoFolderSelected, sPrompter, sSmpFolder) {
        currentFolderUri?.let { u ->
            when (u.scheme) {
                "spl-prompter" -> sPrompter
                "spl-smp" -> sSmpFolder
                "file" -> File(u.path ?: "").name.ifBlank { "SPL_Music" }
                else -> (DocumentFile.fromTreeUri(context, u)
                    ?: DocumentFile.fromSingleUri(context, u))?.name ?: "SPL_Music"
            }
        } ?: sNoFolderSelected
    }

    val hasRoot = currentFolderUri != null
    val canConvertFolder = currentFolderUri != null &&
        currentFolderUri.scheme != "spl-prompter" &&
        currentFolderUri.scheme != "spl-smp"

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
                    contentDescription = stringResource(R.string.library_header_cd_back),
                    tint = Color.White
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.library_title), color = titleColor, fontSize = 20.sp)
            Text(folderName, color = subtitleColor, fontSize = 11.sp)
        }

        IconButton(onClick = { actionsExpanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.library_header_cd_actions),
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { actionsExpanded = false }
        ) {

            // ✅ LE TRUC QUI MANQUAIT : choisir (ou rechanger) le dossier racine
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_choose_folder)) },
                onClick = {
                    actionsExpanded = false
                    onPickRoot()
                }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_rescan)) },
                enabled = hasRoot,
                onClick = { actionsExpanded = false; onRescan() }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_import_music)) },
                enabled = hasRoot,
                onClick = { actionsExpanded = false; onImportBackingTracks() }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_convert_folder_to_smp)) },
                enabled = canConvertFolder,
                onClick = { actionsExpanded = false; onConvertFolderToSmp() }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_import_smp)) },
                enabled = hasRoot,
                onClick = { actionsExpanded = false; onImportSmp() }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_header_forget_folder)) },
                enabled = hasRoot,
                onClick = { actionsExpanded = false; onForget() }
            )
        }
    }
}
