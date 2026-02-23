package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.history.HistoryEvent
import com.patrick.lrcreader.core.history.HistoryRepository
import com.patrick.lrcreader.core.history.PlaySource
import com.patrick.lrcreader.exo.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val HISTORY_UI_PREFS = "history_ui_prefs"
private const val KEY_SHOW_DATE_TIME = "show_date_time"

private val KNOWN_AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "flac", "m4a", "aac", "ogg", "oga", "opus", "wma",
    "aiff", "aif", "alac", "amr", "3gp", "mp4", "m4b", "m4p", "webm"
)

private enum class HistoryFilter(val label: String, val source: PlaySource?) {
    ALL("Tout", null),
    BACKING("Backing", PlaySource.BACKING),
    DJ("DJ", PlaySource.DJ)
}

@Composable
fun HistoryScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val repo = remember(context) { HistoryRepository.getInstance(context) }
    val prefs = remember(context) {
        context.getSharedPreferences(HISTORY_UI_PREFS, Context.MODE_PRIVATE)
    }
    var selectedFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var showDateTime by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_SHOW_DATE_TIME, true))
    }
    val eventsFlow = remember(repo, selectedFilter) {
        repo.observe(selectedFilter.source)
    }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_cd_back),
                    tint = Color(0xFFF5F5F5)
                )
            }
            Text(
                text = "Historique",
                color = Color(0xFFF5F5F5),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { scope.launch { repo.clearAll() } }
            ) {
                Text("Vider")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Date/heure",
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showDateTime,
                onCheckedChange = { checked ->
                    showDateTime = checked
                    prefs.edit().putBoolean(KEY_SHOW_DATE_TIME, checked).apply()
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (events.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aucun événement",
                    color = Color(0xFFB0BEC5),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            val groupedEvents = remember(events) {
                events.groupBy { event ->
                    dayStartMillis(event.timestamp)
                }.toSortedMap(compareByDescending { it })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                groupedEvents.forEach { (dayStartMs, dayEvents) ->
                    item(key = "day_header_$dayStartMs") {
                        Text(
                            text = formatDayHeader(dayStartMs),
                            color = Color(0xFFCFD8DC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = dayEvents,
                        key = { it.id }
                    ) { event ->
                        HistoryEventRow(
                            event = event,
                            showDateTime = showDateTime
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEventRow(
    event: HistoryEvent,
    showDateTime: Boolean
) {
    val dt: String? = remember(showDateTime, event.timestamp) {
        if (showDateTime) formatTimestamp(event.timestamp) else null
    }

    val isDj = event.source == PlaySource.DJ.name
    val djBlue = Color(0xFF81D4FA)
    val titleColor = if (isDj) djBlue else Color(0xFFF5F5F5)
    val metaColor = if (isDj) djBlue else Color(0xFFCFD8DC)

    val displayTitle = normalizeDisplayTitle(
        stripAudioExtension(event.title.ifBlank { HistoryRepository.UNTITLED_FALLBACK })
    )
    val metadata = buildList {
        event.artist?.takeIf { it.isNotBlank() }?.let { add(it) }
        dt?.let { add(it) }
    }.joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = displayTitle,
            color = titleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        if (metadata.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = metadata,
                color = metaColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Default
            )
        }
    }
}

private fun stripAudioExtension(name: String): String {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex == name.lastIndex) return name
    val ext = name.substring(dotIndex + 1).lowercase()
    return if (ext in KNOWN_AUDIO_EXTENSIONS) {
        name.substring(0, dotIndex)
    } else {
        name
    }
}

private fun normalizeDisplayTitle(name: String): String {
    val hasLetter = name.any { it.isLetter() }
    if (!hasLetter) return name

    val lower = name.lowercase()
    val firstLetterIndex = lower.indexOfFirst { it.isLetter() }
    if (firstLetterIndex < 0) return name

    val firstUpper = lower[firstLetterIndex].titlecaseChar().toString()
    return lower.replaceRange(firstLetterIndex, firstLetterIndex + 1, firstUpper)
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
}

private fun dayStartMillis(timestamp: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun formatDayHeader(dayStartMillis: Long): String {
    val raw = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault())
        .format(Date(dayStartMillis))
    return raw.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
}
