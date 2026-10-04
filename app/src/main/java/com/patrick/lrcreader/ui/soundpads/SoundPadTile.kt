package com.patrick.lrcreader.ui.soundpads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.exo.R

internal val SoundPadsPalette = listOf(
    0xFFED6755L, 0xFFEAB644L, 0xFF399EE8L, 0xFF9B71DCL,
    0xFF43BB8AL, 0xFFE56AA5L, 0xFF3BB9CCL, 0xFFE69549L
)
internal fun soundPadColor(pad: SoundPad): Long = pad.colorArgb ?: SoundPadsPalette[
    (pad.padId.hashCode() and Int.MAX_VALUE) % SoundPadsPalette.size
]

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SoundPadTile(
    pad: SoundPad?, slot: Int, active: Boolean, selected: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onTrigger: () -> Unit, onEdit: () -> Unit
) {
    val empty = pad == null || pad.audioPath.isEmpty()
    val color = if (pad == null) Color(0xFF8C919B) else Color(soundPadColor(pad))
    val shape = RoundedCornerShape(20.dp)
    val status = stringResource(if (active) R.string.soundpads_playing else if (empty) R.string.soundpads_available else R.string.soundpads_ready)
    val editLabel = stringResource(R.string.soundpads_settings)
    Box(
        modifier.shadow(if (active) 10.dp else 3.dp, shape,
            ambientColor = color.copy(alpha = 0.25f), spotColor = color.copy(alpha = 0.25f))
            .clip(shape).background(Color(0xFF171A1E))
            .border(if (active) 2.dp else 1.dp, if (active) color else Color.White.copy(alpha = 0.10f), shape)
            .combinedClickable(enabled = enabled, role = Role.Button,
                onClick = onTrigger, onLongClickLabel = editLabel, onLongClick = onEdit)
            .testTag(if (pad == null) "soundpad-empty-$slot" else "soundpad-${pad.padId}")
            .semantics { stateDescription = status }
            .padding(5.dp)
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(
                    color.copy(alpha = if (empty) 0.10f else if (active) 0.85f else 0.52f),
                    color.copy(alpha = if (empty) 0.02f else 0.12f))))
                .border(1.dp, color.copy(alpha = if (active) 0.95f else if (selected) 0.70f else 0.28f), RoundedCornerShape(15.dp))
                .padding(12.dp), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (empty) Icons.Filled.Add else Icons.Filled.MusicNote,
                    contentDescription = null, modifier = Modifier.size(30.dp),
                    tint = Color.White.copy(alpha = if (empty) 0.36f else 0.90f))
                Text(pad?.name ?: stringResource(R.string.soundpads_free),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = if (empty) 0.64f else 0.96f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Box(Modifier.align(Alignment.BottomCenter).width(32.dp).height(3.dp)
                .background(if (active) Color.White else color.copy(alpha = if (empty) 0.12f else 0.65f), RoundedCornerShape(2.dp)))
        }
    }
}
