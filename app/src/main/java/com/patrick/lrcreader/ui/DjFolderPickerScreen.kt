package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.DjMediaFolderNode
import com.patrick.lrcreader.exo.R

@Composable
fun DjFolderPickerScreen(
    currentFolder: DjMediaFolderNode?,
    rootFolders: List<DjMediaFolderNode>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenFolder: (DjMediaFolderNode) -> Unit,
    onSelectFolder: (DjMediaFolderNode) -> Unit,
    onChooseManualFolder: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )
    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)

    val visibleFolders = currentFolder?.childFolders ?: rootFolders
    val title = currentFolder?.folderName ?: stringResource(R.string.dj_folder_picker_title)
    val pathLabel = currentFolder?.folderPath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.dj_cd_back),
                    tint = onBg
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = if (currentFolder == null) {
                        stringResource(R.string.dj_folder_picker_title)
                    } else {
                        title
                    },
                    color = onBg,
                    fontSize = 20.sp
                )
                if (!pathLabel.isNullOrBlank()) {
                    Text(
                        text = pathLabel,
                        color = sub,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (currentFolder != null && currentFolder.recursiveTracks.isNotEmpty()) {
                TextButton(onClick = { onSelectFolder(currentFolder) }) {
                    Text(stringResource(R.string.dj_folder_picker_play_this))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Text(
                text = stringResource(R.string.common_loading),
                color = sub,
                style = MaterialTheme.typography.bodyMedium
            )
            return
        }

        if (rootFolders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dj_folder_play_no_media_folders),
                    color = sub,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onChooseManualFolder) {
                    Text(stringResource(R.string.dj_folder_play_choose_manual))
                }
            }
            return
        }

        if (visibleFolders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dj_folder_picker_no_subfolders),
                    color = sub,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (currentFolder != null && currentFolder.recursiveTracks.isNotEmpty()) {
                    TextButton(onClick = { onSelectFolder(currentFolder) }) {
                        Text(stringResource(R.string.dj_folder_picker_play_this))
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visibleFolders, key = { it.folderPath }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFolder(folder) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.folderName,
                            color = onBg,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.dj_folder_picker_folder_meta,
                                folder.childFolders.size,
                                folder.recursiveTracks.size
                            ),
                            color = sub,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = { onSelectFolder(folder) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.dj_folder_picker_play_folder_cd),
                            tint = onBg,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = sub
                    )
                }
            }
        }
    }
}
