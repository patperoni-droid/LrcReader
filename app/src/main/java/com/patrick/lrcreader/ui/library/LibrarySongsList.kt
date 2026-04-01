package com.patrick.lrcreader.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun buildSongMetaLine(song: LibrarySongItem): String {
    val parts = mutableListOf(song.songId)
    if (!song.audioAvailable) parts += "NO AUDIO"
    if (song.hasLyrics) parts += "LRC"
    if (song.hasChords) parts += "CHD"
    if (song.hasNotes) parts += "NOTES"
    if (song.hasMidi) parts += "MIDI"
    if (song.hasLight) parts += "LIGHT"
    if (song.hasPrompter) parts += "PROMPT"
    return parts.joinToString(" • ")
}

@Composable
fun LibrarySongsList(
    songs: List<LibrarySongItem>,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    onOpenPlayer: (LibrarySongItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(songs, key = { it.songId }) { song ->
            val canPlay = song.audioAvailable
            val rowClick = if (canPlay) {
                Modifier.clickable { onOpenPlayer(song) }
            } else {
                Modifier
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(10.dp))
                    .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                    .then(rowClick)
                    .alpha(if (canPlay) 1f else 0.72f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2E7D32), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("SMP", color = Color.White, fontSize = 10.sp)
                }

                Spacer(Modifier.width(10.dp))

                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.displayTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.size(2.dp))

                    Text(
                        text = buildSongMetaLine(song),
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onOpenPlayer(song) },
                    enabled = canPlay
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (canPlay) accent else Color.White.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}
