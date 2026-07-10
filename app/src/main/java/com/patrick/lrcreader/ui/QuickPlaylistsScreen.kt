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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.HardwareListCommand
import com.patrick.lrcreader.core.MiniTunerVisibilityStore
import com.patrick.lrcreader.core.NotesRepository
import com.patrick.lrcreader.core.TunerEngine
import com.patrick.lrcreader.core.TunerState
import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.buildSmpOccurrenceItem
import com.patrick.lrcreader.core.canonicalPlaylistPlaybackKey
import com.patrick.lrcreader.core.getVariantFamilyId
import com.patrick.lrcreader.core.getVariantFamilySongIds
import com.patrick.lrcreader.core.getVariantFamilyTitle
import com.patrick.lrcreader.core.getGroupColorArgb
import com.patrick.lrcreader.core.getGroupUuid
import com.patrick.lrcreader.core.getGroupTitle
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.isPlayableAudioItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.isVariantFamilyItem
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.PlaylistTrackLimitPolicy
import com.patrick.lrcreader.core.renameGroupHeader
import com.patrick.lrcreader.core.setGroupColorArgb
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.core.config.TrackSettingsStore
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.core.search.SearchEngine
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.ui.library.SongVariantFamiliesStore
import com.patrick.lrcreader.ui.library.SongVariantFamily
import kotlinx.coroutines.yield
import java.io.File
import java.net.URLDecoder

