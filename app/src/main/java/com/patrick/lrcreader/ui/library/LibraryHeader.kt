package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
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

private fun isLegacyBackingTracksFolderName(name: String): Boolean {
    val normalized = name.trim()
    return normalized.equals("BackingTracks", ignoreCase = true) ||
        normalized.equals("BackingTrack", ignoreCase = true)
}

@Composable
fun LibraryHeader(
    titleColor: Color,
    subtitleColor: Color,
    currentFolderUri: Uri?,
    folderNameOverride: String? = null,
    isFilesViewMode: Boolean,
    showActions: Boolean = true,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    onPickRoot: () -> Unit,
    onRescan: () -> Unit,
    onForget: () -> Unit,
    onImportBackingTracks: () -> Unit,
    onConvertFolderToSmp: () -> Unit,
    onImportSmp: () -> Unit,
    selectionCount: Int = 0,
    onCopySelection: (() -> Unit)? = null,
    onMoveSelection: (() -> Unit)? = null,
    onDeleteSelection: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onSecretMultiTap: (() -> Unit)? = null,
    compactActionsOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var actionsExpanded by remember { mutableStateOf(false) }
    val sNoFolderSelected = stringResource(R.string.library_no_folder_selected)
    val sPrompter = stringResource(R.string.main_menu_prompter)
    val sSmpFolder = stringResource(R.string.library_smp_folder)
    val sLegacyBackingTracksFolder = stringResource(R.string.library_legacy_backing_tracks_folder)
    val sFilesView = stringResource(R.string.library_view_mode_files)

    val folderName = remember(
        currentFolderUri,
        folderNameOverride,
        sNoFolderSelected,
        sPrompter,
        sSmpFolder,
        sLegacyBackingTracksFolder,
        sFilesView
    ) {
        folderNameOverride ?: (
        currentFolderUri?.let { u ->
            when (u.scheme) {
                "spl-prompter" -> sPrompter
                "spl-smp" -> sSmpFolder
                "spl-shared-audio" -> sharedAudioFolderDisplayName(u, sFilesView)
                "file" -> {
                    val rawName = File(u.path ?: "").name.ifBlank { "SPL_Music" }
                    if (isLegacyBackingTracksFolderName(rawName)) sLegacyBackingTracksFolder else rawName
                }
                else -> {
                    val rawName = (DocumentFile.fromTreeUri(context, u)
                        ?: DocumentFile.fromSingleUri(context, u))?.name ?: "SPL_Music"
                    if (isLegacyBackingTracksFolderName(rawName)) sLegacyBackingTracksFolder else rawName
                }
            }
        } ?: sNoFolderSelected)
    }

    val hasRoot = currentFolderUri != null
    val canConvertFolder = currentFolderUri != null &&
        currentFolderUri.scheme != "spl-prompter" &&
        currentFolderUri.scheme != "spl-smp"
    val isSelectionContext = selectionCount > 0 &&
        (onCopySelection != null || onMoveSelection != null || onDeleteSelection != null || onClearSelection != null)

    @Composable
    fun HeaderActionsMenu() {
        DropdownMenu(
            expanded = actionsExpanded,
            onDismissRequest = { actionsExpanded = false }
        ) {
            if (isSelectionContext) {
                if (onCopySelection != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_bottom_copy)) },
                        onClick = {
                            actionsExpanded = false
                            onCopySelection()
                        }
                    )
                }
                if (onMoveSelection != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_bottom_move)) },
                        onClick = {
                            actionsExpanded = false
                            onMoveSelection()
                        }
                    )
                }
                if (onDeleteSelection != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.library_bottom_delete),
                                color = Color(0xFFD32F2F)
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onDeleteSelection()
                        }
                    )
                }
                if (onClearSelection != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_bottom_clear_selection)) },
                        onClick = {
                            actionsExpanded = false
                            onClearSelection()
                        }
                    )
                }
            } else if (isFilesViewMode) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_header_rescan)) },
                    enabled = hasRoot,
                    onClick = { actionsExpanded = false; onRescan() }
                )
            } else {
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

    if (compactActionsOnly) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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

            IconButton(onClick = onOpenStorage) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.library_header_cd_storage),
                    tint = Color.White
                )
            }

            if (showActions) {
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.library_header_cd_actions),
                            tint = Color.White
                        )
                    }
                    HeaderActionsMenu()
                }
            }
        }
        return
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
                    contentDescription = stringResource(R.string.library_header_cd_back),
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onSecretMultiTap != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSecretMultiTap
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(stringResource(R.string.library_title), color = titleColor, fontSize = 20.sp)
            Text(folderName, color = subtitleColor, fontSize = 11.sp)
        }

        IconButton(onClick = onOpenStorage) {
            Icon(
                Icons.Default.Folder,
                contentDescription = stringResource(R.string.library_header_cd_storage),
                tint = Color.White
            )
        }

        if (showActions) {
            IconButton(onClick = { actionsExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.library_header_cd_actions),
                    tint = Color.White
                )
            }

            HeaderActionsMenu()
        }
    }
}
