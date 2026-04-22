package com.patrick.lrcreader.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.TimelineMarker
import com.patrick.lrcreader.smp.TimelineMarkerKind
import kotlin.math.abs

private const val TIMELINE_SLOT_MS = 1_000L
private const val TIMELINE_SCRUB_THROTTLE_MS = 120L

private data class IndexedTimelineMarker(
    val index: Int,
    val marker: TimelineMarker
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScrubColumn(
    modifier: Modifier = Modifier,
    markers: List<TimelineMarker>,
    positionMs: Int,
    durationMs: Int,
    isPlaying: Boolean,
    slotLabel: (Long) -> String = ::formatTimelineScrubTime,
    focusRequestTimeMs: Long? = null,
    focusRequestToken: Int = 0,
    onSeekToMs: (Long) -> Unit,
    onEditMarker: (Int) -> Unit,
    onDeleteMarker: (Int) -> Unit,
    onCopyDmxMarker: (Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val safeDurationMs = durationMs.coerceAtLeast(0).toLong()
    val safePositionMs = positionMs
        .coerceAtLeast(0)
        .toLong()
        .let { pos -> if (safeDurationMs > 0L) pos.coerceAtMost(safeDurationMs) else pos }
    val slotCount = remember(safeDurationMs) {
        if (safeDurationMs <= 0L) {
            1
        } else {
            ((safeDurationMs + TIMELINE_SLOT_MS - 1L) / TIMELINE_SLOT_MS).toInt() + 1
        }
    }

    fun slotIndexFromTime(timeMs: Long): Int {
        if (slotCount <= 1) return 0
        if (safeDurationMs > 0L && timeMs >= safeDurationMs) {
            return slotCount - 1
        }
        return (timeMs.coerceAtLeast(0L) / TIMELINE_SLOT_MS).toInt().coerceIn(0, slotCount - 1)
    }

    fun timeMsForSlot(slotIndex: Int): Long {
        return (slotIndex.toLong() * TIMELINE_SLOT_MS)
            .coerceAtLeast(0L)
            .let { time -> if (safeDurationMs > 0L) time.coerceAtMost(safeDurationMs) else time }
    }

    val activeMarkerIndex = remember(markers, safePositionMs) {
        markers.indexOfLast { marker -> marker.timeMs <= safePositionMs }
    }
    val currentSlotIndex = remember(safePositionMs, slotCount) {
        slotIndexFromTime(safePositionMs)
    }
    val markersBySlot = remember(markers, slotCount, safeDurationMs) {
        markers.mapIndexed { index, marker ->
            IndexedTimelineMarker(index = index, marker = marker)
        }.groupBy { entry ->
            slotIndexFromTime(entry.marker.timeMs)
        }
    }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var initialFocusDone by remember(safeDurationMs) { mutableStateOf(false) }
    var lastScrubTargetMs by remember { mutableStateOf(-1L) }
    var lastScrubDispatchAtMs by remember { mutableStateOf(0L) }

    fun centeredSlotIndex(): Int? {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return null

        val viewportCenter = (
            lazyListState.layoutInfo.viewportStartOffset +
                lazyListState.layoutInfo.viewportEndOffset
            ) / 2

        return visibleItems
            .minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }
            ?.index
            ?.coerceIn(0, slotCount - 1)
    }

    suspend fun centerSlot(slotIndex: Int) {
        if (slotIndex !in 0 until slotCount) return

        val visible = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == slotIndex }
        if (visible == null) {
            lazyListState.scrollToItem(slotIndex)
        }

        val info = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == slotIndex }
            ?: return
        val viewportCenter = (
            lazyListState.layoutInfo.viewportStartOffset +
                lazyListState.layoutInfo.viewportEndOffset
            ) / 2
        val itemCenter = info.offset + info.size / 2
        val delta = itemCenter - viewportCenter
        if (abs(delta) > 1) {
            lazyListState.scrollBy(delta.toFloat())
        }
    }

    fun dispatchScrubSeek(slotIndex: Int, force: Boolean = false) {
        if (slotIndex !in 0 until slotCount) return

        val targetMs = timeMsForSlot(slotIndex)
        val now = SystemClock.elapsedRealtime()
        val shouldDispatch = force ||
            (targetMs != lastScrubTargetMs && now - lastScrubDispatchAtMs >= TIMELINE_SCRUB_THROTTLE_MS)

        if (!shouldDispatch) return

        runCatching { onSeekToMs(targetMs) }
        lastScrubTargetMs = targetMs
        lastScrubDispatchAtMs = now
    }

    LaunchedEffect(currentSlotIndex, slotCount) {
        if (initialFocusDone || currentSlotIndex !in 0 until slotCount) return@LaunchedEffect
        centerSlot(currentSlotIndex)
        initialFocusDone = true
    }

    LaunchedEffect(focusRequestToken, focusRequestTimeMs, slotCount) {
        if (focusRequestToken <= 0) return@LaunchedEffect
        val targetTimeMs = focusRequestTimeMs ?: return@LaunchedEffect
        val targetSlotIndex = slotIndexFromTime(targetTimeMs)
        if (targetSlotIndex !in 0 until slotCount) return@LaunchedEffect
        centerSlot(targetSlotIndex)
    }

    LaunchedEffect(lazyListState, isPlaying, slotCount) {
        while (true) {
            val scrolling = lazyListState.isScrollInProgress

            if (!isPlaying && scrolling) {
                isUserScrubbing = true
                centeredSlotIndex()?.let { slotIndex ->
                    dispatchScrubSeek(slotIndex)
                }
            } else if (!scrolling) {
                if (isUserScrubbing) {
                    centeredSlotIndex()?.let { slotIndex ->
                        dispatchScrubSeek(slotIndex, force = true)
                    }
                }
                isUserScrubbing = false
            }

            kotlinx.coroutines.delay(80L)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentPadding = PaddingValues(top = 140.dp, bottom = 140.dp)
    ) {
        items(
            count = slotCount,
            key = { slotIndex -> slotIndex }
        ) { slotIndex ->
            val slotTimeMs = timeMsForSlot(slotIndex)
            val slotMarkers = markersBySlot[slotIndex].orEmpty()
            val isCurrentSlot = slotIndex == currentSlotIndex
            val rowShape = RoundedCornerShape(10.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isCurrentSlot) Color(0x2239D98A) else Color.Transparent,
                        shape = rowShape
                    )
                    .border(
                        width = if (isCurrentSlot) 1.dp else 0.dp,
                        color = if (isCurrentSlot) Color(0xFF39D98A) else Color.Transparent,
                        shape = rowShape
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = slotLabel(slotTimeMs),
                    color = if (isCurrentSlot) Color(0xFF80CBC4) else Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    modifier = Modifier.width(74.dp)
                )

                if (slotMarkers.isEmpty()) {
                    Text(
                        text = "\u00b7",
                        color = Color(0xFF4B4B4B),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        slotMarkers.forEach { entry ->
                            val isActiveMarker = entry.index == activeMarkerIndex
                            val rowInteractionModifier = if (entry.marker.kind == TimelineMarkerKind.DMX) {
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onEditMarker(entry.index) },
                                        onLongClick = { onCopyDmxMarker(entry.index) }
                                    )
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditMarker(entry.index) }
                            }

                            Row(
                                modifier = rowInteractionModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (entry.marker.kind != TimelineMarkerKind.TEXT) {
                                    TimelineMarkerTypeIcon(
                                        kind = entry.marker.kind,
                                        tint = markerTypeTint(entry.marker.kind)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = entry.marker.label,
                                    color = if (isActiveMarker) Color(0xFF39D98A) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isActiveMarker) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onEditMarker(entry.index) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.timeline_cd_rename),
                                        tint = Color(0xFF80CBC4)
                                    )
                                }
                                IconButton(onClick = { onDeleteMarker(entry.index) }) {
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
        }
    }
}

@Composable
private fun TimelineMarkerTypeIcon(
    kind: TimelineMarkerKind,
    tint: Color
) {
    val imageVector = when (kind) {
        TimelineMarkerKind.TEXT -> return
        TimelineMarkerKind.MIDI -> Icons.Filled.GraphicEq
        TimelineMarkerKind.NOTE -> Icons.AutoMirrored.Filled.StickyNote2
        TimelineMarkerKind.DMX -> Icons.Filled.FlashOn
    }

    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint
    )
}

private fun markerTypeTint(kind: TimelineMarkerKind): Color {
    return when (kind) {
        TimelineMarkerKind.TEXT -> Color.White
        TimelineMarkerKind.MIDI -> Color(0xFF80CBC4)
        TimelineMarkerKind.NOTE -> Color(0xFFFFF176)
        TimelineMarkerKind.DMX -> Color(0xFFFFB74D)
    }
}

private fun formatTimelineScrubTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
