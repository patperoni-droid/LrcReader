package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Ligne de titre DJ avec :
 *  - clic sur toute la ligne = jouer sur la platine
 *  - bouton + = mettre dans la file d’attente
 */
@Composable
fun DjTrackRow(
    title: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit
) {
    val playingBg = if (isPlaying) Color(0x22E040FB) else Color.Transparent
    val playingBorder = if (isPlaying) Color(0xFFE040FB) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(playingBg, RoundedCornerShape(10.dp))
            .border(
                width = if (isPlaying) 1.dp else 0.dp,
                color = playingBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onPlay() }   // clic sur la ligne = prêt à jouer
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isPlaying) Color(0xFFE040FB) else Color(0xFFFFF8E1),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onEnqueue,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Mettre en attente",
                tint = Color(0xFFFFF8E1).copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}