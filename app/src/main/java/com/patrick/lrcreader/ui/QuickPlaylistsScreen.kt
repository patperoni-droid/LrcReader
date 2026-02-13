package com.patrick.lrcreader.ui


import android.media.MediaMetadataRetriever
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.patrick.lrcreader.exo.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.LibraryIndexCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import android.content.Context
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
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
import androidx.compose.material.icons.filled.Refresh
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
    onPlaylistColorChange: (Color) -> Unit = {},
    onRequestShowPlayer: () -> Unit = {},
    indexAll: List<LibraryIndexCache.CachedEntry> = emptyList() // ✅ propre + default
) {

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var isRenameBusy by remember { mutableStateOf(false) }

// ✅ IMPORTANT : on observe le repo RAM (sinon la playlist garde des URI "morts" après rename en bibliothèque)
    val repoVersion = PlaylistRepository.version.value

// ✅ la liste des playlists se met à jour dès que le repo change
    val playlists = remember(refreshKey, repoVersion) { PlaylistRepository.getPlaylists() }

    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: playlists.firstOrNull())
    }

    val songs = remember { mutableStateListOf<String>() }
    // ✅ Snapshot "ordre d'origine" (pour le bouton Réinitialiser)
    // - On le fixe au premier chargement d'une playlist
    // - Et on le met à jour quand TU réordonnes à la main (drag)
    // ✅ Durée playlist (cache par titre) + affichage mini dans le header
    val durationCache = remember { mutableStateMapOf<String, Long>() } // uriString -> ms
    var playlistTotalMs by remember { mutableStateOf(-1L) } // -1 = loading
    val originalOrderByPlaylist = remember { mutableStateMapOf<String, List<String>>() }
    var currentListColor by remember { mutableStateOf(Color.White) } // ✅ plus de couleur "globale" de playlist

    var showMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    var isFillerRunning by remember { mutableStateOf(FillerSoundManager.isPlaying()) }

    // dialog création titre texte (ancienne méthode, on la garde pour l’instant)
    var showCreateTextDialog by remember { mutableStateOf(false) }
    var newTextTitle by remember { mutableStateOf("") }
    var newTextContent by remember { mutableStateOf("") }