private data class QuickPlaylistUiSnapshot(
    val loadStamp: String,
    val songs: List<String>,
    val playlistTotalMs: Long,
    val durationCache: Map<String, Long>,
    val originalOrder: List<String>?,
    val portableStamp: String?,
    val smpSongsById: Map<String, com.patrick.lrcreader.smp.SongUnit>,
    val loaded: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

private const val DEMO_TITLES_TAG = "DEMO_TITLES"

private object QuickPlaylistsUiCache {
    private val snapshots = mutableMapOf<String, QuickPlaylistUiSnapshot>()

    fun get(playlist: String?): QuickPlaylistUiSnapshot? = snapshots[playlist.orEmpty()]

    fun put(playlist: String?, snapshot: QuickPlaylistUiSnapshot) {
        snapshots[playlist.orEmpty()] = snapshot
    }

    fun remove(playlist: String?) {
        snapshots.remove(playlist.orEmpty())
    }
}

/**
 * QuickPlaylistsScreen + titres "texte seul" (prompteur).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun QuickPlaylistsScreen(
    modifier: Modifier = Modifier,
    onPlaySong: (String, String, Color) -> Unit,
    onPlayFromHere: (List<String>, Int, String) -> Unit = { _, _, _ -> },
    onArmChainFromCurrent: (List<String>, Int, String) -> Unit = { _, _, _ -> },
    refreshKey: Int,
    openPrompterSignal: Int = 0,
    libraryLoadedSignal: Int = 0,
    playlistsReady: Boolean = true,
    nextChainedUri: String? = null,
    nextTrackUri: String? = null,
    isPlaying: Boolean = false,
    currentPlayingUri: String? = null,
    currentPlayingPlaylist: String? = null,
    currentPlayingPlaylistItemKey: String? = null,
    selectedPlaylist: String? = null,
    openedPlaylist: String? = null,
    isRestoringSession: Boolean = false,
    onSelectedPlaylistChange: (String?) -> Unit = {},
    onPlaylistColorChange: (Color) -> Unit = {},
    onSetNextTrack: (uri: String, title: String, playlist: String?) -> Unit = { _, _, _ -> },
    onClearNextTrack: () -> Unit = {},
    onConsumeOpenPrompterSignal: () -> Unit = {},
    onRequestShowPlayer: () -> Unit = {},
    hardwareCommandToken: Int = 0,
    hardwareCommand: HardwareListCommand = HardwareListCommand.ACTIVATE,
    hardwareReturnToCurrentToken: Int = 0,
    hardwareReturnCommand: HardwareListCommand = HardwareListCommand.MOVE_NEXT,
    playbackControlActivateSelectedToken: Int = 0,
    onSequentialSelectionChanged: () -> Unit = {},
    onAddTrackToPlaylist: (String) -> Unit = {},
    searchToggleSignal: Int = 0,
    smpSongsCache: Map<String, com.patrick.lrcreader.smp.SongUnit> = emptyMap(),
    indexAll: List<LibraryIndexCache.CachedEntry> = emptyList(), // ✅ propre + default
    compactTabletLayout: Boolean = false
) {

    val context = LocalContext.current
    val sQuickplaylistsNewGroupDefault = stringResource(R.string.quickplaylists_group_new_default)
    val sQuickplaylistsCurrentGroup = stringResource(R.string.quickplaylists_group_current)
    val sQuickplaylistsCreateLiveList = stringResource(R.string.quickplaylists_menu_create_live_list)
    val sQuickplaylistsAddToLiveList = stringResource(R.string.quickplaylists_menu_add_to_live_list)
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
    var playlistImportResultMessage by remember { mutableStateOf<String?>(null) }
    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rawJson = context.contentResolver.openInputStream(pickedUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    importPlaylistFile(context.applicationContext, rawJson)
                }.getOrElse {
                    PlaylistFileImportResult(
                        importedPlaylistCount = 0,
                        foundCount = 0,
                        missingCount = 0,
                        failed = true
                    )
                }
            }
            playlistImportResultMessage = formatPlaylistImportResultMessage(context, result)
        }
    }
    val titleAliasVersion = TitleAliasesStore.version.intValue
    val variantFamilyVersion = SongVariantFamiliesStore.version.intValue
    val variantFamilies = remember(variantFamilyVersion) { SongVariantFamiliesStore.load(context) }
    val variantFamilyById = remember(variantFamilies) { variantFamilies.associateBy { it.id } }

// ✅ IMPORTANT : on observe le repo RAM (sinon la playlist garde des URI "morts" après rename en bibliothèque)
    val repoVersion = PlaylistRepository.version.value

// ✅ la liste des playlists se met à jour dès que le repo change
    val playlists = remember(refreshKey, repoVersion) { PlaylistRepository.getPlaylists() }

    var internalSelected by rememberSaveable {
        mutableStateOf<String?>(selectedPlaylist ?: openedPlaylist ?: playlists.firstOrNull())
    }
    val resolvedPlaylistSelection = internalSelected ?: selectedPlaylist ?: openedPlaylist ?: playlists.firstOrNull()
    val playlistLoadStamp = remember(
        resolvedPlaylistSelection,
        refreshKey,
        repoVersion,
        libraryLoadedSignal,
        playlistsReady,
        titleAliasVersion
    ) {
        buildString {
            append("playlist=").append(resolvedPlaylistSelection.orEmpty())
            append("|refreshKey=").append(refreshKey)
            append("|repoVersion=").append(repoVersion)
            append("|libraryLoadedSignal=").append(libraryLoadedSignal)
            append("|playlistsReady=").append(playlistsReady)
            append("|titleAliasVersion=").append(titleAliasVersion)
            append("|variantFamilyVersion=").append(variantFamilyVersion)
        }
    }
    val cachedPlaylistSnapshot = remember(resolvedPlaylistSelection) {
        QuickPlaylistsUiCache.get(resolvedPlaylistSelection)
    }
    val isMiniTunerVisible by MiniTunerVisibilityStore.state(context).collectAsState()

    val songs = remember(resolvedPlaylistSelection) {
        mutableStateListOf<String>().apply {
            addAll(cachedPlaylistSnapshot?.songs.orEmpty())
        }
    }
    val smpSongIdsInPlaylist by remember(variantFamilyById) {
        derivedStateOf {
            songs.flatMap { item ->
                val family = variantFamilyForPlaylistItem(item, variantFamilyById)
                if (family != null) {
                    family.songIds
                } else {
                    listOfNotNull(getSmpSongId(item))
                }
            }.toSet()
        }
    }
    var playlistContentLoaded by remember(resolvedPlaylistSelection) {
        mutableStateOf(cachedPlaylistSnapshot?.loaded == true)
    }
    val smpSongsById = remember(refreshKey, libraryLoadedSignal, repoVersion, smpSongIdsInPlaylist, smpSongsCache) {
        if (
            cachedPlaylistSnapshot?.loaded == true &&
            cachedPlaylistSnapshot.loadStamp == playlistLoadStamp
        ) {
            cachedPlaylistSnapshot.smpSongsById + smpSongsCache.filterKeys { it in smpSongIdsInPlaylist }
        } else if (smpSongIdsInPlaylist.isEmpty()) {
            emptyMap()
        } else {
            smpSongsCache.filterKeys { it in smpSongIdsInPlaylist }
        }
    }
    val smpTitleById = remember(smpSongsById) {
        smpSongsById.mapValues { (_, song) ->
            cleanQuickPlaylistTitle(song.title) ?: song.id
        }
    }
    val smpPlaybackUriById = remember(smpSongsById) {
        smpSongsById.values
            .mapNotNull { song ->
                val audioPath = song.audioPath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                song.id to Uri.fromFile(File(audioPath)).toString()
            }
            .toMap()
    }
    // ✅ Snapshot "ordre d'origine" (pour le bouton Réinitialiser)
    // - On le fixe au premier chargement d'une playlist
    // - Et on le met à jour quand TU réordonnes à la main (drag)
    // ✅ Durée playlist (cache par titre) + affichage mini dans le header
    val durationCache = remember(resolvedPlaylistSelection) {
        mutableStateMapOf<String, Long>().apply {
            putAll(cachedPlaylistSnapshot?.durationCache.orEmpty())
        }
    } // uriString -> ms
    var playlistTotalMs by remember(resolvedPlaylistSelection) {
        mutableStateOf(cachedPlaylistSnapshot?.playlistTotalMs ?: -1L)
    } // -1 = loading
    val originalOrderByPlaylist = remember(resolvedPlaylistSelection) {
        mutableStateMapOf<String, List<String>>().apply {
            resolvedPlaylistSelection?.let { playlist ->
                cachedPlaylistSnapshot?.originalOrder?.let { put(playlist, it) }
            }
        }
    }
    var currentListColor by remember { mutableStateOf(Color.White) } // ✅ plus de couleur "globale" de playlist

    var showMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showResetPlaylistDialog by remember { mutableStateOf(false) }
    var savePlaylistName by remember { mutableStateOf("") }
    var playlistSearchQuery by rememberSaveable(internalSelected) { mutableStateOf("") }
    var isSearchVisible by rememberSaveable(internalSelected) { mutableStateOf(false) }
    var lastHandledSearchToggleSignal by rememberSaveable(internalSelected) { mutableIntStateOf(0) }

    val listState = rememberSaveable(resolvedPlaylistSelection, saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = cachedPlaylistSnapshot?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = cachedPlaylistSnapshot?.firstVisibleItemScrollOffset ?: 0
        )
    }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val adaptiveTokens = rememberSmpAdaptiveTokens()
    val rowHeight = if (compactTabletLayout) 52.dp else adaptiveTokens.playlistRowHeight
    val screenPadding = if (compactTabletLayout) 8.dp else 16.dp
    val rackPadding = if (compactTabletLayout) 4.dp else 6.dp
    val rowOuterVerticalPadding = if (compactTabletLayout) 2.dp else 4.dp
    val rowHorizontalPadding = if (compactTabletLayout) 8.dp else 12.dp
    val songRowHorizontalPadding = if (compactTabletLayout) 4.dp else 6.dp
    val titleFontSize = if (compactTabletLayout) 13.sp else 14.sp
    val secondaryFontSize = if (compactTabletLayout) 10.sp else 12.sp
    val groupMetaFontSize = if (compactTabletLayout) 10.sp else 11.sp
    val badgeFontSize = if (compactTabletLayout) 9.sp else 10.sp
    val dragHandleSize = if (compactTabletLayout) 30.dp else 34.dp
    val markerHeight = if (compactTabletLayout) 22.dp else 28.dp
    val groupAccentHeight = if (compactTabletLayout) 22.dp else 26.dp
    val rowMenuSize = if (compactTabletLayout) 30.dp else 32.dp
    val topSpacerHeight = if (compactTabletLayout) 6.dp else 10.dp
    val searchFieldHeight = if (compactTabletLayout) 44.dp else 56.dp
    val searchTextSize = if (compactTabletLayout) 13.sp else 16.sp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val headerDropPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    var draggingUri by remember { mutableStateOf<String?>(null) }
    var dragOrderChanged by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragYInListViewport by remember { mutableStateOf<Float?>(null) }
    var hoverHeaderKey by remember { mutableStateOf<String?>(null) }
    var collapsedGroupIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var expandedVariantFamilyIds by rememberSaveable(internalSelected) { mutableStateOf(setOf<String>()) }
    var activePlayingGroupHeaderKey by rememberSaveable(internalSelected) { mutableStateOf<String?>(null) }
    var pendingLiveGroupScrollHeaderKey by remember { mutableStateOf<String?>(null) }
    var keyboardSelectedItem by rememberSaveable(internalSelected) { mutableStateOf<String?>(null) }

    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }
    var renameGroupText by remember { mutableStateOf("") }
    var groupColorTarget by remember { mutableStateOf<String?>(null) }
    var selectedTrackKeys by remember { mutableStateOf(setOf<String>()) }
    var assignGroupTargetUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var assignGroupOptions by remember { mutableStateOf<List<GroupAssignOption>>(emptyList()) }
    var alsoAddGroupTargetUri by remember { mutableStateOf<String?>(null) }
    var alsoAddGroupOptions by remember { mutableStateOf<List<GroupAssignOption>>(emptyList()) }
    var moveTargetUri by remember { mutableStateOf<String?>(null) }
    var moveTargetOptions by remember { mutableStateOf<List<PlaylistMoveOption>>(emptyList()) }

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
    val activeGroupPulseTransition = rememberInfiniteTransition(label = "quick_playlists_active_group")
    val activeGroupPulseFraction by activeGroupPulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quick_playlists_active_group_fraction"
    )
    var previousSongsSize by remember { mutableIntStateOf(0) }
    val portableStampByPlaylist = remember(resolvedPlaylistSelection) {
        mutableStateMapOf<String, String>().apply {
            resolvedPlaylistSelection?.let { playlist ->
                cachedPlaylistSnapshot?.portableStamp?.let { put(playlist, it) }
            }
        }
    }
    var quickEnterAtMs by remember { mutableLongStateOf(0L) }

    SideEffect {
        QuickPlaylistsUiCache.put(
            resolvedPlaylistSelection,
            QuickPlaylistUiSnapshot(
                loadStamp = playlistLoadStamp,
                songs = songs.toList(),
                playlistTotalMs = playlistTotalMs,
                durationCache = durationCache.toMap(),
                originalOrder = resolvedPlaylistSelection?.let { originalOrderByPlaylist[it] },
                portableStamp = resolvedPlaylistSelection?.let { portableStampByPlaylist[it] },
                smpSongsById = smpSongsById,
                loaded = playlistContentLoaded,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        )
    }

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
    LaunchedEffect(
        internalSelected,
        refreshKey,
        notesVersion,
        repoVersion,
        libraryLoadedSignal,
        playlistsReady,
        titleAliasVersion,
        variantFamilyVersion,
        smpPlaybackUriById
    ) {
        val pl = internalSelected
        Log.d(
            "BOOTSTEP",
            "QuickPlaylists.enter openedPlaylist=$pl selectedPlaylist=$selectedPlaylist reason=internalSelected"
        )
        if (
            pl != null &&
            cachedPlaylistSnapshot?.loaded == true &&
            cachedPlaylistSnapshot.loadStamp == playlistLoadStamp
        ) {
            playlistContentLoaded = true
            if (songs != cachedPlaylistSnapshot.songs) {
                songs.clear()
                songs.addAll(cachedPlaylistSnapshot.songs)
            }
            playlistTotalMs = cachedPlaylistSnapshot.playlistTotalMs
            cachedPlaylistSnapshot.originalOrder?.let { originalOrderByPlaylist[pl] = it }
            cachedPlaylistSnapshot.portableStamp?.let { portableStampByPlaylist[pl] = it }
            durationCache.clear()
            durationCache.putAll(cachedPlaylistSnapshot.durationCache)
            Log.d(
                "BOOTSTEP",
                "QuickPlaylists.restoreSnapshot playlist=$pl size=${cachedPlaylistSnapshot.songs.size} totalMs=${cachedPlaylistSnapshot.playlistTotalMs}"
            )
            return@LaunchedEffect
        }
        if (pl != null) {
            PlaylistRepository.normalizeSmpItemsForPlaylist(pl)
            val raw = PlaylistRepository.getAllSongsRaw(pl)
            if (!playlistsReady) {
                songs.clear()
                songs.addAll(raw)
                Log.d(
                    "BOOTSTEP",
                    "QuickPlaylists.wait playlistsReady=false playlist=$pl rawSize=${raw.size} keepFallback=true"
                )
                playlistContentLoaded = false
                return@LaunchedEffect
            }

            songs.clear()
            // ✅ fallback immédiat sans dépendance LibraryIndexCache
            songs.addAll(raw)
            if (raw.isNotEmpty()) yield()

            val portableStamp = "$refreshKey|$repoVersion|$libraryLoadedSignal|${raw.hashCode()}"
            if (portableStampByPlaylist[pl] != portableStamp) {
                val portableStart = SystemClock.elapsedRealtime()
                val restoredManual = if (PlaylistRepository.isRestoring) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        loadManualOrder(context, pl, raw)
                    }
                }
                val hasPlayedItems = raw.any { PlaylistRepository.isSongPlayed(pl, it) }
                var portableApplied = false
                if (!hasPlayedItems && restoredManual != null && restoredManual != raw) {
                    PlaylistRepository.updatePlayListOrder(pl, restoredManual)
                    portableApplied = true
                }
                portableStampByPlaylist[pl] = portableStamp
                Log.d(
                    "BOOTSTEP",
                    "QuickPlaylists.applyPortableOrder playlist=$pl applied=$portableApplied hasPlayedItems=$hasPlayedItems ms=${SystemClock.elapsedRealtime() - portableStart} rawSize=${raw.size}"
                )
            }

            val getSongsStart = SystemClock.elapsedRealtime()
            Log.d("BOOTSTEP", "QuickPlaylists.getSongsFor:before playlist=$pl")
            val loaded = if (raw.any { isGroupHeader(it) || isGroupEnd(it) }) {
                raw
            } else {
                PlaylistRepository.getSongsFor(pl)
            }
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
                    val durationSource = resolvePlaylistDurationSource(
                        item = u,
                        familyById = variantFamilyById,
                        smpPlaybackUriById = smpPlaybackUriById
                    ) ?: continue
                    val cached = durationCache[durationSource]?.takeIf { it > 0L }
                    val d = cached
                        ?: getAudioDurationMsQP(context, durationSource)?.takeIf { it > 0L }?.also {
                            durationCache[durationSource] = it
                        }
                    if (d != null) acc += d
                }
                acc
            }
            playlistContentLoaded = true
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
            songs.contains(key) && isPlaylistGroupableItem(key)
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
    LaunchedEffect(songs, activePlayingGroupHeaderKey) {
        val activeKey = activePlayingGroupHeaderKey ?: return@LaunchedEffect
        if (activeKey !in songs) {
            activePlayingGroupHeaderKey = null
        }
    }

    val menuBg = Color(0xFF1B1B1B)

    fun persistSongsOrder(playlist: String, overwriteOriginal: Boolean = false) {
        val snapshot = songs.toList()
        PlaylistRepository.updatePlayListOrder(playlist, snapshot)
        if (overwriteOriginal) {
            originalOrderByPlaylist[playlist] = snapshot
        }
        scope.launch(Dispatchers.IO) {
            saveManualOrder(context, playlist, snapshot)
            if (overwriteOriginal) {
                overwriteOriginalOrder(context, playlist, snapshot)
            }
        }
    }

    fun findGroupHeaderKeyByTitle(title: String): String? {
        return songs.firstOrNull { item ->
            isGroupHeader(item) && getGroupTitle(item) == title
        }
    }

    fun ensureCurrentGroupHeader(playlist: String): Pair<String, Boolean> {
        findGroupHeaderKeyByTitle(sQuickplaylistsCurrentGroup)?.let { return it to false }
        val header = buildGroupHeader(sQuickplaylistsCurrentGroup)
        val end = getGroupUuid(header)?.let { buildGroupEnd(it) }
        songs.add(header)
        if (end != null) {
            songs.add(end)
        }
        persistSongsOrder(playlist, overwriteOriginal = true)
        return header to true
    }

    fun moveTrackToGroupHeader(playlist: String, trackIndex: Int, headerKey: String) {
        if (trackIndex !in songs.indices) return
        val headerIndex = songs.indexOf(headerKey)
        if (headerIndex < 0) return
        moveItemIntoGroup(
            items = songs,
            fromIndex = trackIndex,
            headerIndex = headerIndex,
            mode = "BOTTOM"
        )
        collapsedGroupIds = collapsedGroupIds - headerKey
        persistSongsOrder(playlist, overwriteOriginal = true)
    }

    fun findCurrentPlayingTrackIndexInPlaylist(playlist: String): Int? {
        if (currentPlayingPlaylist == playlist && !currentPlayingPlaylistItemKey.isNullOrBlank()) {
            val currentItemKey = currentPlayingPlaylistItemKey
            val byItemKey = songs.indexOfFirst { item ->
                val playbackItem = resolveVariantFamilyPlaybackItem(item, variantFamilyById)
                isPlayableAudioItem(playbackItem) &&
                    canonicalPlaylistPlaybackKey(
                        playlistItemKey = item,
                        playbackUri = playbackItem
                    ) == currentItemKey
            }
            if (byItemKey >= 0) return byItemKey
        }
        val currentUri = currentPlayingUri?.takeIf { it.isNotBlank() } ?: return null
        val byUri = songs.indexOfFirst { item ->
            val playbackItem = resolveVariantFamilyPlaybackItem(item, variantFamilyById)
            isPlayableAudioItem(playbackItem) && playbackItem == currentUri
        }
        return byUri.takeIf { it >= 0 }
    }

    fun isExistingPlaylist(name: String?): Boolean {
        val clean = name?.trim().orEmpty()
        return clean.isNotEmpty() && playlists.contains(clean)
    }

    fun saveExistingPlaylist(playlist: String) {
        if (!isExistingPlaylist(playlist)) {
            Toast.makeText(
                context,
                context.getString(R.string.quickplaylists_playlist_save_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        persistSongsOrder(playlist, overwriteOriginal = true)
        internalSelected = playlist
        onSelectedPlaylistChange(playlist)
        Toast.makeText(
            context,
            context.getString(R.string.quickplaylists_playlist_saved),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun createEmptyPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        PlaylistRepository.addPlaylist(clean)
        internalSelected = clean
        onSelectedPlaylistChange(clean)
    }

    fun createPlaylistFromCurrent(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || isExistingPlaylist(clean)) {
            Toast.makeText(
                context,
                context.getString(R.string.quickplaylists_playlist_save_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val snapshot = songs.toList()
        val limitedTrackCount = PlaylistTrackLimitPolicy.countLimitedTrackItems(snapshot)
        if (!PlaylistTrackLimitPolicy.canAddTracks(context, clean, limitedTrackCount)) {
            Toast.makeText(
                context,
                context.getString(R.string.playlist_track_limit_reached),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        PlaylistRepository.addPlaylist(clean)
        snapshot.forEach { uri ->
            PlaylistRepository.assignSongToPlaylist(clean, uri)
        }
        saveManualOrder(context, clean, snapshot)
        overwriteOriginalOrder(context, clean, snapshot)
        originalOrderByPlaylist[clean] = snapshot
        internalSelected = clean
        onSelectedPlaylistChange(clean)
        Toast.makeText(
            context,
            context.getString(R.string.quickplaylists_playlist_created),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun requestSaveCurrentPlaylist() {
        val current = internalSelected?.trim()
        if (isExistingPlaylist(current)) {
            saveExistingPlaylist(current!!)
        } else {
            savePlaylistName = current.orEmpty()
            showSavePlaylistDialog = true
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

    fun openAlsoAddDialogForTarget(targetUri: String) {
        if (getSmpSongId(targetUri).isNullOrBlank()) return
        val options = songs.asSequence()
            .filter { isGroupHeader(it) }
            .map { GroupAssignOption(headerKey = it, title = getGroupTitle(it)) }
            .toList()
        if (options.isNotEmpty()) {
            alsoAddGroupTargetUri = targetUri
            alsoAddGroupOptions = options
        }
    }

    fun addSharedOccurrenceToGroup(targetUri: String, option: GroupAssignOption) {
        val pl = internalSelected ?: return
        val songId = getSmpSongId(targetUri) ?: return
        if (groupContainsSongId(songs, option.headerKey, songId)) {
            Toast.makeText(
                context,
                context.getString(R.string.quickplaylists_group_already_contains_song),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val headerIndex = songs.indexOf(option.headerKey)
        if (headerIndex < 0) return
        val occurrenceUri = buildSmpOccurrenceItem(songId)
        PlaylistRepository.assignSongToPlaylist(
            playlistName = pl,
            songUri = occurrenceUri,
            songId = songId
        )
        val endIndex = findMatchingGroupEndIndex(songs, headerIndex)
            ?: findGroupRange(songs, headerIndex).last
        val insertIndex = if (endIndex >= headerIndex) {
            endIndex.coerceIn(0, songs.size)
        } else {
            (headerIndex + 1).coerceIn(0, songs.size)
        }
        songs.add(insertIndex, occurrenceUri)
        collapsedGroupIds = collapsedGroupIds - option.headerKey
        persistSongsOrder(pl, overwriteOriginal = true)
    }

    fun removeTargetsFromGroup(targets: List<String>) {
        val pl = internalSelected ?: return
        val movedUris = moveTracksOutOfGroup(items = songs, trackUris = targets)
        if (movedUris.isNotEmpty()) {
            persistSongsOrder(pl, overwriteOriginal = true)
            selectedTrackKeys = selectedTrackKeys - movedUris
        }
    }

    fun openMoveDialogForTarget(targetUri: String) {
        if (!songs.contains(targetUri)) return
        val options = buildPlaylistMoveOptions(
            context = context,
            songs = songs,
            sourceUri = targetUri,
            smpTitleById = smpTitleById
        )
        if (options.isNotEmpty()) {
            moveTargetUri = targetUri
            moveTargetOptions = options
        }
    }

    fun moveTargetToOption(trackUri: String, option: PlaylistMoveOption) {
        val pl = internalSelected ?: return
        if (movePlaylistItemToOption(songs, trackUri, option)) {
            persistSongsOrder(pl, overwriteOriginal = true)
            keyboardSelectedItem = trackUri
        }
    }

    fun dragHandleModifier(itemKey: String): Modifier {
        return Modifier.pointerInput(songs.size) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    draggingUri = itemKey
                    dragOrderChanged = false
                    dragOffsetPx = 0f
                    val visibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                        playlistViewportKeyToItem(info.key) == itemKey
                    }
                    dragYInListViewport = visibleInfo?.let { info ->
                        info.offset + (info.size / 2f)
                    }
                    hoverHeaderKey = dragYInListViewport?.let { dragY ->
                        val viewportItems = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                            val key = playlistViewportKeyToItem(info.key) ?: return@mapNotNull null
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
                        isPlaylistGroupableItem(dragged) &&
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
                        dragOrderChanged = true
                    }
                    draggingUri = null
                    dragOffsetPx = 0f
                    dragYInListViewport = null
                    hoverHeaderKey = null
                    if (dragOrderChanged) internalSelected?.let { pl ->
                        persistSongsOrder(pl, overwriteOriginal = true)
                    }
                    dragOrderChanged = false
                },
                onDragCancel = {
                    draggingUri = null
                    dragOrderChanged = false
                    dragOffsetPx = 0f
                    dragYInListViewport = null
                    hoverHeaderKey = null
                }
            ) { _, dragAmount ->
                val current = draggingUri ?: return@detectDragGesturesAfterLongPress
                val currentIndex = songs.indexOf(current)
                if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                val baseY = dragYInListViewport ?: listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> playlistViewportKeyToItem(info.key) == current }
                    ?.let { info -> info.offset + (info.size / 2f) }
                    ?: return@detectDragGesturesAfterLongPress
                dragYInListViewport = baseY + dragAmount.y

                val viewportItems = listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    val key = playlistViewportKeyToItem(info.key) ?: return@mapNotNull null
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
                val lockReorderForDrop = isPlaylistGroupableItem(current) && hoverHeaderKey != null
                if (lockReorderForDrop) {
                    dragOffsetPx = 0f
                    return@detectDragGesturesAfterLongPress
                }

                if (dragOffsetPx >= rowHeightPx / 2f) {
                    if (isGroupHeader(current)) {
                        val range = findGroupBlockRange(songs, currentIndex)
                        val newStart = if (range.isEmpty()) null else findGroupDragDownInsertIndex(songs, range)
                        if (newStart != null) {
                            moveBlock(songs, range, newStart)
                            dragOrderChanged = true
                        }
                    } else {
                        val next = findNextTrackReorderIndex(songs, currentIndex, +1)
                        if (next != null) {
                            songs.swap(currentIndex, next)
                            dragOrderChanged = true
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        } else {
                            val currentHeaderIndex = findContainingGroupHeaderIndex(songs, currentIndex)
                            if (currentHeaderIndex != null && hoverHeaderKey == null) {
                                val groupEndIndex = findMatchingGroupEndIndex(songs, currentHeaderIndex)
                                    ?: findGroupRange(songs, currentHeaderIndex).last
                                val dragged = songs.removeAt(currentIndex)
                                val targetIndex = groupEndIndex.coerceIn(0, songs.size)
                                songs.add(targetIndex, dragged)
                                dragOrderChanged = true
                                internalSelected?.let { pl ->
                                    PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                                }
                            }
                        }
                    }
                    dragOffsetPx = 0f
                }
                if (dragOffsetPx <= -rowHeightPx / 2f) {
                    if (isGroupHeader(current)) {
                        val range = findGroupBlockRange(songs, currentIndex)
                        val newStart = if (range.isEmpty()) null else findGroupDragUpInsertIndex(songs, range)
                        if (newStart != null) {
                            moveBlock(songs, range, newStart)
                            dragOrderChanged = true
                        }
                    } else {
                        val prev = findNextTrackReorderIndex(songs, currentIndex, -1)
                        if (prev != null) {
                            songs.swap(currentIndex, prev)
                            dragOrderChanged = true
                            internalSelected?.let { pl ->
                                PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                            }
                        } else {
                            val currentHeaderIndex = findContainingGroupHeaderIndex(songs, currentIndex)
                            if (currentHeaderIndex != null && hoverHeaderKey == null) {
                                val dragged = songs.removeAt(currentIndex)
                                val targetIndex = currentHeaderIndex.coerceIn(0, songs.size)
                                songs.add(targetIndex, dragged)
                                dragOrderChanged = true
                                internalSelected?.let { pl ->
                                    PlaylistRepository.updatePlayListOrder(pl, songs.toList())
                                }
                            }
                        }
                    }
                    dragOffsetPx = 0f
                }
            }
        }
    }

    val normalizedPlaylistSearchQuery = remember(playlistSearchQuery) {
        SearchEngine.normalize(playlistSearchQuery)
    }
    val searchableTitleByItem = remember(
        context,
        songs.toList(),
        notesVersion,
        titleAliasVersion,
        repoVersion,
        variantFamilyVersion,
        smpTitleById
    ) {
        songs.associateWith { item ->
            buildQuickPlaylistSearchTitle(
                context = context,
                item = item,
                smpTitleById = smpTitleById
            )
        }
    }
    val visibleRows by remember(normalizedPlaylistSearchQuery, collapsedGroupIds, searchableTitleByItem) {
        derivedStateOf {
            buildVisiblePlaylistRows(
                songs = songs,
                collapsedGroupIds = collapsedGroupIds,
                normalizedQuery = normalizedPlaylistSearchQuery,
                searchableTitleByItem = searchableTitleByItem
            )
        }
    }
    LaunchedEffect(isSearchVisible, internalSelected) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(searchToggleSignal) {
        if (searchToggleSignal == 0) return@LaunchedEffect
        if (searchToggleSignal == lastHandledSearchToggleSignal) return@LaunchedEffect
        lastHandledSearchToggleSignal = searchToggleSignal
        if (isSearchVisible) {
            playlistSearchQuery = ""
            isSearchVisible = false
            focusManager.clearFocus(force = true)
        } else {
            isSearchVisible = true
        }
    }

    LaunchedEffect(pendingLiveGroupScrollHeaderKey, visibleRows) {
        val headerKey = pendingLiveGroupScrollHeaderKey ?: return@LaunchedEffect
        val headerRowIndex = visibleRows.indexOfFirst { row -> row.item == headerKey }
        if (headerRowIndex < 0) return@LaunchedEffect
        val approxRowsAbove = (listState.layoutInfo.visibleItemsInfo.size / 2).coerceAtLeast(3)
        val targetIndex = (headerRowIndex - approxRowsAbove).coerceAtLeast(0)
        listState.animateScrollToItem(targetIndex)
        pendingLiveGroupScrollHeaderKey = null
    }

    fun closePlaylistSearch() {
        playlistSearchQuery = ""
        isSearchVisible = false
        focusManager.clearFocus(force = true)
    }

    suspend fun scrollKeyboardSelectionIntoView(targetItem: String) {
        val rowIndex = visibleRows.indexOfFirst { row -> row.item == targetItem }
        if (rowIndex >= 0) {
            listState.animateScrollToItem(rowIndex)
        }
    }

    suspend fun moveKeyboardSelection(
        command: HardwareListCommand,
        anchorToCurrentTrack: Boolean
    ) {
        val playableItems = visibleRows
            .map { it.item }
            .filter(::isKeyboardSelectablePlaylistItem)
        if (playableItems.isEmpty()) return

        val anchorItem = when {
            keyboardSelectedItem in playableItems -> keyboardSelectedItem
            anchorToCurrentTrack && currentPlayingPlaylist == internalSelected &&
                currentPlayingPlaylistItemKey in playableItems -> currentPlayingPlaylistItemKey
            currentPlayingPlaylist == internalSelected &&
                currentPlayingPlaylistItemKey in playableItems -> currentPlayingPlaylistItemKey
            currentPlayingUri in playableItems -> currentPlayingUri
            else -> playableItems.first()
        } ?: playableItems.first()

        val anchorIndex = playableItems.indexOf(anchorItem).coerceAtLeast(0)
        val targetIndex = when (command) {
            HardwareListCommand.MOVE_PREVIOUS -> (anchorIndex - 1).coerceAtLeast(0)
            HardwareListCommand.MOVE_NEXT -> (anchorIndex + 1).coerceAtMost(playableItems.lastIndex)
            HardwareListCommand.ACTIVATE -> anchorIndex
        }
        val targetItem = playableItems[targetIndex]
        keyboardSelectedItem = targetItem
        scrollKeyboardSelectionIntoView(targetItem)
    }

    fun moveSequentialSelection(delta: Int) {
        if (delta == 0) return
        val selectableItems = visibleRows.map { it.item }
        if (selectableItems.isEmpty()) return

        val anchorItem = when {
            keyboardSelectedItem in selectableItems -> keyboardSelectedItem
            currentPlayingPlaylist == internalSelected &&
                currentPlayingPlaylistItemKey in selectableItems -> currentPlayingPlaylistItemKey
            currentPlayingUri in selectableItems -> currentPlayingUri
            else -> selectableItems.first()
        } ?: selectableItems.first()

        val anchorIndex = selectableItems.indexOf(anchorItem).coerceAtLeast(0)
        val targetIndex = anchorIndex + delta
        if (targetIndex !in selectableItems.indices) return

        keyboardSelectedItem = selectableItems[targetIndex]
        onSequentialSelectionChanged()
    }

    LaunchedEffect(hardwareCommandToken, visibleRows, internalSelected) {
        if (hardwareCommandToken == 0) return@LaunchedEffect
        if (internalSelected.isNullOrBlank()) return@LaunchedEffect
        when (hardwareCommand) {
            HardwareListCommand.MOVE_PREVIOUS,
            HardwareListCommand.MOVE_NEXT -> moveKeyboardSelection(
                command = hardwareCommand,
                anchorToCurrentTrack = false
            )

            HardwareListCommand.ACTIVATE -> {
                val currentPlaylist = internalSelected ?: return@LaunchedEffect
                moveKeyboardSelection(
                    command = HardwareListCommand.ACTIVATE,
                    anchorToCurrentTrack = false
                )
                val targetItem = keyboardSelectedItem ?: return@LaunchedEffect
                val playbackItem = resolveVariantFamilyPlaybackItem(targetItem, variantFamilyById)
                saveOriginalOrderIfMissing(context, currentPlaylist, songs.toList())
                onPlaySong(playbackItem, currentPlaylist, Color.White)
                closePlaylistSearch()
            }
        }
    }

    LaunchedEffect(playbackControlActivateSelectedToken, visibleRows, internalSelected) {
        if (playbackControlActivateSelectedToken == 0) return@LaunchedEffect
        val currentPlaylist = internalSelected ?: return@LaunchedEffect
        val targetItem = keyboardSelectedItem
            ?.takeIf { selectedItem -> visibleRows.any { row -> row.item == selectedItem } }
            ?: return@LaunchedEffect
        val playbackItem = resolveVariantFamilyPlaybackItem(targetItem, variantFamilyById)
        saveOriginalOrderIfMissing(context, currentPlaylist, songs.toList())
        onPlaySong(playbackItem, currentPlaylist, Color.White)
        closePlaylistSearch()
    }

    LaunchedEffect(hardwareReturnToCurrentToken, visibleRows, internalSelected) {
        if (hardwareReturnToCurrentToken == 0) return@LaunchedEffect
        if (internalSelected.isNullOrBlank()) return@LaunchedEffect
        moveKeyboardSelection(
            command = hardwareReturnCommand,
            anchorToCurrentTrack = true
        )
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
                .padding(screenPadding)
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.quickplaylists_create_playlist_action),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                showMenu = false
                                createPlaylistName = ""
                                showCreatePlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.quickplaylists_save_current_list_action),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                showMenu = false
                                requestSaveCurrentPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.more_item_import_playlist),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                showMenu = false
                                importPlaylistLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/json",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.quickplaylists_create_prompter_action),
                                    color = Color.White
                                )
                            },
                            onClick = {
                                showMenu = false
                                newTextTitle = ""
                                newTextContent = ""
                                showCreateTextDialog = true
                            }
                        )

                        if (playlists.isEmpty()) {
                            DropdownMenuItem(
                                enabled = false,
                                text = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.quickplaylists_no_playlists_title),
                                            color = Color(0xFFCFD8DC),
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.quickplaylists_no_playlists_subtitle),
                                            color = Color(0xFF90A4AE),
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                onClick = {}
                            )
                        } else {
                            playlists.forEach { name ->
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
                                showResetPlaylistDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.common_cd_reset),
                                tint = Color(0xFFFFB74D)
                            )
                        }

                        IconButton(
                            onClick = {
                                onAddTrackToPlaylist(internalSelected ?: return@IconButton)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = stringResource(R.string.quickplaylists_add_track_button),
                                tint = Color(0xFF81C784)
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

            if (isSearchVisible) {
                SmpSearchField(
                    value = playlistSearchQuery,
                    onValueChange = { playlistSearchQuery = it },
                    placeholder = stringResource(R.string.common_search_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(searchFieldHeight)
                        .focusRequester(searchFocusRequester)
                        .semantics { testTag = "quick_playlists_search" },
                    textStyle = TextStyle(fontSize = searchTextSize),
                    leadingIconTint = Color(0xFFFFC107),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                playlistSearchQuery = ""
                                isSearchVisible = false
                                focusManager.clearFocus(force = true)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_cd_close)
                            )
                        }
                    }
                )

                Spacer(Modifier.height(topSpacerHeight))
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

                Spacer(Modifier.height(topSpacerHeight))
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
                    .padding(rackPadding)
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
                        itemsIndexed(visibleRows, key = { _, row -> playlistViewportStableKey(row.item) }) { _, row ->
                            val itemIndex = row.realIndex
                            val uriString = row.item

                            getVariantFamilyId(uriString)?.let { familyId ->
                                val family = variantFamilyForPlaylistItem(uriString, variantFamilyById)
                                if (family == null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight)
                                            .padding(vertical = rowOuterVerticalPadding, horizontal = 2.dp)
                                            .background(Color(0xFF181818), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = quickPlaylistFallbackName(uriString).uppercase(),
                                            color = Color.White.copy(alpha = 0.65f),
                                            fontSize = if (compactTabletLayout) 12.sp else 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box {
                                            var menuOpen by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuOpen = true }) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = stringResource(R.string.common_cd_options),
                                                    tint = Color.White.copy(alpha = 0.75f)
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
                                                            stringResource(R.string.quickplaylists_menu_remove_from_playlist),
                                                            color = Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        internalSelected?.let { pl ->
                                                            PlaylistRepository.removeSongFromPlaylist(pl, uriString)
                                                        }
                                                        songs.remove(uriString)
                                                        menuOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    return@itemsIndexed
                                }

                                val activeSongId = activeSongIdForFamily(family)
                                val activePlaybackItem = activeSongId?.let(::buildSmpItem) ?: uriString
                                val isExpanded = family.id in expandedVariantFamilyIds
                                val isKeyboardSelected = compactTabletLayout && keyboardSelectedItem == uriString
                                val isCurrentPlaying = currentPlayingUri == activePlaybackItem ||
                                    currentPlayingPlaylistItemKey == activePlaybackItem
                                val isForcedNext = nextTrackUri != null && nextTrackUri == activePlaybackItem
                                val familyAliasTitle = cleanQuickPlaylistTitle(
                                    TitleAliasesStore.getTitleForTrack(context, uriString)
                                )
                                val displayTitle = familyAliasTitle
                                    ?: activeSongId?.let(smpTitleById::get)
                                    ?: family.title
                                val _forceFamilyColor = songColorsVersion
                                val customFamilyColor = internalSelected?.let { pl ->
                                    loadSongColor(context, pl, uriString)
                                }
                                val familyTitleColor = when {
                                    customFamilyColor != null -> customFamilyColor
                                    isCurrentPlaying -> Color(0xFFFFFDE7)
                                    else -> Color.White
                                }
                                val isFamilyInsideGroup = isItemInsideGroup(songs, itemIndex)
                                val orderedSongIds = remember(family) {
                                    buildList {
                                        family.parentSongId?.takeIf { it in family.songIds }?.let(::add)
                                        family.songIds.sorted().forEach { songId ->
                                            if (songId !in this) add(songId)
                                        }
                                    }
                                }
                                val rowShape = RoundedCornerShape(10.dp)
                                val familyBackground = when {
                                    isKeyboardSelected -> Color(0x224FC3F7)
                                    isCurrentPlaying -> Color(0xFF202020)
                                    else -> Color(0xFF181818)
                                }
                                val familyBorder = when {
                                    isKeyboardSelected -> Color(0xCC4FC3F7)
                                    isCurrentPlaying -> Color.White.copy(alpha = 0.75f)
                                    else -> Color.White.copy(alpha = 0.20f)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = if (compactTabletLayout) 2.dp else 3.dp,
                                            horizontal = 2.dp
                                        )
                                        .background(familyBackground, rowShape)
                                        .border(
                                            width = if (isKeyboardSelected || isCurrentPlaying) 2.dp else 1.dp,
                                            color = familyBorder,
                                            shape = rowShape
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight - 8.dp)
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isExpanded) "▼" else "▶",
                                            color = currentListColor,
                                            fontSize = titleFontSize,
                                            modifier = Modifier
                                                .width(28.dp)
                                                .clickable {
                                                    expandedVariantFamilyIds = if (isExpanded) {
                                                        expandedVariantFamilyIds - family.id
                                                    } else {
                                                        expandedVariantFamilyIds + family.id
                                                    }
                                                }
                                        )
                                        Text(
                                            text = displayTitle.uppercase(),
                                            color = familyTitleColor,
                                            fontSize = titleFontSize,
                                            fontWeight = if (isCurrentPlaying) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    keyboardSelectedItem = uriString
                                                    val currentPlaylist = internalSelected ?: return@clickable
                                                    saveOriginalOrderIfMissing(context, currentPlaylist, songs.toList())
                                                    onPlaySong(activePlaybackItem, currentPlaylist, Color.White)
                                                    closePlaylistSearch()
                                                }
                                        )
                                        if (isForcedNext) {
                                            Text(
                                                text = stringResource(R.string.quickplaylists_badge_next_forced),
                                                color = Color.White,
                                                fontSize = badgeFontSize,
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .background(Color(0xFFD32F2F), RoundedCornerShape(999.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        Box {
                                            var menuOpen by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuOpen = true }) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = stringResource(R.string.common_cd_options),
                                                    tint = currentListColor
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuOpen,
                                                onDismissRequest = { menuOpen = false },
                                                modifier = Modifier.background(Color(0xFF1E1E1E))
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_play), color = Color.White) },
                                                    onClick = {
                                                        val currentPlaylist = internalSelected
                                                        if (currentPlaylist != null) {
                                                            val visibleQueue = resolveVariantFamilyPlaybackQueue(songs.toList(), variantFamilyById)
                                                            val startIndex = songs.indexOf(uriString)
                                                            if (startIndex >= 0) {
                                                                activePlayingGroupHeaderKey = null
                                                                onPlayFromHere(visibleQueue, startIndex, currentPlaylist)
                                                                closePlaylistSearch()
                                                            }
                                                        }
                                                        menuOpen = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_define_next), color = Color.White) },
                                                    onClick = {
                                                        onSetNextTrack(activePlaybackItem, displayTitle, internalSelected)
                                                        menuOpen = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (findGroupHeaderKeyByTitle(sQuickplaylistsCurrentGroup) == null) {
                                                                sQuickplaylistsCreateLiveList
                                                            } else {
                                                                sQuickplaylistsAddToLiveList
                                                            },
                                                            color = Color.White
                                                        )
                                                    },
                                                    onClick = {
                                                        val pl = internalSelected
                                                        if (pl != null) {
                                                            val (headerKey, createdNow) = ensureCurrentGroupHeader(pl)
                                                            val currentTrackIndex = if (createdNow) {
                                                                findCurrentPlayingTrackIndexInPlaylist(pl)
                                                            } else {
                                                                null
                                                            }
                                                            val clickedWasCurrentTrack = currentTrackIndex == itemIndex
                                                            if (createdNow && currentTrackIndex != null) {
                                                                moveTrackToGroupHeader(
                                                                    playlist = pl,
                                                                    trackIndex = currentTrackIndex,
                                                                    headerKey = headerKey
                                                                )
                                                            }
                                                            if (!clickedWasCurrentTrack) {
                                                                val adjustedTrackIndex = when {
                                                                    createdNow && currentTrackIndex != null && currentTrackIndex < itemIndex -> itemIndex - 1
                                                                    else -> itemIndex
                                                                }
                                                                moveTrackToGroupHeader(
                                                                    playlist = pl,
                                                                    trackIndex = adjustedTrackIndex,
                                                                    headerKey = headerKey
                                                                )
                                                            }
                                                            activePlayingGroupHeaderKey = headerKey
                                                            pendingLiveGroupScrollHeaderKey = headerKey
                                                            val currentGroupedTrackIndex = findCurrentPlayingTrackIndexInPlaylist(pl)
                                                            val currentGroupedTrack = currentGroupedTrackIndex?.let(songs::getOrNull)
                                                            val headerIndex = songs.indexOf(headerKey)
                                                            if (currentGroupedTrack != null && headerIndex >= 0) {
                                                                val groupRange = findGroupRange(songs, headerIndex)
                                                                if (!groupRange.isEmpty()) {
                                                                    val groupQueue = ((groupRange.first + 1)..groupRange.last)
                                                                        .map { idx -> songs[idx] }
                                                                        .filter { item ->
                                                                            !isGroupHeader(item) &&
                                                                                !isGroupEnd(item) &&
                                                                                (isPlayableAudioItem(item) || isVariantFamilyItem(item))
                                                                        }
                                                                    val currentQueueIndex = groupQueue.indexOf(currentGroupedTrack)
                                                                    if (currentQueueIndex >= 0) {
                                                                        onArmChainFromCurrent(
                                                                            resolveVariantFamilyPlaybackQueue(groupQueue, variantFamilyById),
                                                                            currentQueueIndex,
                                                                            pl
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        menuOpen = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_insert_group_above), color = Color.White) },
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
                                                                persistSongsOrder(pl, overwriteOriginal = true)
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
                                                            key in selectedTrackKeys && isPlaylistGroupableItem(key)
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
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_also_add_to_group), color = Color.White) },
                                                    onClick = {
                                                        openAlsoAddDialogForTarget(activePlaybackItem)
                                                        menuOpen = false
                                                    },
                                                    enabled = songs.any { isGroupHeader(it) } && getSmpSongId(activePlaybackItem) != null
                                                )
                                                if (isFamilyInsideGroup) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.quickplaylists_menu_remove_from_group), color = Color.White) },
                                                        onClick = {
                                                            val selectedBatch = songs.filter { key ->
                                                                key in selectedTrackKeys && isPlaylistGroupableItem(key)
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
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    color = Color(0xFF2F2F2F)
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_move_to), color = Color.White) },
                                                    onClick = {
                                                        openMoveDialogForTarget(uriString)
                                                        menuOpen = false
                                                    }
                                                )
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
                                                            PlaylistRepository.removeSongFromPlaylist(pl, uriString)
                                                        }
                                                        songs.remove(uriString)
                                                        menuOpen = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.common_rename), color = Color.White) },
                                                    onClick = {
                                                        renameTarget = uriString
                                                        renameText = displayTitle
                                                        menuOpen = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.quickplaylists_menu_title_color), color = Color.White) },
                                                    onClick = { }
                                                )
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val colors = listOf(
                                                        Color(0xFFD32F2F),
                                                        Color(0xFFFFEB3B),
                                                        Color(0xFF1976D2),
                                                        Color(0xFFFF9800),
                                                        Color(0xFF388E3C),
                                                        Color(0xFF7B1FA2),
                                                        Color(0xFF00ACC1),
                                                        Color(0xFFE0E0E0)
                                                    )
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
                                                                        val selectedBatch = songs.filter { key ->
                                                                            key in selectedTrackKeys &&
                                                                                (isPlayableAudioItem(key) || isVariantFamilyItem(key))
                                                                        }
                                                                        val targets = if (
                                                                            uriString in selectedTrackKeys &&
                                                                            selectedBatch.size > 1
                                                                        ) {
                                                                            selectedBatch
                                                                        } else {
                                                                            listOf(uriString)
                                                                        }
                                                                        targets.forEach { targetUri ->
                                                                            saveSongColor(context, pl, targetUri, c)
                                                                        }
                                                                        selectedTrackKeys = selectedTrackKeys - targets.toSet()
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

                                    if (isExpanded) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            orderedSongIds
                                                .filter { it != activeSongId }
                                                .forEach { songId ->
                                                    val variantTitle = smpTitleById[songId] ?: songId
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(34.dp)
                                                            .background(Color.White.copy(alpha = 0.045f))
                                                            .clickable {
                                                                if (variantFamilyById[family.id] == null) {
                                                                    SongVariantFamiliesStore.upsertFamily(
                                                                        context,
                                                                        family.copy(activeSongId = songId)
                                                                    )
                                                                }
                                                                SongVariantFamiliesStore.setActiveSongId(context, family.id, songId)
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
                                                                .background(currentListColor.copy(alpha = 0.45f))
                                                        )
                                                        Text(
                                                            text = variantTitle.uppercase(),
                                                            color = Color.White.copy(alpha = 0.78f),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                        }
                                    }
                                }
                            }
                                return@itemsIndexed
                            }

                            if (isGroupHeader(uriString)) {
                                val groupTitle = getGroupTitle(uriString).uppercase()
                                val headerKey = uriString
                                val isKeyboardSelectedGroup = compactTabletLayout && keyboardSelectedItem == uriString
                                val isActivePlayingGroup = activePlayingGroupHeaderKey == headerKey
                                val isDraggingThis = draggingUri == uriString
                                val isCollapsed = collapsedGroupIds.contains(headerKey)
                                val isDraggingTrack = draggingUri?.let { !isGroupHeader(it) } == true
                                val isDropTargetHeader = isDraggingTrack && hoverHeaderKey == uriString
                                val groupRange = findGroupRange(songs, itemIndex)
                                val groupDurationStats = calculatePlaylistGroupDurationStats(
                                    items = songs,
                                    headerIndex = itemIndex,
                                    familyById = variantFamilyById,
                                    smpPlaybackUriById = smpPlaybackUriById,
                                    durationCache = durationCache
                                )
                                val groupMetaText = buildString {
                                    append(groupDurationStats.trackCount)
                                    append(" • ")
                                    append(
                                        when {
                                            groupDurationStats.trackCount == 0 -> formatDuration(0L)
                                            groupDurationStats.hasUnknownDuration -> "…"
                                            else -> formatDuration(groupDurationStats.knownDurationMs)
                                        }
                                    )
                                }
                                val folderBlue = Color(0xFF0A6C97)
                                val folderBlueBorder = Color(0xFF07506F)
                                val groupColorArgb = getGroupColorArgb(uriString)
                                val groupColor = groupColorArgb?.let { Color(it) } ?: folderBlue
                                val groupBorderColor = if (groupColorArgb == null) {
                                    folderBlueBorder
                                } else {
                                    groupColor.copy(alpha = 0.82f)
                                }
                                val activeGroupRed = Color(0xFFD32F2F)
                                val activeGroupRedBorder = Color(0xFF9A0007)
                                val animatedActiveGroupRed = if (isActivePlayingGroup && isPlaying) {
                                    lerp(activeGroupRed, Color(0xFFE14B4B), activeGroupPulseFraction)
                                } else {
                                    activeGroupRed
                                }
                                val headerText = Color.White
                                val headerMuted = Color.White.copy(alpha = 0.75f)
                                val headerChevron = Color.White.copy(alpha = 0.60f)
                                val badgeBg = Color.White.copy(alpha = 0.18f)
                                val badgeBorder = Color.White.copy(alpha = 0.30f)
                                val dragTint = if (isDraggingThis) headerText else headerMuted
                                val rowBorder = when {
                                    isKeyboardSelectedGroup -> Color(0xCC4FC3F7)
                                    isDropTargetHeader -> Color.White.copy(alpha = 0.70f)
                                    isActivePlayingGroup -> activeGroupRedBorder
                                    else -> groupBorderColor
                                }
                                val rowBackground = when {
                                    isKeyboardSelectedGroup -> Color(0xFF0B4F6E)
                                    isDropTargetHeader -> Color(0xFF1184B8)
                                    isActivePlayingGroup -> animatedActiveGroupRed
                                    else -> groupColor
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight)
                                        .padding(vertical = rowOuterVerticalPadding, horizontal = 2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(rowBackground)
                                        .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = rowHorizontalPadding),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.common_cd_move),
                                        tint = dragTint,
                                        modifier = Modifier
                                            .size(dragHandleSize)
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
                                    if (isActivePlayingGroup) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = headerText,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .padding(end = 8.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                onClick = {
                                                    collapsedGroupIds = if (isCollapsed) {
                                                        collapsedGroupIds - headerKey
                                                    } else {
                                                        collapsedGroupIds + headerKey
                                                    }
                                                },
                                                onLongClick = {
                                                    val currentPlaylist = internalSelected
                                                        ?: return@combinedClickable
                                                    if (groupRange.isEmpty()) return@combinedClickable
                                                    val groupQueue = (groupRange.first + 1..groupRange.last)
                                                        .map { idx -> songs[idx] }
                                                        .filter { item ->
                                                            !isGroupHeader(item) &&
                                                                !isGroupEnd(item) &&
                                                                (isPlayableAudioItem(item) || isVariantFamilyItem(item))
                                                        }
                                                    if (groupQueue.isNotEmpty()) {
                                                        activePlayingGroupHeaderKey = headerKey
                                                        onPlayFromHere(
                                                            resolveVariantFamilyPlaybackQueue(groupQueue, variantFamilyById),
                                                            0,
                                                            currentPlaylist
                                                        )
                                                    }
                                                }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = groupTitle,
                                            color = headerText,
                                            fontSize = titleFontSize,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = groupMetaText,
                                            color = headerMuted,
                                            fontSize = groupMetaFontSize
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
                                                    fontSize = badgeFontSize,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isCollapsed) "▶" else "▼",
                                        color = headerChevron,
                                        fontSize = if (compactTabletLayout) 14.sp else 16.sp,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )

                                    Box {
                                        var menuOpen by remember { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .size(rowMenuSize)
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
                                                        stringResource(R.string.quickplaylists_menu_change_group_color),
                                                        color = Color.White
                                                    )
                                                },
                                                onClick = {
                                                    groupColorTarget = uriString
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
                                                            if (activePlayingGroupHeaderKey == headerKey) {
                                                                activePlayingGroupHeaderKey = null
                                                            }
                                                            persistSongsOrder(pl, overwriteOriginal = true)
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

                            val smpSongId = getSmpSongId(uriString)

                            // 🔹 NOM D’AFFICHAGE
                            val _forceNotes = notesVersion
                            val smpAliasTitle = if (smpSongId != null) {
                                cleanQuickPlaylistTitle(TitleAliasesStore.getTitleForTrack(context, buildSmpItem(smpSongId)))
                            } else {
                                null
                            }
                            val smpCustomTitle = if (smpSongId != null) {
                                cleanQuickPlaylistTitle(PlaylistRepository.getAnyCustomTitleForUri(uriString))
                            } else {
                                null
                            }
                            val smpLibraryTitle = if (smpSongId != null) {
                                cleanQuickPlaylistTitle(smpTitleById[smpSongId])
                                    ?: cleanQuickPlaylistTitle(smpSongsById[smpSongId]?.title)
                            } else {
                                null
                            }
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
                                if (smpSongId != null) {
                                    smpCustomTitle
                                        ?: smpLibraryTitle
                                        ?: smpAliasTitle
                                        ?: stringResource(R.string.quickplaylists_missing_title)
                                } else {
                                    // 👉 Audio normal (alias global)
                                    cleanQuickPlaylistTitle(TitleAliasesStore.getTitleForTrack(context, uriString))
                                        ?: cleanQuickPlaylistTitle(PlaylistRepository.getAnyCustomTitleForUri(uriString))
                                        ?: baseNameClean
                                }
                            }
                            if (smpSongId != null) {
                                Log.d(
                                    "PLAYLIST_DIAG",
                                    "itemUri=$uriString songId=$smpSongId rawTitle=$baseNameClean alias=${smpAliasTitle ?: "null"} libraryTitle=${smpLibraryTitle ?: "null"} resolvedTitle=$displayName"
                                )
                                if (internalSelected == "SPL Demo") {
                                    Log.i(
                                        DEMO_TITLES_TAG,
                                        "ui:resolve playlist=SPL Demo uri=$uriString songId=$smpSongId alias=${smpAliasTitle ?: "null"} custom=${smpCustomTitle ?: "null"} smpTitle=${smpLibraryTitle ?: "null"} final=$displayName fallback=${displayName == context.getString(R.string.quickplaylists_missing_title)}"
                                    )
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

                            val playlistItemPlaybackKey = canonicalPlaylistPlaybackKey(
                                playlistItemKey = uriString,
                                playbackUri = uriString
                            )
                            val hasCurrentPlaylistItemKey = !currentPlayingPlaylistItemKey.isNullOrBlank()
                            val isCurrentPlaying = if (hasCurrentPlaylistItemKey) {
                                currentPlayingPlaylist == internalSelected &&
                                    currentPlayingPlaylistItemKey == playlistItemPlaybackKey
                            } else {
                                currentPlayingUri == uriString
                            }
                            val isDraggingThis = draggingUri == uriString
                            val isChainedNext = nextChainedUri != null && uriString == nextChainedUri
                            val isForcedNext = nextTrackUri != null && uriString == nextTrackUri
                            val isSelected = selectedTrackKeys.contains(uriString)
                            val isKeyboardSelected = keyboardSelectedItem == uriString && !isSelected
                            val showCurrentMarker = isCurrentPlaying && !isSelected
                            val containingGroupHeaderIndex = findContainingGroupHeaderIndex(songs, itemIndex)
                            val isInsideGroup =
                                isPlaylistGroupableItem(uriString) && isItemInsideGroup(songs, itemIndex)
                            val activeGroupHeaderKey = containingGroupHeaderIndex?.let { idx -> songs.getOrNull(idx) }
                            val isInsideActivePlayingGroup =
                                isInsideGroup && activeGroupHeaderKey == activePlayingGroupHeaderKey
                            val rowShape = RoundedCornerShape(12.dp)
                            val containingGroupColor = activeGroupHeaderKey
                                ?.let { getGroupColorArgb(it) }
                                ?.let { Color(it) }
                                ?: Color(0xFF0A6C97)
                            val groupTint = containingGroupColor.copy(alpha = 0.38f)
                            val groupAccent = containingGroupColor.copy(alpha = 0.95f)
                            val activeGroupTint = Color(0xFFD32F2F).copy(alpha = 0.30f)
                            val activeGroupAccent = Color(0xFFD32F2F).copy(alpha = 0.92f)
                            val selectedBorderColor = Color(0xCC4FC3F7)
                            val selectedMarkerColor = Color(0xFF4FC3F7)
                            val rowBaseBackground = if (isDraggingThis)
                                Color(0x33FFFFFF)
                            else if (isSelected)
                                Color(0x14FFFFFF)
                            else if (isKeyboardSelected)
                                Color(0x224FC3F7)
                            else if (isForcedNext)
                                Color(0x33D32F2F)
                            else if (isChainedNext)
                                Color(0x22FFFFFF)
                            else
                                Color(0xFF181818)
                            val rowBorderWidth = if (isSelected) 2.dp else 1.dp
                            val rowBorderColor = if (isSelected)
                                selectedBorderColor
                            else if (isKeyboardSelected)
                                selectedBorderColor.copy(alpha = 0.9f)
                            else if (isCurrentPlaying)
                                Color.White.copy(alpha = 0.8f)
                            else if (isForcedNext)
                                Color(0x99FF8A80)
                            else if (isChainedNext)
                                Color(0x66FFD54F)
                            else
                                Color(0x33FFFFFF)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(vertical = rowOuterVerticalPadding, horizontal = 2.dp)
                                    .background(
                                        color = rowBaseBackground,
                                        shape = rowShape
                                    )
                                    .then(
                                        if (isInsideActivePlayingGroup) {
                                            Modifier.background(color = activeGroupTint, shape = rowShape)
                                        } else if (isInsideGroup) {
                                            Modifier.background(color = groupTint, shape = rowShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .border(
                                        width = rowBorderWidth,
                                        color = rowBorderColor,
                                        shape = rowShape
                                    )
                                    .padding(horizontal = songRowHorizontalPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .width(4.dp)
                                            .height(markerHeight)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(selectedMarkerColor)
                                    )
                                }
                                if (showCurrentMarker) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .width(3.dp)
                                            .height(groupAccentHeight)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color(0xFFFFFDE7).copy(alpha = 0.9f))
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.common_cd_move),
                                    tint = if (isPlayed) Color(0xFF9E9E9E) else Color.White,
                                    modifier = Modifier
                                        .size(dragHandleSize)
                                        .padding(end = 6.dp)
                                        .alpha(if (isPlayed) 0.6f else 1f)
                                        .then(dragHandleModifier(uriString))
                                )
                                if (isInsideGroup) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .width(3.dp)
                                            .height(groupAccentHeight)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isInsideActivePlayingGroup) activeGroupAccent else groupAccent)
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
                                    customSongColor != null -> customSongColor
                                    isCurrentPlaying -> Color(0xFFFFFDE7)
                                    isPlayed -> playedTextColor
                                    else -> normalTitleColor
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .combinedClickable(
                                            onClick = {
                                                keyboardSelectedItem = uriString
                                                val currentPlaylist = internalSelected
                                                    ?: return@combinedClickable
                                                val currentGroupHeaderKey = containingGroupHeaderIndex
                                                    ?.let { songs.getOrNull(it) }
                                                val isInsideCurrentQueueGroup =
                                                    currentGroupHeaderKey != null &&
                                                        getGroupTitle(currentGroupHeaderKey) == sQuickplaylistsCurrentGroup
                                                if (activePlayingGroupHeaderKey != null && !isInsideActivePlayingGroup) {
                                                    activePlayingGroupHeaderKey = null
                                                }
                                                // ✅ IMPORTANT : on capture l'ordre "d'origine" AVANT que le système
                                                // ne pousse une chanson jouée en bas.
                                                // Persistant => ça survit au redémarrage.
                                                saveOriginalOrderIfMissing(context, currentPlaylist, songs.toList())
                                                if (isInsideCurrentQueueGroup && containingGroupHeaderIndex != null) {
                                                    val groupRange = findGroupRange(songs, containingGroupHeaderIndex)
                                                    val groupQueue = if (groupRange.isEmpty()) {
                                                        emptyList()
                                                    } else {
                                                        ((groupRange.first + 1)..groupRange.last)
                                                            .map { idx -> songs[idx] }
                                                            .filter { item ->
                                                                !isGroupHeader(item) &&
                                                                    !isGroupEnd(item) &&
                                                                    (isPlayableAudioItem(item) || isVariantFamilyItem(item))
                                                            }
                                                    }
                                                    val groupStartIndex = groupQueue.indexOf(uriString)
                                                    if (groupStartIndex >= 0) {
                                                        activePlayingGroupHeaderKey = currentGroupHeaderKey
                                                        onPlayFromHere(
                                                            resolveVariantFamilyPlaybackQueue(groupQueue, variantFamilyById),
                                                            groupStartIndex,
                                                            currentPlaylist
                                                        )
                                                    } else {
                                                        onPlaySong(uriString, currentPlaylist, Color.White)
                                                    }
                                                } else {
                                                    // ✅ Lance le player
                                                    onPlaySong(uriString, currentPlaylist, Color.White) // ✅ ne teinte plus le lecteur / paroles
                                                }
                                                closePlaylistSearch()
                                                // ⚠️ IMPORTANT : on NE rappelle PAS onSelectedPlaylistChange(currentPlaylist) ici,
                                                // sinon le parent peut recharger la playlist immédiatement (LaunchedEffect),
                                                // ce qui donne l'impression que le titre "descend direct".
                                            },
                                            onLongClick = {
                                                if (!isPlaylistGroupableItem(uriString)) return@combinedClickable
                                                keyboardSelectedItem = uriString
                                                selectedTrackKeys = if (selectedTrackKeys.contains(uriString)) {
                                                    selectedTrackKeys - uriString
                                                } else {
                                                    selectedTrackKeys + uriString
                                                }
                                            }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (prefix + displayName).uppercase(),
                                        color = titleColor,
                                        fontSize = titleFontSize,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isPlayed) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "✔",
                                            color = Color(0xFFD0D0D0),
                                            fontSize = secondaryFontSize
                                        )
                                    }
                                }

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
                                            fontSize = badgeFontSize
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
                                            fontSize = badgeFontSize
                                        )
                                    }
                                }

                                // menu 3 points
                                Box {
                                    var menuOpen by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .size(rowMenuSize)
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
                                        val groupMenuBackground = Color(0xFF0A6C97).copy(alpha = 0.46f)
                                        val groupMenuBorder = Color(0xFF75C7E8).copy(alpha = 0.55f)
                                        val groupMenuIcon = Color(0xFFB3E5FC)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF388E3C).copy(alpha = 0.9f))
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFF3E0),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.quickplaylists_menu_play),
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            onClick = {
                                                val pl = internalSelected
                                                if (pl != null) {
                                                    val rawQueue = songs.toList()
                                                    val visibleQueue = resolveVariantFamilyPlaybackQueue(rawQueue, variantFamilyById)
                                                    val startIndex = rawQueue.indexOf(uriString)
                                                    if (startIndex >= 0) {
                                                        activePlayingGroupHeaderKey = null
                                                        onPlayFromHere(visibleQueue, startIndex, pl)
                                                        closePlaylistSearch()
                                                    }
                                                }
                                                menuOpen = false
                                            }
                                        )
                                        if (isPlayableAudioItem(uriString)) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.quickplaylists_menu_define_next), color = Color.White) },
                                                onClick = {
                                                    onSetNextTrack(uriString, displayName, internalSelected)
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFD32F2F).copy(alpha = 0.85f))
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.LibraryMusic,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE0E0E0),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        if (findGroupHeaderKeyByTitle(sQuickplaylistsCurrentGroup) == null) {
                                                            sQuickplaylistsCreateLiveList
                                                        } else {
                                                            sQuickplaylistsAddToLiveList
                                                        },
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            onClick = {
                                                val pl = internalSelected
                                                if (pl != null) {
                                                    val (headerKey, createdNow) = ensureCurrentGroupHeader(pl)
                                                    val currentTrackIndex = if (createdNow) {
                                                        findCurrentPlayingTrackIndexInPlaylist(pl)
                                                    } else {
                                                        null
                                                    }
                                                    val clickedWasCurrentTrack = currentTrackIndex == itemIndex
                                                    if (createdNow && currentTrackIndex != null) {
                                                        moveTrackToGroupHeader(
                                                            playlist = pl,
                                                            trackIndex = currentTrackIndex,
                                                            headerKey = headerKey
                                                        )
                                                    }
                                                    if (!clickedWasCurrentTrack) {
                                                        val adjustedTrackIndex = when {
                                                            createdNow && currentTrackIndex != null && currentTrackIndex < itemIndex -> itemIndex - 1
                                                            else -> itemIndex
                                                        }
                                                        moveTrackToGroupHeader(
                                                            playlist = pl,
                                                            trackIndex = adjustedTrackIndex,
                                                            headerKey = headerKey
                                                        )
                                                    }
                                                    activePlayingGroupHeaderKey = headerKey
                                                    pendingLiveGroupScrollHeaderKey = headerKey
                                                    val currentGroupedTrackIndex = findCurrentPlayingTrackIndexInPlaylist(pl)
                                                    val currentGroupedTrack = currentGroupedTrackIndex
                                                        ?.let(songs::getOrNull)
                                                    val headerIndex = songs.indexOf(headerKey)
                                                    if (currentGroupedTrack != null && headerIndex >= 0) {
                                                        val groupRange = findGroupRange(songs, headerIndex)
                                                        if (!groupRange.isEmpty()) {
                                                            val groupQueue = ((groupRange.first + 1)..groupRange.last)
                                                                .map { idx -> songs[idx] }
                                                                .filter { item ->
                                                                    !isGroupHeader(item) &&
                                                                        !isGroupEnd(item) &&
                                                                        (isPlayableAudioItem(item) || isVariantFamilyItem(item))
                                                                }
                                                            val currentQueueIndex = groupQueue.indexOf(currentGroupedTrack)
                                                            if (currentQueueIndex >= 0) {
                                                                onArmChainFromCurrent(
                                                                    resolveVariantFamilyPlaybackQueue(groupQueue, variantFamilyById),
                                                                    currentQueueIndex,
                                                                    pl
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                menuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(groupMenuBackground)
                                                        .border(1.dp, groupMenuBorder, RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Folder,
                                                        contentDescription = null,
                                                        tint = groupMenuIcon,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.quickplaylists_menu_insert_group_above),
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
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
                                                        persistSongsOrder(pl, overwriteOriginal = true)
                                                        renameGroupTarget = header
                                                        renameGroupText = getGroupTitle(header)
                                                    }
                                                }
                                                menuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(groupMenuBackground)
                                                        .border(1.dp, groupMenuBorder, RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Folder,
                                                        contentDescription = null,
                                                        tint = groupMenuIcon,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.quickplaylists_menu_assign_to_group),
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            onClick = {
                                                val selectedBatch = songs.filter { key ->
                                                    key in selectedTrackKeys && isPlaylistGroupableItem(key)
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
                                        if (smpSongId != null) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(groupMenuBackground)
                                                            .border(1.dp, groupMenuBorder, RoundedCornerShape(12.dp))
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Folder,
                                                            contentDescription = null,
                                                            tint = groupMenuIcon,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            stringResource(R.string.quickplaylists_menu_also_add_to_group),
                                                            color = Color.White,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                onClick = {
                                                    openAlsoAddDialogForTarget(uriString)
                                                    menuOpen = false
                                                },
                                                enabled = songs.any { isGroupHeader(it) }
                                            )
                                        }
                                        if (isInsideGroup) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(groupMenuBackground)
                                                            .border(1.dp, groupMenuBorder, RoundedCornerShape(12.dp))
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Folder,
                                                            contentDescription = null,
                                                            tint = groupMenuIcon,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            stringResource(R.string.quickplaylists_menu_remove_from_group),
                                                            color = Color.White,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                onClick = {
                                                    val selectedBatch = songs.filter { key ->
                                                        key in selectedTrackKeys && isPlaylistGroupableItem(key)
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
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            color = Color(0xFF2F2F2F)
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.quickplaylists_menu_move_to),
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                openMoveDialogForTarget(uriString)
                                                menuOpen = false
                                            }
                                        )
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
                                                                val selectedBatch = songs.filter { key ->
                                                                    key in selectedTrackKeys &&
                                                                        (isPlayableAudioItem(key) || isVariantFamilyItem(key))
                                                                }
                                                                val targets = if (
                                                                    uriString in selectedTrackKeys &&
                                                                    selectedBatch.size > 1
                                                                ) {
                                                                    selectedBatch
                                                                } else {
                                                                    listOf(uriString)
                                                                }
                                                                targets.forEach { targetUri ->
                                                                    saveSongColor(context, pl, targetUri, c)
                                                                }
                                                                selectedTrackKeys = selectedTrackKeys - targets.toSet()
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

            if (compactTabletLayout) {
                SequentialNavigation(
                    modifier = Modifier.fillMaxWidth(),
                    onSelectPrevious = { moveSequentialSelection(-1) },
                    onSelectNext = { moveSequentialSelection(1) }
                )
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
                persistSongsOrder(pl, overwriteOriginal = true)
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

    if (groupColorTarget != null && internalSelected != null) {
        val target = groupColorTarget
        val selectedColor = target?.let { getGroupColorArgb(it) }
        AlertDialog(
            onDismissRequest = { groupColorTarget = null },
            title = {
                Text(
                    stringResource(R.string.quickplaylists_group_color_title),
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPlaylistGroupColorOptions().forEach { option ->
                        val isSelectedColor = option.argb == selectedColor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val pl = internalSelected
                                    val currentTarget = groupColorTarget
                                    if (pl != null && currentTarget != null) {
                                        val index = songs.indexOf(currentTarget)
                                        if (index >= 0 && isGroupHeader(songs[index])) {
                                            val nextHeader = setGroupColorArgb(songs[index], option.argb)
                                            songs[index] = nextHeader
                                            collapsedGroupIds = if (currentTarget in collapsedGroupIds) {
                                                collapsedGroupIds - currentTarget + nextHeader
                                            } else {
                                                collapsedGroupIds
                                            }
                                            if (activePlayingGroupHeaderKey == currentTarget) {
                                                activePlayingGroupHeaderKey = nextHeader
                                            }
                                            persistSongsOrder(pl, overwriteOriginal = true)
                                        }
                                    }
                                    groupColorTarget = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val swatchColor = option.argb?.let { Color(it) } ?: Color(0xFF0A6C97)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelectedColor) 2.dp else 1.dp,
                                        color = if (isSelectedColor) Color.White else Color.White.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(option.labelRes),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { groupColorTarget = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    if (alsoAddGroupTargetUri != null && internalSelected != null) {
        AlertDialog(
            onDismissRequest = {
                alsoAddGroupTargetUri = null
                alsoAddGroupOptions = emptyList()
            },
            title = { Text(stringResource(R.string.quickplaylists_also_add_group_title), color = Color.White) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    alsoAddGroupOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                val targetUri = alsoAddGroupTargetUri
                                if (!targetUri.isNullOrBlank()) {
                                    addSharedOccurrenceToGroup(targetUri, option)
                                }
                                alsoAddGroupTargetUri = null
                                alsoAddGroupOptions = emptyList()
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
                        alsoAddGroupTargetUri = null
                        alsoAddGroupOptions = emptyList()
                    }
                ) {
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
                                        val snapshot = songs.toList()
                                        PlaylistRepository.updatePlayListOrder(pl, snapshot)
                                        scope.launch(Dispatchers.IO) {
                                            saveManualOrder(context, pl, snapshot)
                                        }
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

    if (moveTargetUri != null && internalSelected != null) {
        AlertDialog(
            onDismissRequest = {
                moveTargetUri = null
                moveTargetOptions = emptyList()
            },
            title = {
                Text(
                    stringResource(R.string.quickplaylists_move_dialog_title),
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    moveTargetOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                val targetUri = moveTargetUri
                                if (!targetUri.isNullOrBlank()) {
                                    moveTargetToOption(targetUri, option)
                                }
                                moveTargetUri = null
                                moveTargetOptions = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option.label,
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        moveTargetUri = null
                        moveTargetOptions = emptyList()
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
                val aliasTarget = getSmpSongId(targetUri)?.let { buildSmpItem(it) } ?: targetUri
                if (BuildConfig.DEBUG) {
                    Log.d("ALIAS_RENAME", "commit source=playlist uri=$aliasTarget newTitle='$newTitle'")
                }
                val saved = TitleAliasesStore.setTitleForTrack(context, aliasTarget, newTitle)
                if (saved) {
                    PlaylistRepository.clearCustomTitleEverywhere(aliasTarget)
                    if (aliasTarget != targetUri) {
                        PlaylistRepository.clearCustomTitleEverywhere(targetUri)
                    }
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

    playlistImportResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { playlistImportResultMessage = null },
            title = {
                Text(
                    text = stringResource(R.string.more_item_import_playlist),
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { playlistImportResultMessage = null }) {
                    Text(stringResource(R.string.common_close), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // dialog création titre texte (ancienne méthode)
    if (showCreateTextDialog && internalSelected != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateTextDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF222222)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.quickplaylists_new_prompter_title),
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = newTextTitle,
                            onValueChange = { newTextTitle = it },
                            label = { Text(stringResource(R.string.common_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newTextContent,
                            onValueChange = { newTextContent = it },
                            label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateTextDialog = false }) {
                            Text(stringResource(R.string.common_cancel), color = Color.White)
                        }
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
                    }
                }
            }
        }
    }

    if (showSavePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlaylistDialog = false },
            title = { Text(stringResource(R.string.all_playlists_create_title), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = savePlaylistName,
                    onValueChange = { savePlaylistName = it },
                    label = { Text(stringResource(R.string.all_playlists_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = savePlaylistName.trim()
                        if (clean.isEmpty()) return@TextButton
                        if (isExistingPlaylist(clean)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.quickplaylists_playlist_exists),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }
                        createPlaylistFromCurrent(clean)
                        showSavePlaylistDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSavePlaylistDialog = false }
                ) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    if (showResetPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showResetPlaylistDialog = false },
            title = {
                Text(
                    stringResource(R.string.quickplaylists_reset_confirm_title),
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.quickplaylists_reset_confirm_message),
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pl = internalSelected
                        showResetPlaylistDialog = false
                        if (pl.isNullOrBlank()) return@TextButton

                        val currentRaw = PlaylistRepository.getAllSongsRaw(pl)
                        val restored = originalOrderByPlaylist[pl]
                                ?.takeIf { it.isNotEmpty() }
                            ?: loadOriginalOrder(context, pl)
                            ?: loadManualOrder(context, pl, currentRaw)
                                ?.takeIf { it.isNotEmpty() }
                            ?: currentRaw

                        PlaylistRepository.resetPlayedFor(pl)
                        PlaylistRepository.updatePlayListOrder(pl, restored)
                        saveManualOrder(context, pl, restored)
                        originalOrderByPlaylist[pl] = restored
                        QuickPlaylistsUiCache.remove(pl)

                        songs.clear()
                        songs.addAll(PlaylistRepository.getSongsFor(pl))

                        onSelectedPlaylistChange(pl)
                        Toast.makeText(
                            context,
                            context.getString(R.string.quickplaylists_reset_done),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.common_reset), color = Color(0xFFFFB74D))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPlaylistDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.all_playlists_create_title), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = createPlaylistName,
                    onValueChange = { createPlaylistName = it },
                    label = { Text(stringResource(R.string.all_playlists_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = createPlaylistName.trim()
                        if (clean.isEmpty()) return@TextButton
                        if (isExistingPlaylist(clean)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.quickplaylists_playlist_exists),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@TextButton
                        }
                        createEmptyPlaylist(clean)
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_ok), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreatePlaylistDialog = false }
                ) {
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

    var depth = 0
    for (cursor in (headerIndex + 1) until items.size) {
        val item = items[cursor]
        when {
            isGroupHeader(item) -> depth++
            isGroupEnd(item) && depth > 0 -> depth--
            isGroupEnd(item) && getGroupUuid(item) == uuid -> return cursor
        }
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
    if (!isPlaylistGroupableItem(items[fromIndex])) return false

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
    val ordered = items.filter { it in targetSet && isPlaylistGroupableItem(it) }
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
    if (!isPlaylistGroupableItem(dragged)) return false

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
        .filter { (idx, key) -> key in targetSet && isPlaylistGroupableItem(key) && idx in items.indices }
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

private fun findGroupDragUpInsertIndex(items: List<String>, sourceRange: IntRange): Int? {
    if (sourceRange.isEmpty()) return null
    var cursor = sourceRange.first - 1
    while (cursor >= 0) {
        val candidate = items[cursor]
        when {
            isGroupEnd(candidate) -> {
                cursor--
            }
            isGroupHeader(candidate) -> {
                return cursor
            }
            else -> {
                val containingHeader = findContainingGroupHeaderIndex(items, cursor)
                return containingHeader ?: cursor
            }
        }
    }
    return null
}

private fun findGroupDragDownInsertIndex(items: List<String>, sourceRange: IntRange): Int? {
    if (sourceRange.isEmpty()) return null
    var cursor = sourceRange.last + 1
    while (cursor in items.indices) {
        val candidate = items[cursor]
        when {
            isGroupEnd(candidate) -> {
                cursor++
            }
            isGroupHeader(candidate) -> {
                val targetRange = findGroupBlockRange(items, cursor)
                return if (targetRange.isEmpty()) cursor + 1 else targetRange.last + 1
            }
            else -> {
                val containingHeader = findContainingGroupHeaderIndex(items, cursor)
                if (containingHeader != null) {
                    val targetRange = findGroupBlockRange(items, containingHeader)
                    return if (targetRange.isEmpty()) cursor + 1 else targetRange.last + 1
                }
                return cursor + 1
            }
        }
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

private enum class PlaylistMoveOptionKind {
    TOP,
    AFTER_ITEM,
    AFTER_GROUP,
    BOTTOM
}

private data class PlaylistMoveOption(
    val kind: PlaylistMoveOptionKind,
    val label: String,
    val targetKey: String? = null
)

private data class VisiblePlaylistRow(
    val realIndex: Int,
    val item: String
)

private fun buildVisiblePlaylistRows(
    songs: List<String>,
    collapsedGroupIds: Set<String>,
    normalizedQuery: String,
    searchableTitleByItem: Map<String, String>
): List<VisiblePlaylistRow> {
    val filtering = normalizedQuery.isNotBlank()

    fun matches(item: String): Boolean {
        if (!filtering) return true
        val searchText = searchableTitleByItem[item].orEmpty()
        return SearchEngine.normalize(searchText).contains(normalizedQuery)
    }

    return songs.mapIndexedNotNull { realIndex, item ->
        if (isGroupEnd(item)) {
            return@mapIndexedNotNull null
        }

        if (isGroupHeader(item)) {
            val groupRange = findGroupRange(songs, realIndex)
            val headerMatches = matches(item)
            val childMatches = if (groupRange.isEmpty()) {
                false
            } else {
                ((realIndex + 1)..groupRange.last).any { childIndex ->
                    val child = songs[childIndex]
                    !isGroupHeader(child) &&
                        !isGroupEnd(child) &&
                        matches(child)
                }
            }
            if (!filtering || headerMatches || childMatches) {
                VisiblePlaylistRow(realIndex = realIndex, item = item)
            } else {
                null
            }
        } else {
            if (!filtering && isItemHiddenByCollapsedGroup(songs, realIndex, collapsedGroupIds)) {
                return@mapIndexedNotNull null
            }
            if (matches(item)) {
                VisiblePlaylistRow(realIndex = realIndex, item = item)
            } else {
                null
            }
        }
    }
}

private fun buildQuickPlaylistSearchTitle(
    context: Context,
    item: String,
    smpTitleById: Map<String, String>
): String {
    getVariantFamilyId(item)?.let { familyId ->
        SongVariantFamiliesStore.load(context)
            .firstOrNull { it.id == familyId }
            ?.let { return it.title }
        getVariantFamilyTitle(item)?.let { return it }
    }

    if (isGroupHeader(item)) {
        return getGroupTitle(item)
    }

    if (item.startsWith("prompter://")) {
        val idPart = item.removePrefix("prompter://")
        val numericId = idPart.toLongOrNull()
        return if (numericId != null) {
            NotesRepository.get(context, numericId)?.title
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.quickplaylists_text_fallback_title)
        } else {
            TextSongRepository.get(context, idPart)?.title
                ?.takeIf { it.isNotBlank() }
                ?: quickPlaylistFallbackName(item)
        }
    }

    val smpSongId = getSmpSongId(item)
    if (smpSongId != null) {
        return cleanQuickPlaylistTitle(PlaylistRepository.getAnyCustomTitleForUri(item))
            ?: cleanQuickPlaylistTitle(smpTitleById[smpSongId])
            ?: cleanQuickPlaylistTitle(TitleAliasesStore.getTitleForTrack(context, buildSmpItem(smpSongId)))
            ?: context.getString(R.string.quickplaylists_missing_title)
    }

    return cleanQuickPlaylistTitle(TitleAliasesStore.getTitleForTrack(context, item))
        ?: cleanQuickPlaylistTitle(PlaylistRepository.getAnyCustomTitleForUri(item))
        ?: quickPlaylistFallbackName(item)
}

private fun cleanQuickPlaylistTitle(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun isKeyboardSelectablePlaylistItem(item: String): Boolean {
    return isPlaylistGroupableItem(item)
}

private fun isPlaylistGroupableItem(item: String): Boolean {
    return item.isNotBlank() && !isGroupHeader(item) && !isGroupEnd(item)
}

private fun activeSongIdForFamily(family: SongVariantFamily): String? {
    return family.activeSongId?.takeIf { it in family.songIds }
        ?: family.parentSongId?.takeIf { it in family.songIds }
        ?: family.songIds.firstOrNull()
}

private fun variantFamilyForPlaylistItem(
    item: String,
    familyById: Map<String, SongVariantFamily>
): SongVariantFamily? {
    val familyId = getVariantFamilyId(item) ?: return null
    familyById[familyId]?.let { return it }
    val title = getVariantFamilyTitle(item)?.takeIf { it.isNotBlank() } ?: return null
    val songIds = getVariantFamilySongIds(item)
    if (songIds.isEmpty()) return null
    return SongVariantFamily(
        id = familyId,
        title = title,
        songIds = songIds,
        parentSongId = songIds.firstOrNull(),
        activeSongId = songIds.firstOrNull()
    )
}

private fun resolveVariantFamilyPlaybackItem(
    item: String,
    familyById: Map<String, SongVariantFamily>
): String {
    val family = variantFamilyForPlaylistItem(item, familyById) ?: return item
    val activeSongId = activeSongIdForFamily(family) ?: return item
    return buildSmpItem(activeSongId)
}

private fun resolveVariantFamilyPlaybackQueue(
    items: List<String>,
    familyById: Map<String, SongVariantFamily>
): List<String> {
    return items.map { item -> resolveVariantFamilyPlaybackItem(item, familyById) }
}

internal data class PlaylistGroupDurationStats(
    val trackCount: Int,
    val knownDurationMs: Long,
    val hasUnknownDuration: Boolean
)

internal fun calculatePlaylistGroupDurationStats(
    items: List<String>,
    headerIndex: Int,
    familyById: Map<String, SongVariantFamily>,
    smpPlaybackUriById: Map<String, String>,
    durationCache: Map<String, Long>
): PlaylistGroupDurationStats {
    val groupRange = findGroupRange(items, headerIndex)
    if (groupRange.isEmpty()) {
        return PlaylistGroupDurationStats(
            trackCount = 0,
            knownDurationMs = 0L,
            hasUnknownDuration = false
        )
    }

    var trackCount = 0
    var knownDurationMs = 0L
    var hasUnknownDuration = false

    for (index in (groupRange.first + 1)..groupRange.last) {
        val child = items.getOrNull(index) ?: continue
        val playbackItem = resolveVariantFamilyPlaybackItem(child, familyById)
        if (!isPlayableAudioItem(playbackItem)) continue

        trackCount++
        val durationSource = resolvePlaylistDurationSource(
            item = child,
            familyById = familyById,
            smpPlaybackUriById = smpPlaybackUriById
        )
        val durationMs = durationSource?.let(durationCache::get)?.takeIf { it > 0L }
        if (durationMs != null) {
            knownDurationMs += durationMs
        } else {
            hasUnknownDuration = true
        }
    }

    return PlaylistGroupDurationStats(
        trackCount = trackCount,
        knownDurationMs = knownDurationMs,
        hasUnknownDuration = hasUnknownDuration
    )
}

private fun resolvePlaylistDurationSource(
    item: String,
    familyById: Map<String, SongVariantFamily>,
    smpPlaybackUriById: Map<String, String>
): String? {
    val playbackItem = resolveVariantFamilyPlaybackItem(item, familyById)
    if (!isPlayableAudioItem(playbackItem)) return null

    val songId = getSmpSongId(playbackItem)
    if (songId != null) {
        return smpPlaybackUriById[songId]
    }

    return playbackItem
}

private fun groupContainsSongId(
    items: List<String>,
    headerKey: String,
    songId: String
): Boolean {
    val headerIndex = items.indexOf(headerKey)
    if (headerIndex < 0) return false
    val groupRange = findGroupRange(items, headerIndex)
    if (groupRange.isEmpty()) return false
    return ((groupRange.first + 1)..groupRange.last).any { index ->
        val item = items.getOrNull(index) ?: return@any false
        !isGroupHeader(item) && !isGroupEnd(item) && getSmpSongId(item) == songId
    }
}

private fun buildPlaylistMoveOptions(
    context: Context,
    songs: List<String>,
    sourceUri: String,
    smpTitleById: Map<String, String>
): List<PlaylistMoveOption> {
    if (!songs.contains(sourceUri)) return emptyList()
    val options = mutableListOf(
        PlaylistMoveOption(
            kind = PlaylistMoveOptionKind.TOP,
            label = context.getString(R.string.quickplaylists_move_top)
        )
    )

    songs.forEach { item ->
        if (item == sourceUri || isGroupEnd(item)) return@forEach
        val label = if (isGroupHeader(item)) {
            getGroupTitle(item).lowercase()
        } else {
            buildQuickPlaylistSearchTitle(context, item, smpTitleById).lowercase()
        }
        options += PlaylistMoveOption(
            kind = if (isGroupHeader(item)) {
                PlaylistMoveOptionKind.AFTER_GROUP
            } else {
                PlaylistMoveOptionKind.AFTER_ITEM
            },
            label = label,
            targetKey = item
        )
    }

    options += PlaylistMoveOption(
        kind = PlaylistMoveOptionKind.BOTTOM,
        label = context.getString(R.string.quickplaylists_move_bottom)
    )
    return options
}

private fun movePlaylistItemToOption(
    items: MutableList<String>,
    trackUri: String,
    option: PlaylistMoveOption
): Boolean {
    val fromIndex = items.indexOf(trackUri)
    if (fromIndex == -1) return false
    val dragged = items[fromIndex]
    if (isGroupHeader(dragged) || isGroupEnd(dragged)) return false

    items.removeAt(fromIndex)
    val insertIndex = when (option.kind) {
        PlaylistMoveOptionKind.TOP -> 0
        PlaylistMoveOptionKind.BOTTOM -> items.size
        PlaylistMoveOptionKind.AFTER_ITEM -> {
            val targetIndex = option.targetKey?.let(items::indexOf) ?: -1
            if (targetIndex == -1) items.size else (targetIndex + 1).coerceIn(0, items.size)
        }
        PlaylistMoveOptionKind.AFTER_GROUP -> {
            val headerIndex = option.targetKey?.let(items::indexOf) ?: -1
            if (headerIndex == -1) {
                items.size
            } else {
                val endIndex = findMatchingGroupEndIndex(items, headerIndex)
                    ?: findGroupRange(items, headerIndex).last
                (endIndex + 1).coerceIn(0, items.size)
            }
        }
    }
    items.add(insertIndex, dragged)
    return true
}

private fun quickPlaylistFallbackName(item: String): String {
    val decoded = Uri.decode(item)
    val tail = decoded.substringAfterLast('/')
    val fromDocId = tail.substringAfterLast(':').substringAfterLast('/')
    return fromDocId.ifBlank {
        tail.ifBlank { decoded.ifBlank { item } }
    }
}

private fun playlistViewportStableKey(item: String): String {
    return "item|$item"
}

private fun playlistViewportKeyToItem(key: Any?): String? {
    val rawKey = key as? String ?: return null
    return when {
        rawKey.startsWith("item|") -> rawKey.removePrefix("item|")
        else -> rawKey.substringAfter(':', rawKey)
    }
}

internal fun findHeaderDropTargetKey(
    songs: List<String>,
    viewportItems: List<ListViewportItem>,
    dragY: Float,
    draggedItemKey: String?,
    headerPaddingPx: Float = 0f
): String? {
    val dragged = draggedItemKey ?: return null
    if (!isPlaylistGroupableItem(dragged)) return null

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

private data class QuickPlaylistGroupColorOption(
    val labelRes: Int,
    val argb: Long?
)

private fun quickPlaylistGroupColorOptions(): List<QuickPlaylistGroupColorOption> = listOf(
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_default, null),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_red, 0xFFD32F2F),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_blue, 0xFF0A6C97),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_green, 0xFF2E7D32),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_purple, 0xFF7B1FA2),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_orange, 0xFFE65100),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_yellow, 0xFFF9A825),
    QuickPlaylistGroupColorOption(R.string.quickplaylists_group_color_gray, 0xFF616161)
)

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
