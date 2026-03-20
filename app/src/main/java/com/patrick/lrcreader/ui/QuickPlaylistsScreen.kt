package com.patrick.lrcreader.ui


import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.patrick.lrcreader.exo.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.MiniTunerVisibilityStore
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TunerEngine
import com.patrick.lrcreader.core.TunerState
import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.getGroupUuid
import com.patrick.lrcreader.core.getGroupTitle
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.isPlayableAudioItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.renameGroupHeader
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.smp.SmpLibraryScanner
import kotlinx.coroutines.yield
import java.net.URLDecoder

/**
 * QuickPlaylistsScreen + titres "texte seul" (prompteur).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String, Color) -> Unit,
    onPlayFromHere: (List<String>, Int, String) -> Unit = { _, _, _ -> },
    refreshKey: Int,
    openPrompterSignal: Int = 0,
    libraryLoadedSignal: Int = 0,
    playlistsReady: Boolean = true,
    nextChainedUri: String? = null,
    nextTrackUri: String? = null,
    currentPlayingUri: String? = null,
    selectedPlaylist: String? = null,
    openedPlaylist: String? = null,
    isRestoringSession: Boolean = false,
    onSelectedPlaylistChange: (String?) -> Unit = {},
    onPlaylistColorChange: (Color) -> Unit = {},
    onSetNextTrack: (uri: String, title: String, playlist: String?) -> Unit = { _, _, _ -> },
    onClearNextTrack: () -> Unit = {},
    onConsumeOpenPrompterSignal: () -> Unit = {},
    onRequestShowPlayer: () -> Unit = {},
    onAddTrackToPlaylist: (String) -> Unit = {},
    indexAll: List<LibraryIndexCache.CachedEntry> = emptyList() // ✅ propre + default
) {

    val context = LocalContext.current
    val smpLibraryScanner = remember(context) { SmpLibraryScanner(context) }
    val sQuickplaylistsNewGroupDefault = stringResource(R.string.quickplaylists_group_new_default)
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasMicPermission = granted }
    )

    val scope = rememberCoroutineScope()
    val titleAliasVersion = TitleAliasesStore.version.intValue

// ✅ IMPORTANT : on observe le repo RAM (sinon la playlist garde des URI "morts" après rename en bibliothèque)
    val repoVersion = PlaylistRepository.version.value

// ✅ la liste des playlists se met à jour dès que le repo change
    val playlists = remember(refreshKey, repoVersion) { PlaylistRepository.getPlaylists() }

    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: openedPlaylist ?: playlists.firstOrNull())
    }
    val resolvedPlaylistSelection = internalSelected ?: selectedPlaylist ?: openedPlaylist ?: playlists.firstOrNull()
    val isMiniTunerVisible by MiniTunerVisibilityStore.state(context).collectAsState()

    val songs = remember { mutableStateListOf<String>() }
    val smpSongIdsInPlaylist by remember {
        derivedStateOf {
            songs.mapNotNull { getSmpSongId(it) }.toSet()
        }
    }
    val smpTitleById = remember(refreshKey, libraryLoadedSignal, repoVersion, smpSongIdsInPlaylist) {
        if (smpSongIdsInPlaylist.isEmpty()) {
            emptyMap()
        } else {
            smpLibraryScanner.listSongs()
                .asSequence()
                .filter { it.id in smpSongIdsInPlaylist }
                .associate { it.id to it.title.ifBlank { it.id } }
        }
    }
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
    val headerDropPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragYInListViewport by remember { mutableStateOf<Float?>(null) }
    var hoverHeaderKey by remember { mutableStateOf<String?>(null) }
    var collapsedGroupIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }
    var renameGroupText by remember { mutableStateOf("") }
    var selectedTrackKeys by remember { mutableStateOf(setOf<String>()) }
    var assignGroupTargetUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var assignGroupOptions by remember { mutableStateOf<List<GroupAssignOption>>(emptyList()) }

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
    var previousSongsSize by remember { mutableIntStateOf(0) }
    val portableStampByPlaylist = remember { mutableStateMapOf<String, String>() }
    var quickEnterAtMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        quickEnterAtMs = SystemClock.elapsedRealtime()
        Log.d(
            "BOOTSTEP",
            "QuickPlaylists.enter nowMs=$quickEnterAtMs selected=$selectedPlaylist opened=$openedPlaylist"
        )
    }

    // Abonnement aux changements de notes
    LaunchedEffect(Unit) {
        NotesEventBus.subscribe {
            notesVersion++
        }
    }

    LaunchedEffect(openPrompterSignal) {
        if (openPrompterSignal > 0 && internalSelected != null) {
            newTextTitle = ""
            newTextContent = ""
            showCreateTextDialog = true
            onConsumeOpenPrompterSignal()
        }
    }

    // recharge quand playlist ou notes changent
    LaunchedEffect(internalSelected, refreshKey, notesVersion, repoVersion, libraryLoadedSignal, playlistsReady, titleAliasVersion) {
        val pl = internalSelected
        Log.d(
            "BOOTSTEP",
            "QuickPlaylists.enter openedPlaylist=$pl selectedPlaylist=$selectedPlaylist reason=internalSelected"
        )
        if (pl != null) {
            val raw = PlaylistRepository.getAllSongsRaw(pl)
            if (!playlistsReady) {
                songs.clear()
                songs.addAll(raw)
                Log.d(
                    "BOOTSTEP",
                    "QuickPlaylists.wait playlistsReady=false playlist=$pl rawSize=${raw.size} keepFallback=true"
                )
                return@LaunchedEffect
            }

            songs.clear()
            // ✅ fallback immédiat sans dépendance LibraryIndexCache
            songs.addAll(raw)
            if (raw.isNotEmpty()) yield()

            val portableStamp = "$refreshKey|$repoVersion|$libraryLoadedSignal|${raw.hashCode()}"
            if (portableStampByPlaylist[pl] != portableStamp) {
                val portableStart = SystemClock.elapsedRealtime()
                val restoredManual = withContext(Dispatchers.Default) {
                    loadManualOrder(context, pl, raw)
                }
                var portableApplied = false
                if (restoredManual != null && restoredManual != raw) {
                    PlaylistRepository.updatePlayListOrder(pl, restoredManual)
                    portableApplied = true
                }
                portableStampByPlaylist[pl] = portableStamp
                Log.d(
                    "BOOTSTEP",
                    "QuickPlaylists.applyPortableOrder playlist=$pl applied=$portableApplied ms=${SystemClock.elapsedRealtime() - portableStart} rawSize=${raw.size}"
                )
            }

            val getSongsStart = SystemClock.elapsedRealtime()
            Log.d("BOOTSTEP", "QuickPlaylists.getSongsFor:before playlist=$pl")
            val loaded = PlaylistRepository.getSongsFor(pl)
            Log.d(
                "BOOTSTEP",
                "QuickPlaylists.getSongsFor:after playlist=$pl size=${loaded.size} ms=${SystemClock.elapsedRealtime() - getSongsStart}"
            )
            if (loaded.isNotEmpty()) {
                songs.clear()
                songs.addAll(loaded)
            } else {
                Log.d("BOOTSTEP", "QuickPlaylists.getSongsFor:empty playlist=$pl -> keep RAW fallback")
            }

            // ✅ Si on n'a pas encore d'ordre "d'origine" pour cette playlist, on le mémorise
            if (originalOrderByPlaylist[pl].isNullOrEmpty()) {
                val originalLoaded = withContext(Dispatchers.Default) {
                    loadOriginalOrder(context, pl)
                }
                originalOrderByPlaylist[pl] = originalLoaded ?: loaded.toList()
            }

            currentListColor = Color.White

            // ✅ calc durée totale (async) — prompter ignoré
            playlistTotalMs = -1L
            val listSnapshot = loaded.toList()
            playlistTotalMs = withContext(Dispatchers.IO) {
                var acc = 0L
                for (u in listSnapshot) {
                    if (!isPlayableAudioItem(u)) continue
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
    LaunchedEffect(selectedPlaylist, openedPlaylist, playlists) {
        val targetPlaylist = selectedPlaylist ?: openedPlaylist ?: playlists.firstOrNull()
        if (targetPlaylist != null) {
            if (targetPlaylist == internalSelected && songs.isNotEmpty()) {
                Log.d("BOOTSTEP", "QuickPlaylists.parentTarget:skip duplicate target=$targetPlaylist size=${songs.size}")
                return@LaunchedEffect
            }
            Log.d(
                "BOOTSTEP",
                "QuickPlaylists.enter openedPlaylist=$openedPlaylist selectedPlaylist=$selectedPlaylist reason=parentTarget target=$targetPlaylist"
            )
            internalSelected = targetPlaylist
            songs.clear()
            val raw = PlaylistRepository.getAllSongsRaw(targetPlaylist)
            songs.addAll(raw)
            Log.d(
                "BOOTSTEP",
                "QuickPlaylists.parentTarget:fallbackApplied playlist=$targetPlaylist rawSize=${raw.size}"
            )
        }
    }

    // si la liste de playlists change
    LaunchedEffect(playlists, playlistsReady) {
        if (internalSelected !in playlists) {
            val first = playlists.firstOrNull()
            internalSelected = first
            songs.clear()
            if (first != null) {
                if (!playlistsReady) {
                    val raw = PlaylistRepository.getAllSongsRaw(first)
                    songs.addAll(raw)
                    Log.d(
                        "BOOTSTEP",
                        "QuickPlaylists.wait playlistsReady=false playlist=$first rawSize=${raw.size} reason=playlistsChanged"
                    )
                    onSelectedPlaylistChange(first)
                    return@LaunchedEffect
                }
                val getSongsStart = SystemClock.elapsedRealtime()
                Log.d("BOOTSTEP", "QuickPlaylists.getSongsFor:before playlist=$first reason=playlistsChanged")
                songs.addAll(PlaylistRepository.getSongsFor(first))
                Log.d(
                    "BOOTSTEP",
                    "QuickPlaylists.getSongsFor:after playlist=$first size=${songs.size} ms=${SystemClock.elapsedRealtime() - getSongsStart} reason=playlistsChanged"
                )
                currentListColor = Color.White
                onSelectedPlaylistChange(first)
                // ✅ on ne pousse plus de couleur playlist vers le lecteur
                // onPlaylistColorChange(currentListColor)
            }
        }
    }

    LaunchedEffect(songs.size) {
        selectedTrackKeys = selectedTrackKeys.filterTo(linkedSetOf()) { key ->
            songs.contains(key) && isPlayableAudioItem(key)
        }
        if (previousSongsSize == 0 && songs.size > 0) {
            val now = SystemClock.elapsedRealtime()
            val delta = if (quickEnterAtMs > 0L) now - quickEnterAtMs else -1L
            Log.d(
                "BOOTSTEP",
                "QuickPlaylists:firstSongsShown nowMs=$now deltaFromEnterMs=$delta playlist=$internalSelected opened=$openedPlaylist size=${songs.size}"
            )
        }
        previousSongsSize = songs.size
    }

    val menuBg = Color(0xFF1B1B1B)

    fun persistSongsOrder(playlist: String, overwriteOriginal: Boolean = false) {
        val snapshot = songs.toList()
        PlaylistRepository.updatePlayListOrder(playlist, snapshot)
        saveManualOrder(context, playlist, snapshot)
        if (overwriteOriginal) {
            overwriteOriginalOrder(context, playlist, snapshot)
        }
    }

    fun openAssignDialogForTargets(targets: List<String>) {
        if (targets.isEmpty()) return
        val options = songs.asSequence()
            .filter { isGroupHeader(it) }
            .map { GroupAssignOption(headerKey = it, title = getGroupTitle(it)) }
            .toList()
        if (options.isNotEmpty()) {
            assignGroupTargetUris = targets
            assignGroupOptions = options
        }
    }

    fun removeTargetsFromGroup(targets: List<String>) {
        val pl = internalSelected ?: return
        val movedUris = moveTracksOutOfGroup(items = songs, trackUris = targets)
        if (movedUris.isNotEmpty()) {
            persistSongsOrder(pl)
            selectedTrackKeys = selectedTrackKeys - movedUris
        }
    }

    fun dragHandleModifier(itemKey: String): Modifier {
        return Modifier.pointerInput(songs.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    draggingUri = itemKey
                    dragOffsetPx = 0f
                    val visibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        (info.key as? String) == itemKey
                    }
                    dragYInListViewport = visibleInfo?.let { info ->
                        info.offset + (info.size / 2f)
                    }
                    hoverHeaderKey = dragYInListViewport?.let { dragY ->
                        val viewportItems = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                            val key = info.key as? String ?: return@mapNotNull null
                            ListViewportItem(key = key, start = info.offset, endExclusive = info.offset + info.size)
                        }
                        findHeaderDropTargetKey(
                            songs = songs,
                            viewportItems = viewportItems,
                            dragY = dragY,
                            draggedItemKey = itemKey,
                            headerPaddingPx = headerDropPaddingPx
                        )
                    }
                },
                onDragEnd = {
                    val dragged = draggingUri
                    val hoverHeader = hoverHeaderKey
                    if (
                        dragged != null &&
                        isPlayableAudioItem(dragged) &&
                        hoverHeader != null &&
                        isGroupHeader(hoverHeader)
                    ) {
                        val fromIndex = songs.indexOf(dragged)
                        val headerIndex = songs.indexOf(hoverHeader)
                        moveItemIntoGroup(
                            items = songs,
                            fromIndex = fromIndex,
                            headerIndex = headerIndex,
                            mode = "BOTTOM"
                        )
                    }
                    draggingUri = null
                    dragOffsetPx = 0f
                    dragYInListViewport = null
                    hoverHeaderKey = null
                    internalSelected?.let { pl ->
                        persistSongsOrder(pl, overwriteOriginal = true)
                    }
                },
                onDragCancel = {
                    draggingUri = null
                    dragOffsetPx = 0f
                    dragYInListViewport = null
                    hoverHeaderKey = null
                }
            ) { _, dragAmount ->
                val current = draggingUri ?: return@detectDragGesturesAfterLongPress
                val currentIndex = songs.indexOf(current)
                if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                val baseY = dragYInListViewport ?: listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> (info.key as? String) == current }
                    ?.let { info -> info.offset + (info.size / 2f) }
                    ?: return@detectDragGesturesAfterLongPress
                dragYInListViewport = baseY + dragAmount.y

                val viewportItems = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val key = info.key as? String ?: return@mapNotNull null
                    ListViewportItem(key = key, start = info.offset, endExclusive = info.offset + info.size)
                }
                hoverHeaderKey = findHeaderDropTargetKey(
                    songs = songs,
                    viewportItems = viewportItems,
                    dragY = dragYInListViewport ?: baseY,
                    draggedItemKey = current,
                    headerPaddingPx = headerDropPaddingPx
                )

                dragOffsetPx += dragAmount.y
                val lockReorderForDrop = isPlayableAudioItem(current) && hoverHeaderKey != null
                if (lockReorderForDrop) {
                    dragOffsetPx = 0f
                    return@detectDragGesturesAfterLongPress
                }

                if (dragOffsetPx >= rowHeightPx / 2f) {
                    if (isGroupHeader(current)) {
                        val range = findGroupBlockRange(songs, currentIndex)
                        val next = if (range.isEmpty()) null else findNextReorderIndex(songs, range.last, +1)
                        if (next != null) {
                            val nextItem = songs[next]
                            val newStart = if (isGroupHeader(nextItem)) {
                                val targetRange = findGroupBlockRange(songs, next)
                                if (targetRange.isEmpty()) next + 1 else targetRange.last + 1
                            } else {
                                next + 1
                            }
                            moveBlock(songs, range, newStart)
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        }
                    } else {
                        val next = findNextTrackReorderIndex(songs, currentIndex, +1)
                        if (next != null) {
                            songs.swap(currentIndex, next)
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        }
                    }
                    dragOffsetPx = 0f
                }
                if (dragOffsetPx <= -rowHeightPx / 2f) {
                    if (isGroupHeader(current)) {
                        val range = findGroupBlockRange(songs, currentIndex)
                        val prev = if (range.isEmpty()) null else findNextReorderIndex(songs, range.first, -1)
                        if (prev != null) {
                            val prevItem = songs[prev]
                            val newStart = if (isGroupHeader(prevItem)) {
                                val targetRange = findGroupBlockRange(songs, prev)
                                if (targetRange.isEmpty()) prev else targetRange.first
                            } else {
                                prev
                            }
                            moveBlock(songs, range, newStart)
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        }
                    } else {
                        val prev = findNextTrackReorderIndex(songs, currentIndex, -1)
                        if (prev != null) {
                            songs.swap(currentIndex, prev)
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        }
                    }
                    dragOffsetPx = 0f
                }
            }
        }
    }

    val visibleRows by remember {
        derivedStateOf {
            songs.mapIndexedNotNull { realIndex, item ->
                if (isGroupEnd(item)) return@mapIndexedNotNull null
                if (isItemHiddenByCollapsedGroup(songs, realIndex, collapsedGroupIds)) return@mapIndexedNotNull null
                VisiblePlaylistRow(realIndex = realIndex, item = item)
            }
        }
    }
    val miniTunerState: TunerState = if (isMiniTunerVisible) {
        TunerEngine.state.collectAsState().value
    } else {
        TunerState()
    }

    DisposableEffect(isMiniTunerVisible, hasMicPermission) {
        if (isMiniTunerVisible && hasMicPermission) {
            TunerEngine.start()
        }
        onDispose {
            if (isMiniTunerVisible && hasMicPermission) {
                TunerEngine.stop()
            }
        }
    }

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics { testTag = "quick_playlists_root" }
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
                            contentDescription = stringResource(R.string.common_cd_choose_playlist),
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
                                saveManualOrder(context, pl, original)

                                // 3) UI
                                songs.clear()
                                songs.addAll(original)

                                onSelectedPlaylistChange(pl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.common_cd_reset),
                                tint = Color(0xFFFFB74D)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val next = !isMiniTunerVisible
                            MiniTunerVisibilityStore.setVisible(context, next)
                            if (next && !hasMicPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isMiniTunerVisible) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = if (isMiniTunerVisible) {
                                stringResource(R.string.quickplaylists_cd_hide_mini_tuner)
                            } else {
                                stringResource(R.string.quickplaylists_cd_show_mini_tuner)
                            },
                            tint = if (isMiniTunerVisible) Color(0xFF80DEEA) else Color(0xFF78909C)
                        )
                    }

                }
            }

            Spacer(Modifier.height(12.dp))

            resolvedPlaylistSelection?.let { currentPlaylist ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { onAddTrackToPlaylist(currentPlaylist) }) {
                        Text(stringResource(R.string.quickplaylists_add_track_button))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (isMiniTunerVisible) {
                val tunerCents = miniTunerState.cents?.toFloat()
                val miniTunerActive = hasMicPermission && miniTunerState.isListening
                val hasSignal = tunerCents != null && miniTunerState.noteName != "—"

                MiniTunerRow(
                    noteString = miniTunerState.noteName,
                    centsOffset = tunerCents ?: 0f,
                    isInTune = isTunerInTune(tunerCents),
                    hasSignal = hasSignal,
                    isActive = miniTunerActive,
                    onEnable = {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))
            }

            if (!internalSelected.isNullOrBlank() && songs.isEmpty() && (isRestoringSession || !playlistsReady)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22111111), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFFC107)
                    )
                    Text(
                        text = if (!playlistsReady) {
                            stringResource(R.string.quickplaylists_loading_playlists)
                        } else {
                            stringResource(R.string.quickplaylists_restoring_session)
                        },
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

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
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTag = "quick_playlists_list" },
                        state = listState
                    ) {
                        itemsIndexed(visibleRows, key = { _, row -> row.item }) { _, row ->
                            val itemIndex = row.realIndex
                            val uriString = row.item

                            if (isGroupHeader(uriString)) {
                                val groupTitle = getGroupTitle(uriString).uppercase()
                                val headerKey = uriString
                                val isDraggingThis = draggingUri == uriString
                                val isCollapsed = collapsedGroupIds.contains(headerKey)
                                val isDraggingTrack = draggingUri?.let { !isGroupHeader(it) } == true
                                val isDropTargetHeader = isDraggingTrack && hoverHeaderKey == uriString
                                val groupRange = findGroupRange(songs, itemIndex)
                                val groupTrackCount = if (groupRange.isEmpty()) {
                                    0
                                } else {
                                    (groupRange.first + 1..groupRange.last).count { idx ->
                                        !isGroupHeader(songs[idx]) && !isGroupEnd(songs[idx])
                                    }
                                }
                                val folderBlue = Color(0xFF0A6C97)
                                val folderBlueBorder = Color(0xFF07506F)
                                val headerText = Color.White
                                val headerMuted = Color.White.copy(alpha = 0.75f)
                                val headerChevron = Color.White.copy(alpha = 0.60f)
                                val badgeBg = Color.White.copy(alpha = 0.18f)
                                val badgeBorder = Color.White.copy(alpha = 0.30f)
                                val dragTint = if (isDraggingThis) headerText else headerMuted
                                val rowBorder = if (isDropTargetHeader) {
                                    Color.White.copy(alpha = 0.70f)
                                } else {
                                    folderBlueBorder
                                }
                                val rowBackground = if (isDropTargetHeader) {
                                    Color(0xFF1184B8)
                                } else {
                                    folderBlue
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight)
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(rowBackground)
                                        .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.common_cd_move),
                                        tint = dragTint,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .padding(end = 6.dp)
                                            .alpha(0.9f)
                                            .then(dragHandleModifier(uriString))
                                    )

                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = headerText,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 8.dp)
                                    )

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                collapsedGroupIds = if (isCollapsed) {
                                                    collapsedGroupIds - headerKey
                                                } else {
                                                    collapsedGroupIds + headerKey
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = groupTitle,
                                            color = headerText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = groupTrackCount.toString(),
                                            color = headerMuted,
                                            fontSize = 11.sp
                                        )
                                        if (isDropTargetHeader) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(Color.White.copy(alpha = 0.22f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = Color.White.copy(alpha = 0.48f),
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.quickplaylists_drop_here),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isCollapsed) "▶" else "▼",
                                        color = headerChevron,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )

                                    Box {
                                        var menuOpen by remember { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .border(
                                                    width = 1.dp,
                                                    color = badgeBorder,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { menuOpen = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = stringResource(R.string.common_cd_options),
                                                tint = headerMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = menuOpen,
                                            onDismissRequest = { menuOpen = false },
                                            modifier = Modifier.background(Color(0xFF1E1E1E))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.common_rename), color = Color.White) },
                                                onClick = {
                                                    renameGroupTarget = uriString
                                                    renameGroupText = groupTitle
                                                    menuOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.quickplaylists_menu_delete_group),
                                                        color = Color(0xFFFF8A80)
                                                    )
                                                },
                                                onClick = {
                                                    internalSelected?.let { pl ->
                                                        val headerIndex = songs.indexOf(uriString)
                                                        if (removeGroupAtHeader(songs, headerIndex)) {
                                                            collapsedGroupIds = collapsedGroupIds - headerKey
                                                            persistSongsOrder(pl)
                                                        }
                                                    }
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                                return@itemsIndexed
                            }

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
                                    note?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.quickplaylists_text_fallback_title)
                                } else {
                                    // 👉 ancien système TextSongRepository (id non numérique)
                                    val textSong = TextSongRepository.get(context, idPart)
                                    textSong?.title?.takeIf { it.isNotBlank() } ?: baseNameClean
                                }
                            } else {
                                val smpSongId = getSmpSongId(uriString)
                                if (smpSongId != null) {
                                    PlaylistRepository.getAnyCustomTitleForUri(uriString)
                                        ?: smpTitleById[smpSongId]
                                        ?: "SMP $smpSongId"
                                } else {
                                    // 👉 Audio normal (alias global)
                                    TitleAliasesStore.getTitleForTrack(context, uriString)
                                        ?: PlaylistRepository.getAnyCustomTitleForUri(uriString)
                                        ?: baseNameClean
                                }
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
                            val isChainedNext = nextChainedUri != null && uriString == nextChainedUri
                            val isForcedNext = nextTrackUri != null && uriString == nextTrackUri
                            val isSelected = selectedTrackKeys.contains(uriString)
                            val isInsideGroup =
                                isPlayableAudioItem(uriString) && isItemInsideGroup(songs, itemIndex)
                            val rowShape = RoundedCornerShape(12.dp)
                            val groupTint = Color(0xFF0A6C97).copy(alpha = 0.38f)
                            val groupAccent = Color(0xFF0A6C97).copy(alpha = 0.95f)
                            val rowBaseBackground = if (isDraggingThis)
                                Color(0x33FFFFFF)
                            else if (isSelected)
                                Color(0x2239B7FF)
                            else if (isForcedNext)
                                Color(0x33D32F2F)
                            else if (isChainedNext)
                                Color(0x22FFFFFF)
                            else
                                Color(0xFF181818)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                    .background(
                                        color = rowBaseBackground,
                                        shape = rowShape
                                    )
                                    .then(
                                        if (isInsideGroup) {
                                            Modifier.background(color = groupTint, shape = rowShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected)
                                            Color(0xAA4FC3F7)
                                        else if (isCurrentPlaying)
                                            Color.White.copy(alpha = 0.8f)
                                        else if (isForcedNext)
                                            Color(0x99FF8A80)
                                        else if (isChainedNext)
                                            Color(0x66FFD54F)
                                        else
                                            Color(0x33FFFFFF),
                                        shape = rowShape
                                    )
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.common_cd_move),
                                    tint = if (isPlayed) Color(0xFF9E9E9E) else Color.White,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .padding(end = 6.dp)
                                        .alpha(if (isPlayed) 0.6f else 1f)
                                        .then(dragHandleModifier(uriString))
                                )
                                if (isInsideGroup) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .width(3.dp)
                                            .height(26.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(groupAccent)
                                    )
                                }
                                val isPrompter = uriString.startsWith("prompter://")
                                val prefix = if (isPrompter) "📝 " else ""
                                val playedTextColor = Color(0xFF9E9E9E)
                                val normalTitleColor = when {
                                    isToReview -> Color(0xFFFF6F6F) // rouge = a revoir
                                    else -> Color.White
                                }
                                val titleColor = when {
                                    isPlayed -> playedTextColor
                                    isCurrentPlaying -> Color(0xFFFFFDE7)
                                    customSongColor != null -> customSongColor
                                    else -> normalTitleColor
                                }
                                Text(
                                    text = (prefix + displayName).uppercase(),
                                    color = titleColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .alpha(if (isPlayed) 0.6f else 1f)
                                        .combinedClickable(
                                            onClick = {
                                                val pl = internalSelected ?: return@combinedClickable
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
                                            },
                                            onLongClick = {
                                                if (!isPlayableAudioItem(uriString)) return@combinedClickable
                                                selectedTrackKeys = if (selectedTrackKeys.contains(uriString)) {
                                                    selectedTrackKeys - uriString
                                                } else {
                                                    selectedTrackKeys + uriString
                                                }
                                            }
                                        )
                                )

                                if (isForcedNext) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .background(
                                                color = Color(0xFFD32F2F),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.quickplaylists_badge_next_forced),
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                } else if (isChainedNext) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .background(
                                                color = Color(0xFFFDD835),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.quickplaylists_badge_next),
                                            color = Color(0xFF111111),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // menu 3 points
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .border(
                                                width = 1.dp,
                                                color = if (isPlayed) playedTextColor else currentListColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .alpha(if (isPlayed) 0.6f else 1f)
                                            .clickable { menuOpen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.common_cd_options),
                                            tint = if (isPlayed) playedTextColor else currentListColor,
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
                                                    stringResource(R.string.quickplaylists_menu_play),
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                val pl = internalSelected
                                                if (pl != null) {
                                                    val visibleQueue = songs.toList()
                                                    val startIndex = visibleQueue.indexOf(uriString)
                                                    if (startIndex >= 0) {
                                                        onPlayFromHere(visibleQueue, startIndex, pl)
                                                    }
                                                }
                                                menuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.quickplaylists_menu_insert_group_above),
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                val pl = internalSelected
                                                if (pl != null) {
                                                    val index = songs.indexOf(uriString)
                                                    if (index >= 0) {
                                                        val header = buildGroupHeader(sQuickplaylistsNewGroupDefault)
                                                        val end = getGroupUuid(header)?.let { buildGroupEnd(it) }
                                                        songs.add(index, header)
                                                        if (end != null) {
                                                            songs.add(index + 1, end)
                                                        }
                                                        persistSongsOrder(pl)
                                                        renameGroupTarget = header
                                                        renameGroupText = getGroupTitle(header)
                                                    }
                                                }
                                                menuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.quickplaylists_menu_assign_to_group), color = Color.White) },
                                            onClick = {
                                                val selectedBatch = songs.filter { key ->
                                                    key in selectedTrackKeys && isPlayableAudioItem(key)
                                                }
                                                val targets = if (
                                                    uriString in selectedTrackKeys &&
                                                    selectedBatch.size > 1
                                                ) {
                                                    selectedBatch
                                                } else {
                                                    listOf(uriString)
                                                }
                                                openAssignDialogForTargets(targets)
                                                menuOpen = false
                                            },
                                            enabled = songs.any { isGroupHeader(it) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.quickplaylists_menu_set_next), color = Color.White) },
                                            onClick = {
                                                onSetNextTrack(
                                                    uriString,
                                                    displayName,
                                                    internalSelected
                                                )
                                                menuOpen = false
                                            }
                                        )
                                        if (isInsideGroup) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.quickplaylists_menu_remove_from_group),
                                                        color = Color.White
                                                    )
                                                },
                                                onClick = {
                                                    val selectedBatch = songs.filter { key ->
                                                        key in selectedTrackKeys && isPlayableAudioItem(key)
                                                    }
                                                    val targets = if (
                                                        uriString in selectedTrackKeys &&
                                                        selectedBatch.size > 1
                                                    ) {
                                                        selectedBatch
                                                    } else {
                                                        listOf(uriString)
                                                    }
                                                    removeTargetsFromGroup(targets)
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                        if (nextTrackUri != null) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.quickplaylists_menu_cancel_next), color = Color.White) },
                                                onClick = {
                                                    onClearNextTrack()
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.quickplaylists_menu_remove_from_playlist),
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
                                            text = { Text(stringResource(R.string.common_rename), color = Color.White) },
                                            onClick = {
                                                renameTarget = uriString
                                                renameText = displayName
                                                menuOpen = false
                                            }
                                        )
                                        // ✅ Éditer texte prompteur (uniquement si c'est un "prompter://")
                                        if (uriString.startsWith("prompter://")) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.quickplaylists_edit_prompter_title), color = Color.White) },
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
                                            text = { Text(stringResource(R.string.quickplaylists_menu_title_color), color = Color.White) },
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

    // ─── DIALOG RENOMMAGE GROUPE ─────────────────────────
    if (renameGroupTarget != null && internalSelected != null) {
        val commitGroupRename: () -> Unit = commit@{
            val target = renameGroupTarget ?: return@commit
            val newTitle = renameGroupText.trim()
            if (newTitle.isBlank()) return@commit

            val pl = internalSelected ?: return@commit
            val index = songs.indexOf(target)
            if (index >= 0 && isGroupHeader(songs[index])) {
                songs[index] = renameGroupHeader(songs[index], newTitle)
                persistSongsOrder(pl)
            }
            renameGroupTarget = null
        }

        AlertDialog(
            onDismissRequest = { renameGroupTarget = null },
            title = { Text(stringResource(R.string.common_rename), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameGroupText,
                    onValueChange = { renameGroupText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitGroupRename() })
                )
            },
            confirmButton = {
                TextButton(onClick = commitGroupRename) {
                    Text(stringResource(R.string.common_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameGroupTarget = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ─── DIALOG ATTRIBUER AU GROUPE ──────────────────────
    if (assignGroupTargetUris.isNotEmpty() && internalSelected != null) {
        AlertDialog(
            onDismissRequest = {
                assignGroupTargetUris = emptyList()
                assignGroupOptions = emptyList()
            },
            title = { Text(stringResource(R.string.quickplaylists_assign_group_title), color = Color.White) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    assignGroupOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                val pl = internalSelected
                                val targetUris = assignGroupTargetUris
                                if (pl != null && targetUris.isNotEmpty()) {
                                    val movedCount = assignTracksToGroupByHeaderKey(
                                        items = songs,
                                        trackUris = targetUris,
                                        headerKey = option.headerKey
                                    )
                                    if (movedCount > 0) {
                                        persistSongsOrder(pl)
                                        selectedTrackKeys = emptySet()
                                    }
                                }
                                assignGroupTargetUris = emptyList()
                                assignGroupOptions = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.title, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        assignGroupTargetUris = emptyList()
                        assignGroupOptions = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ─── DIALOG RENOMMAGE ────────────────────────────────
    if (renameTarget != null && internalSelected != null) {
        val commitAliasRename: () -> Unit = commit@{
            val targetUri = renameTarget ?: return@commit
            val newTitle = renameText.trim()
            if (newTitle.isBlank()) return@commit

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
                if (BuildConfig.DEBUG) {
                    Log.d("ALIAS_RENAME", "commit source=playlist uri=$targetUri newTitle='$newTitle'")
                }
                val saved = TitleAliasesStore.setTitleForTrack(context, targetUri, newTitle)
                if (saved) {
                    PlaylistRepository.clearCustomTitleEverywhere(targetUri)
                }
                if (BuildConfig.DEBUG) {
                    Toast.makeText(
                        context,
                        if (saved) "Alias enregistré" else "Alias NON enregistré (voir logs)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            renameTarget = null
        }

        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.common_rename), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitAliasRename() })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = commitAliasRename
                ) {
                    Text(stringResource(R.string.common_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

// dialog création titre texte (ancienne méthode)
    if (showCreateTextDialog && internalSelected != null) {
        AlertDialog(
            onDismissRequest = { showCreateTextDialog = false },
            title = { Text(stringResource(R.string.quickplaylists_new_prompter_title), color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTextTitle,
                        onValueChange = { newTextTitle = it },
                        label = { Text(stringResource(R.string.common_title_label)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTextContent,
                        onValueChange = { newTextContent = it },
                        label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
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
                    Text(stringResource(R.string.common_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTextDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
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
                    stringResource(R.string.quickplaylists_edit_prompter_title),
                    color = Color.White,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = editTextTitle,
                    onValueChange = { editTextTitle = it },
                    label = { Text(stringResource(R.string.common_title_label)) },
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
                        label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
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
                        Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
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
                        Text(stringResource(R.string.common_save), color = Color.White)
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

internal fun findGroupRange(items: List<String>, headerIndex: Int): IntRange {
    if (headerIndex !in items.indices) return IntRange.EMPTY
    if (!isGroupHeader(items[headerIndex])) return IntRange.EMPTY
    findMatchingGroupEndIndex(items, headerIndex)?.let { endIndex ->
        return headerIndex..endIndex
    }

    var end = items.lastIndex
    for (cursor in (headerIndex + 1) until items.size) {
        if (isGroupHeader(items[cursor])) {
            end = cursor - 1
            break
        }
    }
    return headerIndex..end
}

internal fun findMatchingGroupEndIndex(items: List<String>, headerIndex: Int): Int? {
    if (headerIndex !in items.indices) return null
    val header = items[headerIndex]
    if (!isGroupHeader(header)) return null
    val uuid = getGroupUuid(header) ?: return null

    for (cursor in (headerIndex + 1) until items.size) {
        val item = items[cursor]
        if (isGroupHeader(item)) return null
        if (isGroupEnd(item) && getGroupUuid(item) == uuid) return cursor
    }
    return null
}

internal fun findGroupBlockRange(items: List<String>, headerIndex: Int): IntRange {
    if (headerIndex !in items.indices) return IntRange.EMPTY
    if (!isGroupHeader(items[headerIndex])) return IntRange.EMPTY
    findMatchingGroupEndIndex(items, headerIndex)?.let { endIndex ->
        return headerIndex..endIndex
    }
    val fallback = findGroupRange(items, headerIndex)
    return if (fallback.isEmpty()) headerIndex..headerIndex else fallback
}

internal fun <T> moveBlock(items: MutableList<T>, range: IntRange, newStartIndex: Int) {
    if (range.first !in items.indices || range.last !in items.indices) return
    if (range.first > range.last) return
    val block = items.subList(range.first, range.last + 1).toList()
    repeat(block.size) { items.removeAt(range.first) }
    val adjustedStart = if (newStartIndex > range.first) {
        newStartIndex - block.size
    } else {
        newStartIndex
    }.coerceIn(0, items.size)
    items.addAll(adjustedStart, block)
}

internal fun moveItemIntoGroup(
    items: MutableList<String>,
    fromIndex: Int,
    headerIndex: Int,
    mode: String
) {
    if (fromIndex !in items.indices) return
    if (headerIndex !in items.indices) return
    val dragged = items[fromIndex]
    if (isGroupHeader(dragged) || isGroupEnd(dragged)) return
    if (!isGroupHeader(items[headerIndex])) return

    val headerKey = items[headerIndex]
    items.removeAt(fromIndex)

    val resolvedHeaderIndex = items.indexOf(headerKey)
    if (resolvedHeaderIndex == -1) return

    val endIndex = findMatchingGroupEndIndex(items, resolvedHeaderIndex)
    val insertionIndex = (endIndex ?: (resolvedHeaderIndex + 1)).coerceIn(0, items.size)

    items.add(insertionIndex, dragged)
}

internal fun assignTrackToGroupByHeaderKey(
    items: MutableList<String>,
    trackUri: String,
    headerKey: String
): Boolean {
    val fromIndex = items.indexOf(trackUri)
    if (fromIndex == -1) return false
    if (!isPlayableAudioItem(items[fromIndex])) return false

    val headerIndex = items.indexOf(headerKey)
    if (headerIndex == -1) return false
    if (!isGroupHeader(items[headerIndex])) return false

    val currentHeaderIndex = findContainingGroupHeaderIndex(items, fromIndex)
    if (currentHeaderIndex != null && items[currentHeaderIndex] == headerKey) return false

    val before = items.toList()
    moveItemIntoGroup(
        items = items,
        fromIndex = fromIndex,
        headerIndex = headerIndex,
        mode = "BOTTOM"
    )
    return items != before
}

internal fun assignTracksToGroupByHeaderKey(
    items: MutableList<String>,
    trackUris: List<String>,
    headerKey: String
): Int {
    if (trackUris.isEmpty()) return 0
    val targetSet = trackUris.toSet()
    var movedCount = 0
    val ordered = items.filter { it in targetSet && isPlayableAudioItem(it) }
    ordered.forEach { trackUri ->
        if (assignTrackToGroupByHeaderKey(items, trackUri, headerKey)) {
            movedCount++
        }
    }
    return movedCount
}

internal fun moveItemOutOfGroup(items: MutableList<String>, trackUri: String): Boolean {
    val fromIndex = items.indexOf(trackUri)
    if (fromIndex == -1) return false
    val dragged = items[fromIndex]
    if (!isPlayableAudioItem(dragged)) return false

    var headerIndex = -1
    for (cursor in (fromIndex - 1) downTo 0) {
        if (isGroupHeader(items[cursor])) {
            headerIndex = cursor
            break
        }
    }
    if (headerIndex == -1) return false

    val headerKey = items[headerIndex]
    val endBeforeRemoval = findMatchingGroupEndIndex(items, headerIndex)
        ?: run {
            val range = findGroupRange(items, headerIndex)
            if (range.isEmpty()) return false
            range.last
        }

    if (fromIndex <= headerIndex || fromIndex > endBeforeRemoval) return false
    if (findMatchingGroupEndIndex(items, headerIndex) != null && fromIndex >= endBeforeRemoval) return false

    items.removeAt(fromIndex)

    val resolvedHeaderIndex = items.indexOf(headerKey)
    if (resolvedHeaderIndex == -1) return false

    val resolvedEndIndex = findMatchingGroupEndIndex(items, resolvedHeaderIndex)
        ?: run {
            val range = findGroupRange(items, resolvedHeaderIndex)
            if (range.isEmpty()) resolvedHeaderIndex else range.last
        }

    val insertionIndex = (resolvedEndIndex + 1).coerceIn(0, items.size)
    items.add(insertionIndex, dragged)
    return true
}

internal fun moveTracksOutOfGroup(
    items: MutableList<String>,
    trackUris: List<String>
): Set<String> {
    if (trackUris.isEmpty()) return emptySet()
    val targetSet = trackUris.toSet()
    val ordered = items.withIndex()
        .filter { (idx, key) -> key in targetSet && isPlayableAudioItem(key) && idx in items.indices }
        .sortedByDescending { it.index }
        .map { it.value }

    val moved = linkedSetOf<String>()
    ordered.forEach { trackUri ->
        if (moveItemOutOfGroup(items, trackUri)) {
            moved += trackUri
        }
    }
    return moved
}

internal fun removeGroupAtHeader(items: MutableList<String>, headerIndex: Int): Boolean {
    if (headerIndex !in items.indices) return false
    if (!isGroupHeader(items[headerIndex])) return false

    val endIndex = findMatchingGroupEndIndex(items, headerIndex)
    items.removeAt(headerIndex)

    if (endIndex != null) {
        val adjustedEndIndex = if (endIndex > headerIndex) endIndex - 1 else endIndex
        if (adjustedEndIndex in items.indices && isGroupEnd(items[adjustedEndIndex])) {
            items.removeAt(adjustedEndIndex)
        }
    }
    return true
}

internal fun isItemHiddenByCollapsedGroup(
    items: List<String>,
    itemIndex: Int,
    collapsedGroupIds: Set<String>
): Boolean {
    if (itemIndex !in items.indices) return false
    val item = items[itemIndex]
    if (isGroupHeader(item) || isGroupEnd(item)) return false

    var cursor = itemIndex - 1
    while (cursor >= 0) {
        val current = items[cursor]
        if (isGroupHeader(current)) {
            if (!collapsedGroupIds.contains(current)) return false
            val endIndex = findMatchingGroupEndIndex(items, cursor)
            return if (endIndex != null) {
                itemIndex > cursor && itemIndex < endIndex
            } else {
                itemIndex > cursor
            }
        }
        cursor--
    }
    return false
}

internal fun isItemInsideGroup(
    items: List<String>,
    itemIndex: Int
): Boolean {
    if (itemIndex !in items.indices) return false
    val item = items[itemIndex]
    if (isGroupHeader(item) || isGroupEnd(item)) return false

    var cursor = itemIndex - 1
    while (cursor >= 0) {
        val current = items[cursor]
        if (isGroupHeader(current)) {
            val endIndex = findMatchingGroupEndIndex(items, cursor)
            return if (endIndex != null) {
                itemIndex > cursor && itemIndex < endIndex
            } else {
                itemIndex > cursor
            }
        }
        cursor--
    }
    return false
}

internal fun findContainingGroupHeaderIndex(items: List<String>, itemIndex: Int): Int? {
    if (itemIndex !in items.indices) return null
    val item = items[itemIndex]
    if (isGroupHeader(item) || isGroupEnd(item)) return null

    var cursor = itemIndex - 1
    while (cursor >= 0) {
        val current = items[cursor]
        if (isGroupHeader(current)) {
            val endIndex = findMatchingGroupEndIndex(items, cursor)
            return if (endIndex != null) {
                if (itemIndex > cursor && itemIndex < endIndex) cursor else null
            } else {
                if (itemIndex > cursor) cursor else null
            }
        }
        cursor--
    }
    return null
}

private fun findNextReorderIndex(items: List<String>, startIndex: Int, step: Int): Int? {
    if (step == 0) return null
    var cursor = startIndex + step
    while (cursor in items.indices) {
        if (!isGroupEnd(items[cursor])) return cursor
        cursor += step
    }
    return null
}

internal fun findNextTrackReorderIndex(items: List<String>, startIndex: Int, step: Int): Int? {
    if (step == 0) return null
    val cursor = startIndex + step
    if (cursor !in items.indices) return null
    val candidate = items[cursor]
    if (isGroupHeader(candidate) || isGroupEnd(candidate)) return null
    return cursor
}

internal data class ListViewportItem(
    val key: String,
    val start: Int,
    val endExclusive: Int
)

internal data class GroupAssignOption(
    val headerKey: String,
    val title: String
)

private data class VisiblePlaylistRow(
    val realIndex: Int,
    val item: String
)

internal fun findHeaderDropTargetKey(
    songs: List<String>,
    viewportItems: List<ListViewportItem>,
    dragY: Float,
    draggedItemKey: String?,
    headerPaddingPx: Float = 0f
): String? {
    val dragged = draggedItemKey ?: return null
    if (!isPlayableAudioItem(dragged)) return null

    val hoverHit = viewportItems.firstOrNull { item ->
        dragY >= (item.start - headerPaddingPx) &&
            dragY < (item.endExclusive + headerPaddingPx)
    } ?: return null

    val hoverKey = hoverHit.key
    val targetHeaderIndex = when {
        isGroupHeader(hoverKey) -> songs.indexOf(hoverKey).takeIf { it >= 0 }
        else -> {
            val hoveredIndex = songs.indexOf(hoverKey)
            if (hoveredIndex == -1) {
                null
            } else {
                findContainingGroupHeaderIndex(songs, hoveredIndex)
            }
        }
    } ?: return null

    val draggedIndex = songs.indexOf(dragged)
    val draggedGroupHeaderIndex = if (draggedIndex >= 0) {
        findContainingGroupHeaderIndex(songs, draggedIndex)
    } else {
        null
    }
    if (draggedGroupHeaderIndex != null && draggedGroupHeaderIndex == targetHeaderIndex) return null

    return songs[targetHeaderIndex].takeIf { isGroupHeader(it) }
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

private fun saveManualOrder(context: Context, playlist: String, order: List<String>) {
    runCatching {
        PlaylistStateStore.saveManualOrder(
            context = context,
            playlistName = playlist,
            manualOrderUris = order
        )
    }
}

private fun loadManualOrder(context: Context, playlist: String, currentOrder: List<String>): List<String>? {
    if (currentOrder.isEmpty()) return null
    return runCatching {
        PlaylistStateStore.loadManualOrder(
            context = context,
            playlistName = playlist,
            currentOrderUris = currentOrder
        )
    }.getOrNull()
}

private fun saveOriginalOrderIfMissing(context: Context, playlist: String, order: List<String>) {
    runCatching {
        PlaylistStateStore.saveOriginalOrderIfMissing(
            context = context,
            playlistName = playlist,
            originalOrderUris = order
        )
    }

    val prefs = context.getSharedPreferences(PLAYLIST_ORIGINAL_ORDER_PREF, Context.MODE_PRIVATE)
    val key = originalOrderKey(playlist)
    if (prefs.contains(key)) return

    val json = org.json.JSONArray().apply { order.forEach { put(it) } }.toString()
    prefs.edit().putString(key, json).apply()
}

private fun overwriteOriginalOrder(context: Context, playlist: String, order: List<String>) {
    runCatching {
        PlaylistStateStore.saveOriginalOrder(
            context = context,
            playlistName = playlist,
            originalOrderUris = order
        )
    }

    val prefs = context.getSharedPreferences(PLAYLIST_ORIGINAL_ORDER_PREF, Context.MODE_PRIVATE)
    val key = originalOrderKey(playlist)
    val json = org.json.JSONArray().apply { order.forEach { put(it) } }.toString()
    prefs.edit().putString(key, json).apply()
}

private fun loadOriginalOrder(context: Context, playlist: String): List<String>? {
    val currentRaw = PlaylistRepository.getAllSongsRaw(playlist)
    val fromJson = runCatching {
        PlaylistStateStore.loadOriginalOrder(
            context = context,
            playlistName = playlist,
            currentOrderUris = currentRaw
        )
    }.getOrNull()
    if (!fromJson.isNullOrEmpty()) return fromJson

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
    runCatching {
        TrackSettingsStore.saveTitleColorArgbByUri(
            context = context,
            playlistName = playlist,
            uriString = uri,
            colorArgb = color.toArgb()
        )
    }

    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .putInt(songColorKey(playlist, uri), color.toArgb())
        .apply()
}

private fun loadSongColor(context: Context, playlist: String, uri: String): Color? {
    val fromJson = runCatching {
        TrackSettingsStore.getTitleColorArgbByUri(
            context = context,
            playlistName = playlist,
            uriString = uri
        )
    }.getOrNull()
    if (fromJson != null) return Color(fromJson)

    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    val key = songColorKey(playlist, uri)
    return if (prefs.contains(key)) {
        Color(prefs.getInt(key, Color.White.toArgb()))
    } else null
}

private fun clearSongColor(context: Context, playlist: String, uri: String) {
    runCatching {
        TrackSettingsStore.clearTitleColorByUri(
            context = context,
            playlistName = playlist,
            uriString = uri
        )
    }

    val prefs = context.getSharedPreferences(SONG_COLOR_PREF, Context.MODE_PRIVATE)
    prefs.edit()
        .remove(songColorKey(playlist, uri))
        .apply()
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