// ✅ dialog édition titre texte (prompteur)
    var showEditTextDialog by remember { mutableStateOf(false) }
    var editTargetUri by remember { mutableStateOf<String?>(null) }
    var editTextTitle by remember { mutableStateOf("") }
    var editTextContent by remember { mutableStateOf("") }
    // 🔹 version des notes : incrémentée quand une note change
    var notesVersion by remember { mutableStateOf(0) }

    // 🔸 version des couleurs par titre : on incrémente pour forcer recompose après un choix
    var songColorsVersion by remember { mutableStateOf(0) }

    // Abonnement aux changements de notes
    LaunchedEffect(Unit) {
        NotesEventBus.subscribe {
            notesVersion++
        }
    }

    // recharge quand playlist ou notes changent
    LaunchedEffect(internalSelected, refreshKey, notesVersion, repoVersion) {
        songs.clear()
        val pl = internalSelected
        if (pl != null) {
            val loaded = PlaylistRepository.getSongsFor(pl)
            songs.addAll(loaded)

            // ✅ Si on n'a pas encore d'ordre "d'origine" pour cette playlist, on le mémorise
            if (originalOrderByPlaylist[pl].isNullOrEmpty()) {
                originalOrderByPlaylist[pl] = loaded.toList()
            }

            currentListColor = Color.White

            // ✅ calc durée totale (async) — prompter ignoré
            playlistTotalMs = -1L
            val listSnapshot = loaded.toList()
            playlistTotalMs = withContext(Dispatchers.IO) {
                var acc = 0L
                for (u in listSnapshot) {
                    if (u.startsWith("prompter://")) continue
                    val cached = durationCache[u]
                    val d = cached
                        ?: (getAudioDurationMsQP(context, u) ?: 0L).also {
                            durationCache[u] = it
                        }
                    acc += d
                }
                acc
            }
        }
    }

    // si le parent force une playlist
    LaunchedEffect(selectedPlaylist, repoVersion) {
        if (selectedPlaylist != null) {
            internalSelected = selectedPlaylist
            songs.clear()
            val loaded = PlaylistRepository.getSongsFor(selectedPlaylist)
            songs.addAll(loaded)

            // ✅ Init ordre d'origine si absent
            if (originalOrderByPlaylist[selectedPlaylist].isNullOrEmpty()) {
                originalOrderByPlaylist[selectedPlaylist] = loaded.toList()
            }

            currentListColor = Color.White

            // ✅ calc durée totale (async) — prompter ignoré
            playlistTotalMs = -1L
            val listSnapshot = loaded.toList()
            playlistTotalMs = withContext(Dispatchers.IO) {
                var acc = 0L
                for (u in listSnapshot) {
                    if (u.startsWith("prompter://")) continue
                    val cached = durationCache[u]
                    val d = cached
                        ?: (getAudioDurationMsQP(context, u) ?: 0L).also {
                            durationCache[u] = it
                        }
                    acc += d
                }
                acc
            }
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
                currentListColor = Color.White
                onSelectedPlaylistChange(first)
                // ✅ on ne pousse plus de couleur playlist vers le lecteur
                // onPlaylistColorChange(currentListColor)
            }
        }
    }

    val menuBg = Color(0xFF1B1B1B)

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ─── HEADER encadré + flèche + icônes ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515), RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .semantics { testTag = "quickplaylists_header" }

                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // bloc titre qui prend toute la largeur disponible
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF101010), RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .semantics { testTag = "quickplaylists_header" }
                        .clickable { showMenu = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = internalSelected ?: stringResource(R.string.quickplaylists_select),
                            color = Color(0xFFFFF3E0),
                            fontSize = 18.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        // ✅ durée totale playlist (petit affichage)
                        val durText = when {
                            playlistTotalMs < 0L -> "…"
                            else -> formatDuration(playlistTotalMs)
                        }

                        Text(
                            text = durText,
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, end = 6.dp)
                        )

                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Choisir une playlist",
                            tint = Color(0xFFFFC107)
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
                                        color = Color.White,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    // ➕ création titre texte (ancienne méthode)
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



                    // reset (NE TOUCHE PAS aux "à revoir", seulement "joué")
                    if (internalSelected != null) {
                        IconButton(
                            onClick = {
                                val pl = internalSelected ?: return@IconButton

                                // 1) on efface le statut "joué"
                                PlaylistRepository.resetPlayedFor(pl)

                                // 2) ✅ on restaure l'ordre d'origine (persistant)
                                val original = loadOriginalOrder(context, pl)
                                    ?: PlaylistRepository.getSongsFor(pl)

                                PlaylistRepository.updatePlayListOrder(pl, original)

                                // 3) UI
                                songs.clear()
                                songs.addAll(original)

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



                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── CADRE "RACK" POUR LA LISTE ─────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF101010), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                    .padding(6.dp)
            ) {
                if (internalSelected == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.quickplaylists_empty),
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        itemsIndexed(songs, key = { _, item -> item }) { _, uriString ->
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

                            // 🔹 NOM D’AFFICHAGE
                            val _forceNotes = notesVersion
                            val displayName = if (uriString.startsWith("prompter://")) {
                                val isPrompter = uriString.startsWith("prompter://")
                                val prefix = if (isPrompter) "📝 " else ""   // ou 📜 si tu préfères
                                val idPart = uriString.removePrefix("prompter://")
                                val numericId = idPart.toLongOrNull()

                                if (numericId != null) {
                                    // 👉 NOTE : titre lu dans NotesRepository
                                    val note = NotesRepository.get(context, numericId)
                                    note?.title?.takeIf { it.isNotBlank() } ?: "(Texte)"
                                } else {
                                    // 👉 ancien système TextSongRepository (id non numérique)
                                    val textSong = TextSongRepository.get(context, idPart)
                                    textSong?.title?.takeIf { it.isNotBlank() } ?: baseNameClean
                                }
                            } else {
                                // 👉 Audio normal
                                internalSelected?.let {
                                    PlaylistRepository.getCustomTitle(it, uriString)
                                } ?: baseNameClean
                            }

                            val isPlayed = internalSelected?.let {
                                PlaylistRepository.isSongPlayed(it, uriString)
                            } ?: false

                            val isToReview = internalSelected?.let {
                                PlaylistRepository.isSongToReview(it, uriString)
                            } ?: false

                            // 🔸 couleur custom par titre (force recompose quand songColorsVersion change)
                            val _forceRecompose = songColorsVersion
                            val customSongColor: Color? = internalSelected?.let { pl ->
                                loadSongColor(context, pl, uriString)
                            }

                            val isCurrentPlaying = currentPlayingUri == uriString
                            val isDraggingThis = draggingUri == uriString

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                    .background(
                                        color = if (isDraggingThis)
                                            Color(0x33FFFFFF)
                                        else
                                            Color(0xFF181818),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrentPlaying)
                                            Color.White.copy(alpha = 0.8f)
                                        else
                                            Color(0x33FFFFFF),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Déplacer",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .padding(end = 6.dp)
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
                                                        // ✅ Si tu réordonnes à la main, ce nouvel ordre devient
                                                        // le nouvel "ordre d'origine" (persistant)
                                                        overwriteOriginalOrder(context, pl, songs.toList())
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
                                                    internalSelected?.let { pl ->
                                                        PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                                                    }
                                                    dragOffsetPx = 0f
                                                }
                                                if (dragOffsetPx <= -rowHeightPx / 2f) {
                                                    val prev = currentIndex - 1
                                                    if (prev >= 0) songs.swap(currentIndex, prev)
                                                    internalSelected?.let { pl ->
                                                        PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                                                    }
                                                    dragOffsetPx = 0f
                                                }
                                            }
                                        }
                                )
                                val isPrompter = uriString.startsWith("prompter://")
                                val prefix = if (isPrompter) "📝 " else ""
                                Text(
                                    text = (prefix + displayName).uppercase(),
                                    color = customSongColor
                                        ?: if (isCurrentPlaying) {
                                            Color(0xFFFFFDE7)
                                        } else {
                                            when {
                                                isToReview -> Color(0xFFFF6F6F) // rouge = à revoir
                                                isPlayed -> Color(0xFF8D8D8D)
                                                else -> Color.White
                                            }
                                        },
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val pl = internalSelected ?: return@clickable
                                            // ✅ IMPORTANT : on capture l'ordre "d'origine" AVANT que le système
                                            // ne pousse une chanson jouée en bas.
                                            // Persistant => ça survit au redémarrage.
                                            saveOriginalOrderIfMissing(context, pl, songs.toList())
                                            // ✅ On arme le suivi "10s de lecture réelle"
                                            PlaylistRepository.setNowPlaying(pl, uriString)

                                            // ✅ Lance le player
                                            onPlaySong(uriString, pl, Color.White) // ✅ ne teinte plus le lecteur / paroles
                                            // ⚠️ IMPORTANT : on NE rappelle PAS onSelectedPlaylistChange(pl) ici,
                                            // sinon le parent peut recharger la playlist immédiatement (LaunchedEffect),
                                            // ce qui donne l'impression que le titre "descend direct".
                                            onRequestShowPlayer()

                                        }
                                )

                                // menu 3 points
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .border(
                                                width = 1.dp,
                                                color = if (isPlayed) Color.Gray else currentListColor,
                                                shape = RoundedCornerShape(8.dp)
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
                                        // ✅ Éditer texte prompteur (uniquement si c'est un "prompter://")
                                        if (uriString.startsWith("prompter://")) {
                                            DropdownMenuItem(
                                                text = { Text( "Éditer le prompteur ✅ TEST 2026", color = Color.White) },
                                                onClick = {
                                                    val idPart = uriString.removePrefix("prompter://")
                                                    val numericId = idPart.toLongOrNull()

                                                    if (numericId != null) {
                                                        val note = NotesRepository.get(context, numericId)
                                                        editTextTitle = note?.title.orEmpty()
                                                        editTextContent = note?.content.orEmpty()
                                                    } else {
                                                        val textSong = TextSongRepository.get(context, idPart)
                                                        editTextTitle = textSong?.title.orEmpty()
                                                        editTextContent = textSong?.content.orEmpty()
                                                    }

                                                    editTargetUri = uriString
                                                    showEditTextDialog = true
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                        // 🎨 Couleur du titre (palette)
                                        DropdownMenuItem(
                                            text = { Text("Couleur du titre", color = Color.White) },
                                            onClick = { /* pas d'action, palette dessous */ }
                                        )

                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val colors = listOf(
                                                Color(0xFFD32F2F), // rouge DJ (plus punchy)
                                                Color(0xFFFFEB3B), // JAUNE franc (spot / scène)
                                                Color(0xFF1976D2), // bleu DJ lumineux
                                                Color(0xFFFF9800), // orange scène
                                                Color(0xFF388E3C), // vert console
                                                Color(0xFF7B1FA2), // violet électro
                                                Color(0xFF00ACC1), // cyan club
                                                Color(0xFFE0E0E0)  // gris clair pro (pas blanc pur)
                                            )

                                            // X = revient à la couleur de la playlist
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .background(Color(0xFF2A2A2A), RoundedCornerShape(999.dp))
                                                    .border(1.dp, Color.White, RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        internalSelected?.let { pl ->
                                                            clearSongColor(context, pl, uriString)
                                                            songColorsVersion++
                                                        }
                                                        menuOpen = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("X", color = Color.White, fontSize = 12.sp)
                                            }

                                            colors.forEach { c ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .background(c, RoundedCornerShape(999.dp))
                                                        .border(1.dp, Color.White, RoundedCornerShape(999.dp))
                                                        .clickable {
                                                            internalSelected?.let { pl ->
                                                                saveSongColor(context, pl, uriString, c)
                                                                songColorsVersion++
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
            }
        }
    }

    // ─── DIALOG RENOMMAGE ────────────────────────────────
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
                TextButton(
                    onClick = {
                        val targetUri = renameTarget ?: return@TextButton
                        val pl = internalSelected ?: return@TextButton
                        val newTitle = renameText.trim()
                        if (newTitle.isBlank()) return@TextButton

                        if (targetUri.startsWith("prompter://")) {
                            // 👉 Cas prompteur : on renomme LA SOURCE
                            val idPart = targetUri.removePrefix("prompter://")
                            val numericId = idPart.toLongOrNull()

                            if (numericId != null) {
                                val note = NotesRepository.get(context, numericId)
                                if (note != null) {
                                    NotesRepository.upsert(
                                        context = context,
                                        id = note.id,
                                        title = newTitle,
                                        content = note.content
                                    )
                                    NotesEventBus.notifyNotesChanged()
                                }
                            } else {
                                val textSong = TextSongRepository.get(context, idPart)
                                if (textSong != null) {
                                    TextSongRepository.update(
                                        context = context,
                                        id = idPart,
                                        title = newTitle,
                                        content = textSong.content
                                    )
                                    NotesEventBus.notifyNotesChanged()
                                }
                            }
                        } else {
                            // ✅ Cas audio normal : RENOMMAGE RÉEL DU FICHIER (source unique)
                            if (isRenameBusy) return@TextButton
                            isRenameBusy = true

                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        renameAudioFileUsingLibraryCache(
                                            context = context,
                                            oldUriString = targetUri,
                                            newBaseName = newTitle
                                        )
                                    }

                                    if (result != null) {
                                        val (newUriString, _) = result

                                        // 1) migration des URI partout (playlist + états)
                                        if (newUriString != targetUri) {
                                            PlaylistRepository.replaceSongUriEverywhere(
                                                oldUri = targetUri,
                                                newUri = newUriString
                                            )
                                        }

                                        // 2) on supprime les titres custom qui masquent le nom réel
                                        PlaylistRepository.clearCustomTitleEverywhere(targetUri)
                                        PlaylistRepository.clearCustomTitleEverywhere(newUriString)
                                    }
                                } finally {
                                    isRenameBusy = false
                                    renameTarget = null
                                }
                            }
                        }
                    }
                ) {
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

// dialog création titre texte (ancienne méthode)
    if (showCreateTextDialog && internalSelected != null) {
        AlertDialog(
            onDismissRequest = { showCreateTextDialog = false },
            title = { Text("Nouveau titre (prompteur 2026 )", color = Color.White) },
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

// ✅ dialog ÉDITION titre texte (prompteur) — version LARGE + boutons visibles
    // ✅ Dialog ÉDITION prompteur — grand + boutons toujours visibles
    if (showEditTextDialog && editTargetUri != null) {

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showEditTextDialog = false
                editTargetUri = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.90f)          // ✅ plus haut (90% écran)
                    .navigationBarsPadding()        // ✅ évite barre du bas
                    .imePadding()                   // ✅ évite le clavier
                    .background(Color(0xFF222222), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "Éditer le prompteur ✅ TEST 2026",
                    color = Color.White,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = editTextTitle,
                    onValueChange = { editTextTitle = it },
                    label = { Text("Titre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // ✅ Zone centrale scrollable, prend tout l'espace restant
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .verticalScroll(scroll)
                ) {
                    OutlinedTextField(
                        value = editTextContent,
                        onValueChange = { editTextContent = it },
                        label = { Text("Texte du prompteur") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp),
                        minLines = 10
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ✅ Boutons FIXES en bas : toujours visibles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            showEditTextDialog = false
                            editTargetUri = null
                        }
                    ) {
                        Text("Annuler", color = Color(0xFFB0BEC5))
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val uri = editTargetUri ?: return@TextButton
                            val title = editTextTitle.trim()
                            val content = editTextContent.trim()

                            if (title.isBlank() || content.isBlank()) return@TextButton

                            if (uri.startsWith("prompter://")) {
                                val idPart = uri.removePrefix("prompter://")
                                val numericId = idPart.toLongOrNull()

                                if (numericId != null) {
                                    val note = NotesRepository.get(context, numericId)
                                    if (note != null) {
                                        NotesRepository.upsert(
                                            context = context,
                                            id = note.id,
                                            title = title,
                                            content = content
                                        )
                                    }
                                } else {
                                    val textSong = TextSongRepository.get(context, idPart)
                                    if (textSong != null) {
                                        TextSongRepository.update(
                                            context = context,
                                            id = idPart,
                                            title = title,
                                            content = content
                                        )
                                    }
                                }

                                NotesEventBus.notifyNotesChanged()
                            }

                            showEditTextDialog = false
                            editTargetUri = null
                        }
                    ) {
                        Text("Enregistrer", color = Color.White)
                    }
                }
            }
        }
    }

// ✅ IMPORTANT : cette accolade DOIT fermer QuickPlaylistsScreen()
// Mets-la ici si tu es à la fin de la fonction.
} // <-- FIN QuickPlaylistsScreen()


