package com.patrick.lrcreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R
import kotlin.math.abs

data class ArrangementListItem(
    val id: String,
    val title: String,
    val durationMs: Long? = null,
    val repeatCount: Int = 1,
    val isMuted: Boolean = false,
    val color: String? = null,
    val isActive: Boolean = false,
    val isQueued: Boolean = false
)

data class ArrangementTrackPlayhead(
    val itemId: String,
    val repeatIndex: Int,
    val repeatCount: Int,
    val segmentProgressFraction: Float
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArrangementListCard(
    modifier: Modifier,
    title: String,
    emptyLabel: String,
    items: List<ArrangementListItem>,
    onItemClick: (String) -> Unit,
    onItemAdd: ((String) -> Unit)?,
    onItemDelete: ((String) -> Unit)?,
    onItemLongClick: ((String) -> Unit)?,
    horizontalTrack: Boolean = false,
    onItemMove: ((String, Int) -> Unit)? = null,
    itemActionsEnabled: Boolean = true,
    playhead: ArrangementTrackPlayhead? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (horizontalTrack) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (horizontalTrack) 0.dp else 6.dp)
        ) {
            if (!horizontalTrack) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyLabel,
                        color = Color(0xFF78909C),
                        fontSize = 13.sp
                    )
                }
            } else if (horizontalTrack) {
                ArrangementHorizontalTrack(
                    items = items,
                    onItemClick = onItemClick,
                    onItemAdd = onItemAdd,
                    onItemDelete = onItemDelete,
                    onItemLongClick = onItemLongClick,
                    onItemMove = onItemMove,
                    itemActionsEnabled = itemActionsEnabled,
                    playhead = playhead
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onItemClick(item.id) },
                                    onLongClick = if (onItemLongClick != null) {
                                        { onItemLongClick(item.id) }
                                    } else {
                                        null
                                    }
                                )
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                color = when {
                                    item.isActive -> Color(0xFF66BB6A)
                                    item.isQueued -> Color(0xFFFFD54F)
                                    else -> Color.White
                                },
                                fontSize = 14.sp,
                                fontWeight = when {
                                    item.isActive -> FontWeight.SemiBold
                                    item.isQueued -> FontWeight.Medium
                                    else -> FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (onItemAdd != null) {
                                IconButton(
                                    onClick = { onItemAdd(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.timeline_palette_add_action),
                                        tint = Color(0xFFCFD8DC),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (onItemDelete != null) {
                                IconButton(
                                    onClick = { onItemDelete(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.library_delete_action),
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArrangementHorizontalTrack(
    items: List<ArrangementListItem>,
    onItemClick: (String) -> Unit,
    onItemAdd: ((String) -> Unit)?,
    onItemDelete: ((String) -> Unit)?,
    onItemLongClick: ((String) -> Unit)?,
    onItemMove: ((String, Int) -> Unit)?,
    itemActionsEnabled: Boolean,
    playhead: ArrangementTrackPlayhead?
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val playheadOffsetDp = arrangementTrackPlayheadOffsetDp(items, playhead)
    val scrollOffsetDp = with(density) { scrollState.value.toDp().value }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = Color(0xFF0B1014),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .horizontalScroll(scrollState)
                .padding(
                    start = ARRANGEMENT_TRACK_CONTENT_PADDING_DP.dp,
                    top = 20.dp,
                    end = ARRANGEMENT_TRACK_CONTENT_PADDING_DP.dp,
                    bottom = 6.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(ARRANGEMENT_TRACK_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
            val borderColor = when {
                item.isActive -> Color(0xFF66BB6A)
                item.isQueued -> Color(0xFFFFD54F)
                else -> Color(0xFF455A64)
            }
            val occurrenceColor = arrangementTrackOccurrenceColor(item.color)
            val containerColor = when {
                item.isMuted -> occurrenceColor.copy(alpha = 0.24f)
                item.isActive -> occurrenceColor.copy(alpha = 0.82f)
                item.isQueued -> occurrenceColor.copy(alpha = 0.68f)
                else -> occurrenceColor.copy(alpha = 0.58f)
            }
            Column(
                modifier = Modifier
                    .width(arrangementTrackBlockWidthDp(item.durationMs).dp)
                    .height(92.dp)
                    .background(containerColor, RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { onItemClick(item.id) },
                        onLongClick = if (onItemLongClick != null && itemActionsEnabled) {
                            { onItemLongClick(item.id) }
                        } else {
                            null
                        }
                    )
                    .padding(start = 10.dp, top = 9.dp, end = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    color = if (item.isMuted) Color(0xFF90A4AE) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (item.isActive || item.isQueued) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.durationMs?.let { durationMs ->
                        Text(
                            text = formatArrangementTrackDuration(durationMs),
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))
                    if (item.repeatCount > 1) {
                        Text(
                            text = stringResource(
                                R.string.arrangement_occurrence_repeat_value,
                                item.repeatCount
                            ),
                            color = Color(0xFFCFD8DC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (item.isMuted) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(R.string.arrangement_occurrence_muted),
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (onItemAdd != null) {
                        IconButton(
                            onClick = { onItemAdd(item.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.timeline_palette_add_action),
                                tint = Color(0xFFCFD8DC),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (onItemMove != null) {
                        var dragAmount = 0f
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = stringResource(R.string.arrangement_occurrence_drag),
                            tint = if (itemActionsEnabled) Color(0xFFB0BEC5) else Color(0xFF455A64),
                            modifier = Modifier
                                .size(28.dp)
                                .pointerInput(item.id, itemActionsEnabled) {
                                    if (!itemActionsEnabled) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { dragAmount = 0f },
                                        onDragEnd = {
                                            if (abs(dragAmount) >= 24.dp.toPx()) {
                                                onItemMove(item.id, if (dragAmount > 0f) 1 else -1)
                                            }
                                        },
                                        onDragCancel = { dragAmount = 0f },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            dragAmount += drag.x
                                        }
                                    )
                                }
                                .padding(4.dp)
                        )
                    }
                    if (onItemLongClick != null) {
                        IconButton(
                            onClick = { onItemLongClick(item.id) },
                            enabled = itemActionsEnabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.common_cd_options),
                                tint = if (itemActionsEnabled) Color(0xFFCFD8DC) else Color(0xFF455A64),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (onItemDelete != null) {
                        IconButton(
                            onClick = { onItemDelete(item.id) },
                            enabled = itemActionsEnabled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.library_delete_action),
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            }
        }
        if (playheadOffsetDp != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val viewportX = playheadOffsetDp.dp.toPx() - scrollState.value
                if (viewportX in 0f..size.width) {
                    drawLine(
                        color = Color(0xFF80CBC4),
                        start = Offset(viewportX, 10.dp.toPx()),
                        end = Offset(viewportX, size.height - 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color(0xFF80CBC4),
                        radius = 5.dp.toPx(),
                        center = Offset(viewportX, 10.dp.toPx())
                    )
                }
            }
            if (playhead != null && playhead.repeatCount > 1) {
                Text(
                    text = stringResource(
                        R.string.arrangement_playhead_repeat_short,
                        playhead.repeatIndex + 1,
                        playhead.repeatCount
                    ),
                    color = Color(0xFF0B1014),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(
                            x = (playheadOffsetDp - scrollOffsetDp - 17f).dp,
                            y = 1.dp
                        )
                        .background(Color(0xFF80CBC4), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun arrangementTrackOccurrenceColor(color: String?): Color = when (color) {
    "red" -> Color(0xFF6D2A2A)
    "blue" -> Color(0xFF244A73)
    "green" -> Color(0xFF285E3A)
    "violet" -> Color(0xFF56336F)
    "orange", "amber" -> Color(0xFF74471F)
    "yellow" -> Color(0xFF665B1F)
    "gray" -> Color(0xFF455A64)
    else -> Color(0xFF1C2933)
}

internal fun arrangementTrackBlockWidthDp(durationMs: Long?): Float {
    val durationSeconds = durationMs?.coerceAtLeast(0L)?.div(1_000f) ?: 0f
    return (durationSeconds * ARRANGEMENT_TRACK_DP_PER_SECOND)
        .coerceIn(ARRANGEMENT_TRACK_MIN_BLOCK_WIDTH_DP, ARRANGEMENT_TRACK_MAX_BLOCK_WIDTH_DP)
}

internal fun arrangementTrackPlayheadOffsetDp(
    items: List<ArrangementListItem>,
    playhead: ArrangementTrackPlayhead?
): Float? {
    val target = playhead ?: return null
    val itemIndex = items.indexOfFirst { item -> item.id == target.itemId }
    if (itemIndex < 0) return null
    val targetWidthDp = arrangementTrackBlockWidthDp(items[itemIndex].durationMs)
    var previousWidthDp = 0f
    for (index in 0 until itemIndex) {
        previousWidthDp += arrangementTrackBlockWidthDp(items[index].durationMs)
    }
    val repeatCount = target.repeatCount.coerceAtLeast(1)
    val repeatIndex = target.repeatIndex.coerceIn(0, repeatCount - 1)
    val segmentProgress = target.segmentProgressFraction.coerceIn(0f, 1f)
    val blockProgress = (repeatIndex + segmentProgress) / repeatCount.toFloat()
    return ARRANGEMENT_TRACK_CONTENT_PADDING_DP +
        previousWidthDp +
        (itemIndex * ARRANGEMENT_TRACK_SPACING_DP) +
        (targetWidthDp * blockProgress)
}

private fun formatArrangementTrackDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val ARRANGEMENT_TRACK_DP_PER_SECOND = 5f
private const val ARRANGEMENT_TRACK_MIN_BLOCK_WIDTH_DP = 168f
private const val ARRANGEMENT_TRACK_MAX_BLOCK_WIDTH_DP = 600f
private const val ARRANGEMENT_TRACK_CONTENT_PADDING_DP = 8f
private const val ARRANGEMENT_TRACK_SPACING_DP = 8f
