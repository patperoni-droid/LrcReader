package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import java.net.URLDecoder

/**
 * QuickPlaylistsScreen + titres "texte seul" (prompteur).
 */
@Composable
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String, Color) -> Unit,
    refreshKey: Int,
    currentPlayingUri: String? = null,
    selectedPlaylist: String? = null,
    onSelectedPlaylistChange: (String?) -> Unit = {},
    onPlaylistColorChange: (Color) -> Unit = {}
) {
    val context = LocalContext.current

    val playlists = remember(refreshKey) { PlaylistRepository.getPlaylists() }

    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: playlists.firstOrNull())
    }

    val songs = remember { mutableStateListOf<String>() }

    var currentListColor by remember { mutableStateOf(Color(0xFFE86FFF)) }
    var colorMenuOpen by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    var isFillerRunning by remember { mutableStateOf(FillerSoundManager.isPlaying()) }

    // dialog création titre texte (ancien système TextSongRepository)
    var showCreateTextDialog by remember { mutableStateOf(false) }
    var newTextTitle by remember { mutableStateOf("") }
    var newTextContent by remember { mutableStateOf("") }

    // recharge quand playlist change
    LaunchedEffect(internalSelected, refreshKey) {
        songs.clear()
        val pl = internalSelected
        if (pl != null) {
            songs.addAll(PlaylistRepository.getSongsFor(pl))
            val savedColor = loadPlaylistColor(context, pl)
            currentListColor = savedColor ?: Color(0xFFE86FFF)
            onPlaylistColorChange(currentListColor)
        }
    }

    // si le parent force une playlist
    LaunchedEffect(selectedPlaylist) {
        if (selectedPlaylist != null) {
            internalSelected = selectedPlaylist
            songs.clear()
            songs.addAll(PlaylistRepository.getSongsFor(selectedPlaylist))
            val savedColor = loadPlaylistColor(context, selectedPlaylist)
            currentListColor = savedColor ?: Color(0xFFE86FFF)
            onPlaylistColorChange(currentListColor)
        }
    }

    // si la liste de playlists change
    LaunchedEffect(playlists) {
        if (internalSelected !in playlists) {
            val first = playlists.firstOrNull()
            internalSelected = first
            songs.clear()
            if (first != null) {
                songs.addAll(PlaylistRepository.getSongsFor(first))
                val savedColor = loadPlaylistColor(context, first)
                currentListColor = savedColor ?: Color(0xFFE86FFF)
                onSelectedPlaylistChange(first)
                onPlaylistColorChange(currentListColor)
            }
        }
    }

    val menuBg = Color(0xFF222222)

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ─── HEADER encadré + flèche + icônes ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // bloc titre qui prend toute la largeur disponible
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { showMenu = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = internalSelected ?: "Sélectionne une playlist",
                            color = Color.White,
                            fontSize = 20.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Choisir une playlist",
                            tint = Color.White
                        )
                    }

                    // menu déroulant des playlists
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(menuBg)
                    ) {
                        playlists.forEach { name ->
                            val isCurrent = name == internalSelected
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = name,
                                        color = if (isCurrent) currentListColor else Color.White,
                                        fontSize = 16.sp
                                    )
                                },
                                onClick = {
                                    internalSelected = name
                                    onSelectedPlaylistChange(name)
                                    showMenu = false
                                    // LaunchedEffect va recharger la liste et la couleur
                                }
                            )
                        }
                    }
                }

                // icônes à droite
                Row(verticalAlignment = Alignment.CenterVertically) {

                    // ➕ ancien bouton "nouveau titre texte"
                    IconButton(
                        onClick = {
                            if (internalSelected != null) {
                                newTextTitle = ""
                                newTextContent = ""
                                showCreateTextDialog = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Titre texte",
                            tint = Color(0xFF81C784)
                        )
                    }

                    // palette
                    Box {
                        IconButton(onClick = { colorMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = "Couleur de la liste",
                                tint = currentListColor
                            )
                        }
                        DropdownMenu(
                            expanded = colorMenuOpen,
                            onDismissRequest = { colorMenuOpen = false },
                            modifier = Modifier.background(Color(0xFF1E1E1E))
                        ) {
                            val colors = listOf(
                                Color.White,
                                Color(0xFFE86FFF),
                                Color(0xFF6AC5FE),
                                Color(0xFFFFB74D),
                                Color(0xFF81C784),
                                Color(0xFFFF6F91),
                            )
                            colors.forEach { c ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .background(c, RoundedCornerShape(999.dp))
                                                .border(
                                                    1.dp,
                                                    Color.White,
                                                    RoundedCornerShape(999.dp)
                                                )
                                        )
                                    },
                                    text = { Text("") },
                                    onClick = {
                                        currentListColor = c
                                        internalSelected?.let { pl ->
                                            savePlaylistColor(context, pl, c)
                                        }
                                        colorMenuOpen = false
                                        onPlaylistColorChange(c)
                                    }
                                )
                            }
                        }
                    }

                    // reset (NE TOUCHE PAS aux "à revoir", seulement "joué")
                    if (internalSelected != null) {
                        IconButton(
                            onClick = {
                                val pl = internalSelected ?: return@IconButton
                                PlaylistRepository.resetPlayedFor(pl)
                                songs.clear()
                                songs.addAll(PlaylistRepository.getSongsFor(pl))
                                onSelectedPlaylistChange(pl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Réinitialiser",
                                tint = Color(0xFFFFB74D)
                            )
                        }
                    }

                    // toggle filler
                    IconButton(
                        onClick = {
                            if (isFillerRunning) {
                                FillerSoundManager.fadeOutAndStop(200)
                                isFillerRunning = false
                            } else {
                                FillerSoundManager.startIfConfigured(context)
                                isFillerRunning = FillerSoundManager.isPlaying()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFillerRunning) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = currentListColor
                        )
                    }

                    // next filler
                    IconButton(
                        onClick = {
                            FillerSoundManager.next(context)
                            isFillerRunning = FillerSoundManager.isPlaying()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null,
                            tint = currentListColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── LISTE DES TITRES ─────────────────────
            if (internalSelected == null) {
                Text("Aucune playlist.\nVa dans “Toutes” pour en créer.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState
                ) {
                    itemsIndexed(songs, key = { _, item -> item }) { _, uriString ->

                        // Décodage "brut" (nom de fichier ou id brut)
                        val decoded = runCatching {
                            URLDecoder.decode(uriString, "UTF-8")
                        }.getOrElse { uriString }

                        val baseNameClean = decoded
                            .substringAfterLast('/')
                            .substringAfterLast(':')
                            .let { name ->
                                when {
                                    name.endsWith(".mp3", true) -> name.dropLast(4)
                                    name.endsWith(".wav", true) -> name.dropLast(4)
                                    else -> name
                                }
                            }
                            .trim()

                        // Titre personnalisé éventuel de la playlist
                        val customTitle = internalSelected?.let {
                            PlaylistRepository.getCustomTitle(it, uriString)
                        }

                        // 🔹 Titre venant d'une NOTE (note://<id>)
                        val noteTitle: String? = if (uriString.startsWith("note://")) {
                            val idStr = uriString.removePrefix("note://")
                            val id = idStr.toLongOrNull()
                            id?.let { nid ->
                                NotesRepository.get(context, nid)?.title
                            }
                        } else null

                        // 🔹 Titre venant de l'ancien TextSongRepository (prompter://<id>)
                        val textSongTitle: String? = if (uriString.startsWith("prompter://")) {
                            val idStr = uriString.removePrefix("prompter://")
                            TextSongRepository.get(context, idStr)?.title
                        } else null

                        val displayName = (customTitle ?: noteTitle ?: textSongTitle ?: baseNameClean)
                            .ifBlank { baseNameClean }

                        val isPlayed = internalSelected?.let {
                            PlaylistRepository.isSongPlayed(it, uriString)
                        } ?: false

                        val isToReview = internalSelected?.let {
                            PlaylistRepository.isSongToReview(it, uriString)
                        } ?: false

                        val isCurrentPlaying = currentPlayingUri == uriString
                        val isDraggingThis = draggingUri == uriString

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .background(
                                    if (isDraggingThis) Color(0x22FFFFFF) else Color.Transparent
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Déplacer",
                                tint = currentListColor,
                                modifier = Modifier
                                    .size(34.dp)
                                    .padding(end = 8.dp)
                                    .pointerInput(songs.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingUri = uriString
                                                dragOffsetPx = 0f
                                            },
                                            onDragEnd = {
                                                draggingUri = null
                                                dragOffsetPx = 0f
                                                internalSelected?.let { pl ->
                                                    PlaylistRepository.updatePlayListOrder(
                                                        pl,
                                                        songs.toList()
                                                    )
                                                }
                                            },
                                            onDragCancel = {
                                                draggingUri = null
                                                dragOffsetPx = 0f
                                            }
                                        ) { _, dragAmount ->
                                            val current = draggingUri
                                                ?: return@detectDragGesturesAfterLongPress
                                            val currentIndex = songs.indexOf(current)
                                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                            dragOffsetPx += dragAmount.y

                                            if (dragOffsetPx >= rowHeightPx / 2f) {
                                                val next = currentIndex + 1
                                                if (next < songs.size) songs.swap(currentIndex, next)
                                                dragOffsetPx = 0f
                                            }
                                            if (dragOffsetPx <= -rowHeightPx / 2f) {
                                                val prev = currentIndex - 1
                                                if (prev >= 0) songs.swap(currentIndex, prev)
                                                dragOffsetPx = 0f
                                            }
                                        }
                                    }
                            )

                            Text(
                                text = displayName.uppercase(),
                                color = when {
                                    isToReview -> Color(0xFFFF6F6F)      // rouge = à revoir
                                    isCurrentPlaying -> Color.White
                                    isPlayed -> Color.Gray
                                    else -> currentListColor
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        internalSelected?.let { pl ->
                                            onPlaySong(uriString, pl, currentListColor)
                                            onSelectedPlaylistChange(pl)
                                        }
                                    }
                            )

                            // menu 3 points
                            Box {
                                var menuOpen by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(
                                            width = 1.dp,
                                            color = if (isPlayed) Color.Gray else currentListColor,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { menuOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Options",
                                        tint = if (isPlayed) Color.Gray else currentListColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Retirer de la liste",
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            internalSelected?.let { pl ->
                                                PlaylistRepository.removeSongFromPlaylist(
                                                    pl,
                                                    uriString
                                                )
                                            }
                                            songs.remove(uriString)
                                            menuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Renommer", color = Color.White) },
                                        onClick = {
                                            renameTarget = uriString
                                            renameText = displayName
                                            menuOpen = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isToReview) "Retirer \"à revoir\""
                                                else "Marquer \"à revoir\"",
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            internalSelected?.let { pl ->
                                                PlaylistRepository.setSongToReview(
                                                    pl,
                                                    uriString,
                                                    !isToReview
                                                )
                                            }
                                            menuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // dialog renommage
    if (renameTarget != null && internalSelected != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renommer", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetUri = renameTarget ?: return@TextButton
                    val pl = internalSelected ?: return@TextButton
                    PlaylistRepository.renameSongInPlaylist(
                        pl,
                        targetUri,
                        renameText.trim()
                    )
                    renameTarget = null
                }) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Annuler", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // dialog création titre texte (ancien système)
    if (showCreateTextDialog && internalSelected != null) {
        AlertDialog(
            onDismissRequest = { showCreateTextDialog = false },
            title = { Text("Nouveau titre (prompteur)", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTextTitle,
                        onValueChange = { newTextTitle = it },
                        label = { Text("Titre") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTextContent,
                        onValueChange = { newTextContent = it },
                        label = { Text("Texte du prompteur") },
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val title = newTextTitle.trim()
                    val content = newTextContent.trim()
                    val pl = internalSelected ?: return@TextButton

                    if (title.isNotEmpty() && content.isNotEmpty()) {
                        val id = TextSongRepository.create(context, title, content)
                        val uri = "prompter://$id"
                        PlaylistRepository.assignSongToPlaylist(pl, uri)
                        songs.add(uri)
                    }

                    showCreateTextDialog = false
                }) {
                    Text("OK", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTextDialog = false }) {
                    Text("Annuler", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

// utilitaire drag
private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

// prefs couleur
private const val PLAYLIST_COLOR_PREF = "playlist_color_pref"

private fun savePlaylistColor(context: Context, playlist: String, color: Color) {
    val prefs =
        context.getSharedPreferences(PLAYLIST_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .putInt(playlist, color.toArgb())
        .apply()
}

private fun loadPlaylistColor(context: Context, playlist: String): Color? {
    val prefs =
        context.getSharedPreferences(PLAYLIST_COLOR_PREF, Context.MODE_PRIVATE)
    return if (prefs.contains(playlist)) {
        Color(prefs.getInt(playlist, Color(0xFFE86FFF).toArgb()))
    } else null
}