// ─────────────────────────────────────────────
// Helpers (OBLIGATOIREMENT en dehors du Composable)
// ─────────────────────────────────────────────

// utilitaire drag
private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

// prefs couleur playlist
private const val PLAYLIST_COLOR_PREF = "playlist_color_pref"

private fun savePlaylistColor(context: Context, playlist: String, color: Color) {
    val prefs = context.getSharedPreferences(PLAYLIST_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .putInt(playlist, color.toArgb())
        .apply()
}

private fun loadPlaylistColor(context: Context, playlist: String): Color? {
    val prefs = context.getSharedPreferences(PLAYLIST_COLOR_PREF, Context.MODE_PRIVATE)
    return if (prefs.contains(playlist)) {
        Color(prefs.getInt(playlist, Color(0xFFE86FFF).toArgb()))
    } else null
}

// prefs couleur par TITRE
private const val SONG_COLOR_PREF = "song_color_pref"
// ─────────────────────────────────────────────
// ✅ Sauvegarde ordre "d'origine" d'une playlist (persistant)
// ─────────────────────────────────────────────
private const val PLAYLIST_ORIGINAL_ORDER_PREF = "playlist_original_order_pref"

private fun originalOrderKey(playlist: String): String = "orig|$playlist"

private fun saveOriginalOrderIfMissing(context: Context, playlist: String, order: List<String>) {
    val prefs = context.getSharedPreferences(PLAYLIST_ORIGINAL_ORDER_PREF, Context.MODE_PRIVATE)
    val key = originalOrderKey(playlist)
    if (prefs.contains(key)) return

    val json = org.json.JSONArray().apply { order.forEach { put(it) } }.toString()
    prefs.edit().putString(key, json).apply()
}

private fun overwriteOriginalOrder(context: Context, playlist: String, order: List<String>) {
    val prefs = context.getSharedPreferences(PLAYLIST_ORIGINAL_ORDER_PREF, Context.MODE_PRIVATE)
    val key = originalOrderKey(playlist)
    val json = org.json.JSONArray().apply { order.forEach { put(it) } }.toString()
    prefs.edit().putString(key, json).apply()
}

private fun loadOriginalOrder(context: Context, playlist: String): List<String>? {
    val prefs = context.getSharedPreferences(PLAYLIST_ORIGINAL_ORDER_PREF, Context.MODE_PRIVATE)
    val key = originalOrderKey(playlist)
    val json = prefs.getString(key, null) ?: return null
    return runCatching {
        val arr = org.json.JSONArray(json)
        List(arr.length()) { idx -> arr.getString(idx) }
    }.getOrNull()
}

private fun songColorKey(playlist: String, uri: String): String = "$playlist|$uri"

private fun saveSongColor(context: Context, playlist: String, uri: String, color: Color) {
    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .putInt(songColorKey(playlist, uri), color.toArgb())
        .apply()
}

private fun loadSongColor(context: Context, playlist: String, uri: String): Color? {
    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    val key = songColorKey(playlist, uri)
    return if (prefs.contains(key)) {
        Color(prefs.getInt(key, Color.White.toArgb()))
    } else null
}

private fun clearSongColor(context: Context, playlist: String, uri: String) {
    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(songColorKey(playlist, uri))
        .apply()
}


/**
 * Renomme un fichier audio en s'appuyant sur le cache d'index de la bibliothèque.
 * Retourne Pair(newUriString, newFileNameFinal) si OK, sinon null.
 */
private fun renameAudioFileUsingLibraryCache(
    context: Context,
    oldUriString: String,
    newBaseName: String
): Pair<String, String>? {
    val cache = LibraryIndexCache.load(context) ?: return null
    val entry = cache.firstOrNull { it.uriString == oldUriString } ?: return null

    val parentUri = entry.parentUriString?.let { Uri.parse(it) } ?: return null

    val oldName = entry.name
    val ext = oldName.substringAfterLast('.', "")
    val cleanBase = newBaseName.trim()
    if (cleanBase.isEmpty()) return null

    val newNameFinal =
        if (ext.isNotEmpty() && !cleanBase.contains(".")) "$cleanBase.$ext" else cleanBase

    val parentDoc = DocumentFile.fromTreeUri(context, parentUri) ?: return null
    val fileDoc = parentDoc.findFile(oldName) ?: return null

    val ok = try {
        fileDoc.renameTo(newNameFinal)
    } catch (_: Exception) {
        false
    }
    if (!ok) return null

    // URI peut changer => on le recherche par nom dans le dossier
    val newUri = findUriByNameInFolder(context, parentUri, newNameFinal)
    val finalUriString = (newUri ?: Uri.parse(oldUriString)).toString()

    // Mise à jour du cache bibliothèque (nom + éventuellement uri)
    val newCache = cache.map { ce ->
        if (ce.uriString == oldUriString) {
            if (finalUriString != oldUriString) {
                ce.copy(uriString = finalUriString, name = newNameFinal)
            } else {
                ce.copy(name = newNameFinal)
            }
        } else ce
    }
    LibraryIndexCache.save(context, newCache)

    return finalUriString to newNameFinal
}
// ✅ Helpers durée audio (AU NIVEAU FICHIER)
private fun getAudioDurationMsQP(context: Context, uriString: String): Long? {
    return runCatching {
        val uri = Uri.parse(uriString)
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        mmr.release()
        durStr?.toLongOrNull()
    }.getOrNull()
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ✅ Bus simple pour forcer le refresh des notes dans Compose
object NotesEventBus {
    private val listeners = mutableListOf<() -> Unit>()

    fun subscribe(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun notifyNotesChanged() {
        listeners.forEach { it.invoke() }
    }
}

