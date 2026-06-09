package com.patrick.lrcreader.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.patrick.lrcreader.exo.R
import java.text.Normalizer

private data class LibrarySongVariantGroup(
    val family: SongVariantFamily,
    val songs: List<LibrarySongItem>
)

private sealed class LibrarySongListRow {
    data class Single(val song: LibrarySongItem) : LibrarySongListRow()
    data class Family(val group: LibrarySongVariantGroup) : LibrarySongListRow()
}

private val knownVariantSuffixes = listOf(
    "original",
    "short",
    "acoustic",
    "sans guitare",
    "-1 ton",
    "restaurant",
    "mariage"
)

private fun normalizeVariantText(value: String): String {
    val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents
        .lowercase()
        .replace(Regex("[\\[\\](){}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun inferVariantFamilyTitle(title: String): String {
    val clean = title.trim().replace(Regex("\\s+"), " ")
    val normalized = normalizeVariantText(clean)
    val suffix = knownVariantSuffixes.firstOrNull { normalized.endsWith(" ${normalizeVariantText(it)}") }
        ?: return clean
    val removeLength = suffix.length
    return clean.dropLast(removeLength).trim().trim('-', '–', '—', '_', '(', '[', ' ').ifBlank { clean }
}

internal fun variantLabelFor(familyTitle: String, displayTitle: String): String {
    val title = displayTitle.trim()
    val family = familyTitle.trim()
    if (title.equals(family, ignoreCase = true)) return title

    val suffix = title.removePrefix(family).trim().trim('-', '–', '—', '_', '(', ')', '[', ']', ' ')
    return suffix.takeIf { it.isNotBlank() } ?: title
}

private fun buildVariantFamilySongIds(source: LibrarySongItem, songs: List<LibrarySongItem>): Pair<String, Set<String>> {
    val familyTitle = inferVariantFamilyTitle(source.displayTitle)
    val normalizedFamily = normalizeVariantText(familyTitle)
    val ids = songs
        .filter { normalizeVariantText(inferVariantFamilyTitle(it.displayTitle)) == normalizedFamily }
        .map { it.songId }
        .toSet()
        .ifEmpty { setOf(source.songId) }
    return familyTitle to ids
}

private fun buildLibrarySongRows(
    songs: List<LibrarySongItem>,
    families: List<SongVariantFamily>
): List<LibrarySongListRow> {
    if (families.isEmpty()) return songs.map(LibrarySongListRow::Single)

    val byId = songs.associateBy { it.songId }
    val groupedSongIds = mutableSetOf<String>()
    val groupsByFirstSongId = families.mapNotNull { family ->
        val variants = family.songIds.mapNotNull(byId::get)
            .sortedBy { it.displayTitle.lowercase() }
        if (variants.size < 2) return@mapNotNull null
        groupedSongIds += variants.map { it.songId }
        variants.first().songId to LibrarySongVariantGroup(family = family, songs = variants)
    }.toMap()

    return buildList {
        songs.forEach { song ->
            val group = groupsByFirstSongId[song.songId]
            when {
                group != null -> add(LibrarySongListRow.Family(group))
                song.songId !in groupedSongIds -> add(LibrarySongListRow.Single(song))
            }
        }
    }
}

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
    listState: LazyListState,
    currentPlayingSongId: String?,
    keyboardSelectedSongId: String?,
    cardBg: Color,
    rowBorder: Color,
    accent: Color,
    bottomPadding: Dp,
    showRichIndicators: Boolean = true,
    compactTabletLayout: Boolean = false,
    selectedSongs: Set<Uri>,
    onToggleSelect: (Uri) -> Unit,
    onKeyboardSelectedSongChange: (String) -> Unit,
    onOpenPlayer: (LibrarySongItem) -> Unit,
    onAssignOne: (Uri) -> Unit,
    onAssignFamily: (SongVariantFamily) -> Unit,
    onShareOne: (Uri) -> Unit,
    onRenameOne: (LibrarySongItem) -> Unit,
    onDeleteOne: (Uri) -> Unit
) {
    val context = LocalContext.current
    val selectionMode = selectedSongs.isNotEmpty()
    val familyVersion = SongVariantFamiliesStore.version.intValue
    val variantFamilies = remember(familyVersion) { SongVariantFamiliesStore.load(context) }
    val rows = remember(songs, variantFamilies) { buildLibrarySongRows(songs, variantFamilies) }
    var expandedFamilyIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val rowOuterVerticalPadding = if (compactTabletLayout) 1.dp else 3.dp
    val rowHorizontalPadding = if (compactTabletLayout) 8.dp else 12.dp
    val rowVerticalPadding = if (compactTabletLayout) 4.dp else 8.dp
    val rowHeight = if (compactTabletLayout) 44.dp else 48.dp
    val variantRowHeight = if (compactTabletLayout) 30.dp else 34.dp
    val titleFontSize = if (compactTabletLayout) 14.sp else 15.sp
    val checkboxSize = if (compactTabletLayout) 18.dp else 20.dp
    val leadingSpacerWidth = if (compactTabletLayout) 7.dp else 10.dp
    val actionButtonSize = if (compactTabletLayout) 40.dp else 48.dp
    val dividerAlpha = if (compactTabletLayout) 0.28f else 0.5f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        items(
            rows,
            key = { row ->
                when (row) {
                    is LibrarySongListRow.Single -> row.song.songId
                    is LibrarySongListRow.Family -> "variant-family:${row.group.family.id}"
                }
            }
        ) { row ->
            if (row is LibrarySongListRow.Family) {
                val group = row.group
                val activeSong = group.family.activeSongId
                    ?.let { activeId -> group.songs.firstOrNull { it.songId == activeId } }
                    ?: group.family.parentSongId
                        ?.let { parentId -> group.songs.firstOrNull { it.songId == parentId } }
                    ?: group.songs.first()
                val activeSongUri = remember(activeSong.playbackItem) { Uri.parse(activeSong.playbackItem) }
                val isExpanded = group.family.id in expandedFamilyIds
                val anySelected = group.songs.any { selectedSongs.contains(Uri.parse(it.playbackItem)) }
                val isCurrentPlaying = group.songs.any { currentPlayingSongId == it.songId }
                val isKeyboardSelected = group.songs.any { keyboardSelectedSongId == it.songId }
                val rowShape = RoundedCornerShape(10.dp)
                val rowBackground = when {
                    anySelected -> accent.copy(alpha = 0.18f)
                    isKeyboardSelected -> accent.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rowOuterVerticalPadding)
                            .background(rowBackground, rowShape)
                            .border(
                                if (anySelected || isKeyboardSelected) 2.dp else 0.dp,
                                if (anySelected) accent else accent.copy(alpha = 0.9f),
                                rowShape
                            )
                            .clip(rowShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isExpanded) "▼" else "▶",
                                color = accent,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .width(28.dp)
                                    .clickable {
                                        expandedFamilyIds = if (isExpanded) {
                                            expandedFamilyIds - group.family.id
                                        } else {
                                            expandedFamilyIds + group.family.id
                                        }
                                    }
                            )

                            if (isCurrentPlaying) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .width(3.dp)
                                        .height(26.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.95f))
                                )
                            }

                            Text(
                                text = activeSong.displayTitle,
                                color = if (isCurrentPlaying) Color(0xFFFFFDE7) else Color.White,
                                fontSize = titleFontSize,
                                fontWeight = if (isCurrentPlaying) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onKeyboardSelectedSongChange(activeSong.songId)
                                        if (selectionMode) {
                                            onToggleSelect(activeSongUri)
                                        } else if (activeSong.audioAvailable) {
                                            onOpenPlayer(activeSong)
                                        }
                                    }
                            )

                            Text(
                                text = group.songs.size.toString(),
                                color = accent,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )

                            IconButton(
                                modifier = Modifier.size(actionButtonSize),
                                onClick = {
                                    onKeyboardSelectedSongChange(activeSong.songId)
                                    onOpenPlayer(activeSong)
                                },
                                enabled = activeSong.audioAvailable
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (activeSong.audioAvailable) accent else Color.White.copy(alpha = 0.35f)
                                )
                            }

                            Box {
                                var familyMenuOpen by remember(group.family.id) { mutableStateOf(false) }
                                IconButton(
                                    modifier = Modifier.size(actionButtonSize),
                                    onClick = { familyMenuOpen = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = familyMenuOpen,
                                    onDismissRequest = { familyMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.library_list_assign_to_playlist),
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            familyMenuOpen = false
                                            onAssignFamily(group.family)
                                        }
                                    )
                                }
                            }
                        }

                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                group.songs
                                    .filter { it.songId != activeSong.songId }
                                    .forEach { variant ->
                                        val variantUri = remember(variant.playbackItem) { Uri.parse(variant.playbackItem) }
                                        val isSelected = selectedSongs.contains(variantUri)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(variantRowHeight)
                                                .background(
                                                    if (isSelected) {
                                                        accent.copy(alpha = 0.16f)
                                                    } else {
                                                        Color.White.copy(alpha = 0.045f)
                                                    }
                                                )
                                                .clickable {
                                                    SongVariantFamiliesStore.setActiveSongId(
                                                        context = context,
                                                        familyId = group.family.id,
                                                        songId = variant.songId
                                                    )
                                                    onKeyboardSelectedSongChange(variant.songId)
                                                    onToggleSelect(variantUri)
                                                }
                                                .padding(start = 34.dp, end = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .width(2.dp)
                                                    .height(18.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(accent.copy(alpha = 0.45f))
                                            )
                                            Text(
                                                text = variant.displayTitle,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (
                                                showRichIndicators && (
                                                    variant.hasLyrics ||
                                                        variant.hasChords ||
                                                        variant.hasMidi ||
                                                        variant.hasLight ||
                                                        variant.hasNotes
                                                    )
                                            ) {
                                                Spacer(Modifier.width(8.dp))
                                                LibrarySongIndicators(variant)
                                            }
                                            if (selectionMode) {
                                                Spacer(Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .background(
                                                            if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isSelected) accent else Color.White.copy(alpha = 0.45f),
                                                            RoundedCornerShape(4.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Text("✕", color = accent, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                    }
                    HorizontalDivider(color = rowBorder.copy(alpha = dividerAlpha))
                }
                return@items
            }

            val song = (row as LibrarySongListRow.Single).song
            val songUri = remember(song.playbackItem) { Uri.parse(song.playbackItem) }
            val canPlay = song.audioAvailable
            val isSelected = selectedSongs.contains(songUri)
            val isCurrentPlaying = currentPlayingSongId == song.songId
            val isKeyboardSelected = keyboardSelectedSongId == song.songId
            var menuOpen by remember(song.songId) { mutableStateOf(false) }
            val rowClick = if (selectionMode) {
                Modifier.clickable {
                    onKeyboardSelectedSongChange(song.songId)
                    onToggleSelect(songUri)
                }
            } else if (canPlay) {
                Modifier.clickable {
                    onKeyboardSelectedSongChange(song.songId)
                    onOpenPlayer(song)
                }
            } else {
                Modifier
            }
            val titleColor = if (isCurrentPlaying) {
                Color(0xFFFFFDE7)
            } else {
                Color.White
            }
            val rowShape = RoundedCornerShape(10.dp)
            val rowBackground = when {
                isSelected -> accent.copy(alpha = 0.18f)
                isKeyboardSelected -> accent.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            val rowStrokeColor = when {
                isSelected -> accent
                isKeyboardSelected -> accent.copy(alpha = 0.9f)
                else -> Color.Transparent
            }
            val rowStrokeWidth = if (isSelected || isKeyboardSelected) 2.dp else 0.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = rowOuterVerticalPadding)
                        .background(rowBackground, rowShape)
                        .then(
                            if (rowStrokeWidth > 0.dp) {
                                Modifier.border(rowStrokeWidth, rowStrokeColor, rowShape)
                            } else {
                                Modifier
                            }
                        )
                        .then(rowClick)
                        .alpha(if (canPlay) 1f else 0.72f)
                        .padding(horizontal = rowHorizontalPadding, vertical = rowVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(checkboxSize)
                            .background(
                                if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) accent else Color.White.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                onKeyboardSelectedSongChange(song.songId)
                                onToggleSelect(songUri)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✕", color = accent, fontSize = if (compactTabletLayout) 12.sp else 13.sp)
                        }
                    }

                    Spacer(Modifier.width(leadingSpacerWidth))

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
                            fontSize = titleFontSize,
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
                        modifier = Modifier.size(actionButtonSize),
                        onClick = {
                            onKeyboardSelectedSongChange(song.songId)
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
                        IconButton(
                            modifier = Modifier.size(actionButtonSize),
                            onClick = { menuOpen = true }
                        ) {
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
                                        stringResource(R.string.library_variant_create_family),
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    val (familyTitle, familySongIds) = buildVariantFamilySongIds(song, songs)
                                    SongVariantFamiliesStore.createOrReplaceFamily(
                                        context = context,
                                        title = familyTitle,
                                        songIds = familySongIds
                                    )
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
                HorizontalDivider(color = rowBorder.copy(alpha = dividerAlpha))
            }
        }
    }
}
