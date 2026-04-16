package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R

@Composable
private fun LibrarySongIndicators(song: LibrarySongItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (song.hasLyrics) {
            Text(text = "🎤", fontSize = 12.sp, modifier = Modifier.alpha(0.92f))
        }
        if (song.hasChords) {
            Text(text = "🎸", fontSize = 12.sp, modifier = Modifier.alpha(0.92f))
        }
        if (song.hasMidi) {
            Text(text = "🎹", fontSize = 12.sp, modifier = Modifier.alpha(0.92f))
        }
        if (song.hasLight) {
            Text(text = "💡", fontSize = 12.sp, modifier = Modifier.alpha(0.92f))
        }
        if (song.hasNotes) {
            Text(text = "📝", fontSize = 12.sp, modifier = Modifier.alpha(0.92f))
        }
    }
}

@Composable
fun LibrarySongsList(
    songs: List<LibrarySongItem>,
    currentPlayingSongId: String?,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    showRichIndicators: Boolean = true,
    selectedSongs: Set<Uri>,
    onToggleSelect: (Uri) -> Unit,
    onOpenPlayer: (LibrarySongItem) -> Unit,
    onAssignOne: (Uri) -> Unit,
    onShareOne: (Uri) -> Unit,
    onRenameOne: (LibrarySongItem) -> Unit,
    onDeleteOne: (Uri) -> Unit
) {
    val selectionMode = selectedSongs.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(songs, key = { it.songId }) { song ->
            val songUri = remember(song.playbackItem) { Uri.parse(song.playbackItem) }
            val canPlay = song.audioAvailable
            val isSelected = selectedSongs.contains(songUri)
            val isCurrentPlaying = currentPlayingSongId == song.songId
            var menuOpen by remember(song.songId) { mutableStateOf(false) }
            val rowClick = if (selectionMode) {
                Modifier.clickable { onToggleSelect(songUri) }
            } else if (canPlay) {
                Modifier.clickable { onOpenPlayer(song) }
            } else {
                Modifier
            }
            val backgroundColor = if (isSelected) {
                accent.copy(alpha = 0.16f)
            } else if (isCurrentPlaying) {
                accent.copy(alpha = 0.12f)
            } else {
                cardBg
            }
            val borderColor = if (isSelected) {
                accent
            } else if (isCurrentPlaying) {
                accent.copy(alpha = 0.45f)
            } else {
                rowBorder
            }
            val titleColor = if (isCurrentPlaying) {
                Color(0xFFFFFDE7)
            } else {
                Color.White
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .then(rowClick)
                    .alpha(if (canPlay) 1f else 0.72f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onToggleSelect(songUri) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text("✕", color = accent, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.width(10.dp))

                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .width(3.dp)
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.95f))
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.displayTitle,
                        color = titleColor,
                        fontSize = 15.sp,
                        fontWeight = if (isCurrentPlaying) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (
                        showRichIndicators && (
                            song.hasLyrics ||
                                song.hasChords ||
                                song.hasMidi ||
                                song.hasLight ||
                                song.hasNotes
                            )
                    ) {
                        Spacer(Modifier.width(8.dp))
                        LibrarySongIndicators(song)
                    }
                }

                IconButton(
                    onClick = {
                        if (selectionMode) onToggleSelect(songUri) else onOpenPlayer(song)
                    },
                    enabled = canPlay
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (canPlay) accent else Color.White.copy(alpha = 0.35f)
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.library_list_assign_to_playlist),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onAssignOne(songUri)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.backup_share),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onShareOne(songUri)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.library_list_rename),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onRenameOne(song)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.library_delete_action),
                                    color = Color(0xFFFF6464)
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDeleteOne(songUri)
                            }
                        )
                    }
                }
            }
        }
    }
}
