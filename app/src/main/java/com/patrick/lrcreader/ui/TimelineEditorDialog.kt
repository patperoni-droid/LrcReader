package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.TimelineMarker
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditorDialog(
    markers: List<TimelineMarker>,
    palette: List<String>,
    playerPositionMs: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onAddPaletteTag: (String) -> Unit,
    onAddMarker: (String) -> Unit,
    onRenameMarker: (Int, String) -> Unit,
    onDeleteMarker: (Int) -> Unit
) {
    var renameIndex by remember(markers) { mutableStateOf<Int?>(null) }
    var renameText by remember(markers) { mutableStateOf("") }
    var paletteDraft by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val safePlayerPositionMs = playerPositionMs.coerceAtLeast(0L)
    var userScrolling by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    val activeMarkerIndex = remember(markers, safePlayerPositionMs) {
        markers.indexOfLast { marker -> marker.timeMs <= safePlayerPositionMs }
    }

    suspend fun centerActiveMarker() {
        if (activeMarkerIndex !in markers.indices) return

        val visible = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == activeMarkerIndex }
        if (visible == null) {
            lazyListState.scrollToItem(activeMarkerIndex)
        }

        val info = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == activeMarkerIndex }
            ?: return
        val start = lazyListState.layoutInfo.viewportStartOffset
        val end = lazyListState.layoutInfo.viewportEndOffset
        val viewportCenter = (start + end) / 2
        val itemCenter = info.offset + info.size / 2
        val delta = itemCenter - viewportCenter
        if (abs(delta) > 1) {
            lazyListState.scrollBy(delta.toFloat())
        }
    }

    LaunchedEffect(lazyListState) {
        while (true) {
            userScrolling = lazyListState.isScrollInProgress
            kotlinx.coroutines.delay(80L)
        }
    }

    LaunchedEffect(activeMarkerIndex, markers.size) {
        if (initialFocusDone) return@LaunchedEffect
        if (activeMarkerIndex !in markers.indices) return@LaunchedEffect
        centerActiveMarker()
        initialFocusDone = true
    }

    LaunchedEffect(isPlaying, activeMarkerIndex, markers.size) {
        if (!isPlaying) return@LaunchedEffect
        if (activeMarkerIndex !in markers.indices) return@LaunchedEffect
        while (true) {
            if (!userScrolling) {
                centerActiveMarker()
            }
            kotlinx.coroutines.delay(120L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.timeline_dialog_title),
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.timeline_palette_title),
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = paletteDraft,
                        onValueChange = { paletteDraft = it },
                        label = { Text(stringResource(R.string.timeline_palette_input_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val trimmed = paletteDraft.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddPaletteTag(trimmed)
                                paletteDraft = ""
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_palette_add_action),
                            color = Color(0xFF80CBC4)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (palette.isEmpty()) {
                    Text(
                        text = stringResource(R.string.timeline_palette_empty_state),
                        color = Color.Gray
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        palette.forEach { label ->
                            TextButton(onClick = { onAddMarker(label) }) {
                                Text(text = label, color = Color(0xFF80CBC4))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.timeline_markers_title),
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTimelineMarkerTime(safePlayerPositionMs),
                        color = if (activeMarkerIndex >= 0) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (markers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.timeline_empty_state),
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 420.dp),
                        contentPadding = PaddingValues(top = 140.dp, bottom = 140.dp)
                    ) {
                        itemsIndexed(
                            items = markers,
                            key = { _, marker -> "${marker.timeMs}:${marker.label}" }
                        ) { index, marker ->
                            val isActive = index == activeMarkerIndex
                            val rowShape = RoundedCornerShape(10.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isActive) Color(0x2239D98A) else Color.Transparent,
                                        shape = rowShape
                                    )
                                    .border(
                                        width = if (isActive) 1.dp else 0.dp,
                                        color = if (isActive) Color(0xFF39D98A) else Color.Transparent,
                                        shape = rowShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTimelineMarkerTime(marker.timeMs),
                                    color = if (isActive) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(74.dp)
                                )
                                Text(
                                    text = marker.label,
                                    color = if (isActive) Color(0xFF39D98A) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        renameIndex = index
                                        renameText = marker.label
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.timeline_cd_rename),
                                        tint = Color(0xFF80CBC4)
                                    )
                                }
                                IconButton(onClick = { onDeleteMarker(index) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.timeline_cd_delete),
                                        tint = Color(0xFFFF8A80)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
            }
        },
        containerColor = Color(0xFF222222)
    )

    val safeRenameIndex = renameIndex
    if (safeRenameIndex != null && safeRenameIndex in markers.indices) {
        AlertDialog(
            onDismissRequest = { renameIndex = null },
            title = {
                Text(
                    text = stringResource(R.string.timeline_rename_title),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.timeline_rename_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameMarker(safeRenameIndex, trimmed)
                        }
                        renameIndex = null
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameIndex = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

private fun formatTimelineMarkerTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hundredths = (safe % 1000L) / 10L
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}
