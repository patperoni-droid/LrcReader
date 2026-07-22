package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import com.patrick.lrcreader.core.MidiOutput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.AppLanguagePrefs
import com.patrick.lrcreader.core.AppEdition
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.DisplayPrefs
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.LegacyLibraryVisibilityPrefs
import com.patrick.lrcreader.core.LightIndicatorPrefs
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.ManualCrossfadeDurationOption
import com.patrick.lrcreader.core.ManualCrossfadePrefs
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.PlayerLaunchMode
import com.patrick.lrcreader.core.PlayerLaunchPrefs
import com.patrick.lrcreader.core.TabletExperimentalModePrefs
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.UiEntryPrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.backup.BackupBundleImportedSong
import com.patrick.lrcreader.core.backup.BackupStateRemapResult
import com.patrick.lrcreader.core.backup.BackupStateRemapper
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.isPrompterItem
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.core.config.TitleAliasesStore
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpArchiveSongIdResolver
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpImporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpUserArchiveRebuilder
import com.patrick.lrcreader.smp.SmpUserArchiveCandidate
import com.patrick.lrcreader.smp.SmpWorkspaceArchiveStore
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens
import com.patrick.lrcreader.ui.locallink.LocalLinkReceiverScreen
import com.patrick.lrcreader.ui.locallink.LocalLinkTestSenderScreen
import com.patrick.lrcreader.ui.sync.SmpSyncDebugScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private const val RESTORE_DIAG_TAG = "RESTORE_DIAG"

/* ─────────────────────────────
   Écran "Plus" (Paramètres)
   ───────────────────────────── */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    currentWaveformSongId: String? = null,
    currentPlayingSongId: String? = null,
    currentPlayingTitle: String? = null,
    currentParsedLines: List<LrcLine> = emptyList(),
    getCurrentPositionMs: () -> Long = { 0L },
    getCurrentDurationMs: () -> Long? = { null },
    isCurrentTrackPlaying: () -> Boolean = { false },
    currentTrackGainDb: Int = 0,
    liveGainControlsEnabled: Boolean = false,
    onLiveGainDelta: (Int) -> Unit = {},
    seekCurrentTrackToMs: (Long) -> Unit = {},
    onPlaybackControlPlayPause: () -> Unit = {},
    playbackControlSelectionInSync: Boolean = true,
    loadCurrentParsedLines: suspend () -> List<LrcLine> = { currentParsedLines },
    requestedRoute: String? = null,
    requestedRouteToken: Int = 0,
    showDjTab: Boolean = false,
    showMainBusTab: Boolean = false,
    tabletExperimentalModeEnabled: Boolean = false,
    onShowDjTabChange: (Boolean) -> Unit = {},
    onShowMainBusTabChange: (Boolean) -> Unit = {},
    onTabletExperimentalModeChange: (Boolean) -> Unit = {},
    onAfterImport: (BackupManager.LastPlayed?) -> Unit = {},
    onAfterSmpRestore: (importedCount: Int, lastImportedSongId: String?) -> Unit = { _, _ -> },
    onAfterSyncImport: (importedSongIds: List<String>) -> Unit = {},
    onOpenTuner: () -> Unit = {},     // callback pour l'accordeur
    onOpenCanonicalArrangement: (() -> Unit)? = null,
    onOpenTempoFromArrangement: () -> Unit = {},
    onStopCurrentPlayback: () -> Unit = {}
) {
    fun resolveSection(route: String?): MoreSection {
        return MoreSection.entries.firstOrNull { it.route == route } ?: MoreSection.Root
    }

    var current by remember(requestedRoute) { mutableStateOf(resolveSection(requestedRoute)) }

    LaunchedEffect(requestedRouteToken) {
        val route = requestedRoute ?: return@LaunchedEffect
        current = resolveSection(route)
    }

    fun navigate(route: String) {
        current = resolveSection(route)
    }

    fun openArrangement() {
        onOpenCanonicalArrangement?.invoke() ?: navigate("arrangement")
    }

    when (current) {
        MoreSection.Root -> MoreRootScreen(
            modifier = modifier,
            onOpenBackup = { navigate("backup") },
            onOpenFiller = { navigate("filler") },
            onOpenHistory = { navigate("history") },
            onOpenArrangement = ::openArrangement,
            onOpenSecondScreen = { navigate("second_screen") },
            onOpenSmpSyncDebug = { navigate("smp_sync_debug") },
            onOpenWaveformPreview = { navigate("waveform_preview") },
            showDjTab = showDjTab,
            showMainBusTab = showMainBusTab,
            tabletExperimentalModeEnabled = tabletExperimentalModeEnabled,
            onShowDjTabChange = onShowDjTabChange,
            onShowMainBusTabChange = onShowMainBusTabChange,
            onTabletExperimentalModeChange = onTabletExperimentalModeChange,
            onOpenTuner = onOpenTuner,
            onAfterImport = onAfterImport,
            onAfterSmpRestore = onAfterSmpRestore
        )

        MoreSection.ArrangementHub -> ArrangementHubScreen(
            modifier = modifier,
            onOpenTempo = {
                Toast.makeText(
                    context,
                    context.getString(R.string.arrangement_hub_tempo_message),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onOpenCutting = ::openArrangement,
            onBack = { navigate("root") }
        )

        MoreSection.Backup -> BackupScreen(
            context = context,
            onAfterImport = onAfterImport,
            onBack = { navigate("root") }
        )

        MoreSection.Filler -> FillerSoundScreen(
            context = context,
            onBack = { navigate("root") },
            isMainPlaybackPlaying = isCurrentTrackPlaying(),
            currentMainTrackGainDb = currentTrackGainDb,
            liveGainControlsEnabled = liveGainControlsEnabled,
            onMainLiveGainDelta = onLiveGainDelta,
            getMainPositionMs = getCurrentPositionMs,
            getMainDurationMs = { getCurrentDurationMs() ?: 0L },
            seekMainToMs = seekCurrentTrackToMs,
            onMainPlaybackPlayPause = onPlaybackControlPlayPause,
            mainPlaybackSelectionInSync = playbackControlSelectionInSync
        )

        MoreSection.History -> HistoryScreen(
            modifier = modifier,
            context = context,
            onBack = { navigate("root") }
        )

        MoreSection.SecondScreen -> SecondScreenHub(
            modifier = modifier,
            onOpenShare = { navigate("local_link_sender") },
            onOpenDisplay = { navigate("local_link_receiver") },
            onBack = { navigate("root") }
        )

        MoreSection.LocalLinkSender -> LocalLinkTestSenderScreen(
            modifier = modifier,
            currentSongId = currentPlayingSongId,
            currentSongTitle = currentPlayingTitle,
            currentParsedLines = currentParsedLines,
            getCurrentPositionMs = getCurrentPositionMs,
            getCurrentDurationMs = getCurrentDurationMs,
            isCurrentTrackPlaying = isCurrentTrackPlaying,
            loadCurrentParsedLines = loadCurrentParsedLines,
            onBack = { navigate("second_screen") }
        )

        MoreSection.LocalLinkReceiver -> LocalLinkReceiverScreen(
            modifier = modifier,
            onBack = { navigate("second_screen") }
        )

        MoreSection.SmpSyncDebug -> SmpSyncDebugScreen(
            modifier = modifier,
            onBack = { navigate("root") },
            onAfterImport = onAfterSyncImport
        )

        MoreSection.WaveformPreview -> WaveformPreviewScreen(
            modifier = modifier,
            onBack = { navigate("root") },
            initialSongId = currentWaveformSongId,
            isOfficialPlaybackPlaying = isCurrentTrackPlaying(),
            currentTrackGainDb = currentTrackGainDb,
            liveGainControlsEnabled = liveGainControlsEnabled,
            onLiveGainDelta = onLiveGainDelta,
            getOfficialPositionMs = getCurrentPositionMs,
            getOfficialDurationMs = { getCurrentDurationMs() ?: 0L },
            seekOfficialToMs = seekCurrentTrackToMs,
            onOfficialPlaybackPlayPause = onPlaybackControlPlayPause,
            officialPlaybackSelectionInSync = playbackControlSelectionInSync
        )

        MoreSection.Arrangement -> ArrangementEditorSection(
            currentSongId = currentPlayingSongId,
            currentPositionMs = 0L,
            onClose = { navigate("root") },
            onStopCurrentPlayback = onStopCurrentPlayback
        )

        MoreSection.ArrangementFromTempo -> ArrangementEditorSection(
            currentSongId = currentPlayingSongId,
            currentPositionMs = 0L,
            onClose = { onOpenTempoFromArrangement() },
            onStopCurrentPlayback = onStopCurrentPlayback,
            showSongPicker = false,
            onBackToTempo = onOpenTempoFromArrangement,
            showCloseButton = false
        )
    }
}

private enum class MoreSection(val route: String) {
    Root("root"),
    ArrangementHub("arrangement_hub"),
    Backup("backup"),
    Filler("filler"),
    History("history"),
    SecondScreen("second_screen"),
    LocalLinkSender("local_link_sender"),
    LocalLinkReceiver("local_link_receiver"),
    SmpSyncDebug("smp_sync_debug"),
    WaveformPreview("waveform_preview"),
    Arrangement("arrangement"),
    ArrangementFromTempo("arrangement_from_tempo")
}

private object MoreLiveSongsExportPrefs {
    private const val PREFS_NAME = "more_live_songs_export_prefs"
    private const val KEY_TREE_URI = "tree_uri"

    fun getTreeUri(context: Context): android.net.Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null)
            .orEmpty()
            .trim()
        return raw.takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse)
    }

    fun setTreeUri(context: Context, uri: android.net.Uri?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, uri?.toString())
            .apply()
    }
}

@Composable
private fun ArrangementHubScreen(
    modifier: Modifier = Modifier,
    onOpenTempo: () -> Unit,
    onOpenCutting: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.common_back_arrow),
                color = Color(0xFFB0BEC5)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.arrangement_title),
                    color = Color.White,
                    fontSize = 22.sp
                )

                TextButton(onClick = onOpenTempo) {
                    Text(
                        text = stringResource(R.string.arrangement_hub_tempo_action),
                        color = Color(0xFF80CBC4),
                        fontSize = 18.sp
                    )
                }

                TextButton(onClick = onOpenCutting) {
                    Text(
                        text = stringResource(R.string.arrangement_hub_cutting_action),
                        color = Color(0xFFFFF3E0),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondScreenHub(
    modifier: Modifier = Modifier,
    onOpenShare: () -> Unit,
    onOpenDisplay: () -> Unit,
    onBack: () -> Unit
) {
    var showAdvancedOptions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.common_back_arrow),
                color = Color(0xFFB0BEC5)
            )
        }

        Text(
            text = stringResource(R.string.second_screen_title),
            color = Color.White,
            fontSize = 24.sp
        )
        Text(
            text = stringResource(R.string.second_screen_prompt),
            color = Color(0xFFE0E0E0),
            fontSize = 17.sp
        )

        SecondScreenChoiceCard(
            title = stringResource(R.string.second_screen_share_title),
            subtitle = stringResource(R.string.second_screen_share_subtitle),
            onClick = onOpenShare
        )

        SecondScreenChoiceCard(
            title = stringResource(R.string.second_screen_display_title),
            subtitle = stringResource(R.string.second_screen_display_subtitle),
            onClick = onOpenDisplay
        )

        TextButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
            Text(
                text = stringResource(R.string.second_screen_advanced_title),
                color = Color(0xFFB0BEC5)
            )
        }

        if (showAdvancedOptions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.second_screen_advanced_subtitle),
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun SecondScreenChoiceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171717)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFFBDBDBD),
                fontSize = 15.sp
            )
        }
    }
}

/* ─────────────────────────────
   Menu principal – style rack analogique
   ───────────────────────────── */

@Composable
private fun MoreRootScreen(
    modifier: Modifier = Modifier,
    onOpenBackup: () -> Unit,
    onOpenFiller: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenArrangement: () -> Unit,
    onOpenSecondScreen: () -> Unit,
    onOpenSmpSyncDebug: () -> Unit,
    onOpenWaveformPreview: () -> Unit,
    showDjTab: Boolean,
    showMainBusTab: Boolean,
    tabletExperimentalModeEnabled: Boolean,
    onShowDjTabChange: (Boolean) -> Unit,
    onShowMainBusTabChange: (Boolean) -> Unit,
    onTabletExperimentalModeChange: (Boolean) -> Unit,
    onOpenTuner: () -> Unit,
    onAfterImport: (BackupManager.LastPlayed?) -> Unit,
    onAfterSmpRestore: (importedCount: Int, lastImportedSongId: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val adaptiveTokens = rememberSmpAdaptiveTokens()
    var exportLiveSongsTreeUri by remember {
        mutableStateOf(MoreLiveSongsExportPrefs.getTreeUri(context))
    }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPlayerLaunchDialog by remember { mutableStateOf(false) }
    var showManualCrossfadeDurationDialog by remember { mutableStateOf(false) }
    var showGuidedReadingColorsDialog by remember { mutableStateOf(false) }
    var showLyricsTextSizeDialog by remember { mutableStateOf(false) }
    var showExportProDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(AppLanguagePrefs.getSavedLanguageTag(context)) }
    var isExportingLiveSongs by remember { mutableStateOf(false) }
    var exportLiveSongsDone by remember { mutableStateOf(0) }
    var exportLiveSongsTotal by remember { mutableStateOf(0) }
    var exportLiveSongsCurrentTitle by remember { mutableStateOf<String?>(null) }
    var exportLiveSongsResultMessage by remember { mutableStateOf<String?>(null) }
    var isRestoringLibrary by remember { mutableStateOf(false) }
    var restoreLibraryDone by remember { mutableStateOf(0) }
    var restoreLibraryTotal by remember { mutableStateOf(0) }
    var restoreLibraryCurrentTitle by remember { mutableStateOf<String?>(null) }
    var restoreLibraryStageText by remember { mutableStateOf<String?>(null) }
    var restoreLibraryResultMessage by remember { mutableStateOf<String?>(null) }
    var playlistImportResultMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreScan by remember { mutableStateOf<LibraryRestoreScanResult?>(null) }
    // ✅ Séquence de Program Change pour le test MIDI
    val testProgramChanges = listOf(8, 39, 58, 127)
    var testPcIndex by remember { mutableStateOf(0) }


    // État du switch "retour auto"
    var autoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
    }
    var playerLaunchMode by remember {
        mutableStateOf(PlayerLaunchPrefs.getMode(context))
    }
    var manualCrossfadeDurationOption by remember {
        mutableStateOf(ManualCrossfadePrefs.getDurationOption(context))
    }
    var showLightIndicator by remember {
        mutableStateOf(LightIndicatorPrefs.isEnabled(context))
    }
    var guidedReadingColorsEnabled by remember {
        mutableStateOf(DisplayPrefs.isGuidedReadingColorsEnabled(context))
    }
    var guidedReadingColorA by remember {
        mutableIntStateOf(DisplayPrefs.getGuidedReadingColorA(context))
    }
    var guidedReadingColorB by remember {
        mutableIntStateOf(DisplayPrefs.getGuidedReadingColorB(context))
    }
    var lyricsTextSize by remember {
        mutableStateOf(DisplayPrefs.getLyricsTextSize(context))
    }
    var showOldWorldInLibrary by remember {
        mutableStateOf(LegacyLibraryVisibilityPrefs.isOldWorldVisible(context))
    }
    var showDjMode by remember { mutableStateOf(showDjTab) }
    var showMainBusMode by remember { mutableStateOf(showMainBusTab) }
    var tabletExperimentalMode by remember { mutableStateOf(tabletExperimentalModeEnabled) }
    var betaCode by remember { mutableStateOf("") }
    var settingsHelpTitle by remember { mutableStateOf<String?>(null) }
    var settingsHelpText by remember { mutableStateOf<String?>(null) }

    fun openSettingsHelp(title: String, text: String) {
        settingsHelpTitle = title
        settingsHelpText = text
    }

    androidx.compose.runtime.LaunchedEffect(showDjTab) {
        showDjMode = showDjTab
    }

    androidx.compose.runtime.LaunchedEffect(showMainBusTab) {
        showMainBusMode = showMainBusTab
    }

    androidx.compose.runtime.LaunchedEffect(tabletExperimentalModeEnabled) {
        tabletExperimentalMode = tabletExperimentalModeEnabled
    }

    val currentLanguageLabel = when (selectedLanguageTag) {
        "fr" -> stringResource(R.string.settings_language_fr)
        "en" -> stringResource(R.string.settings_language_en)
        "es" -> stringResource(R.string.settings_language_es)
        else -> stringResource(R.string.settings_language_auto)
    }
    val currentPlayerLaunchModeLabel = when (playerLaunchMode) {
        PlayerLaunchMode.ALWAYS -> stringResource(R.string.player_open_mode_always)
        PlayerLaunchMode.NEVER -> stringResource(R.string.player_open_mode_never)
        PlayerLaunchMode.AUTOMATIC -> stringResource(R.string.player_open_mode_automatic)
    }
    val currentManualCrossfadeDurationLabel = when (manualCrossfadeDurationOption) {
        ManualCrossfadeDurationOption.SECONDS_2 -> stringResource(R.string.player_crossfade_duration_2s)
        ManualCrossfadeDurationOption.SECONDS_3 -> stringResource(R.string.player_crossfade_duration_3s)
        ManualCrossfadeDurationOption.SECONDS_5 -> stringResource(R.string.player_crossfade_duration_5s)
        ManualCrossfadeDurationOption.SECONDS_8 -> stringResource(R.string.player_crossfade_duration_8s)
        ManualCrossfadeDurationOption.SECONDS_10 -> stringResource(R.string.player_crossfade_duration_10s)
        ManualCrossfadeDurationOption.SECONDS_20 -> stringResource(R.string.player_crossfade_duration_20s)
    }
    val guidedReadingColorsSubtitle = stringResource(
        if (guidedReadingColorsEnabled) {
            R.string.settings_guided_reading_colors_enabled
        } else {
            R.string.settings_guided_reading_colors_disabled
        }
    )
    val lyricsTextSizeLabel = when (lyricsTextSize) {
        DisplayPrefs.LyricsTextSize.SMALL -> stringResource(R.string.settings_lyrics_text_size_small)
        DisplayPrefs.LyricsTextSize.NORMAL -> stringResource(R.string.settings_lyrics_text_size_normal)
        DisplayPrefs.LyricsTextSize.LARGE -> stringResource(R.string.settings_lyrics_text_size_large)
        DisplayPrefs.LyricsTextSize.EXTRA_LARGE -> stringResource(R.string.settings_lyrics_text_size_extra_large)
    }
    val sLiveSongsExportFailed = stringResource(R.string.more_live_songs_export_failed)
    val sLibraryRestoreScanFailed = stringResource(R.string.more_library_restore_scan_failed)
    val sLibraryRestoreEmpty = stringResource(R.string.more_library_restore_empty)
    val sLibraryRestorePreparing = stringResource(R.string.more_library_restore_preparing)
    val sLibraryRestoreAnalyzing = stringResource(R.string.more_library_restore_analyzing)
    val sLibraryRestoreImporting = stringResource(R.string.more_library_restore_importing)
    val sLibraryRestoreRebuilding = stringResource(R.string.more_library_restore_rebuilding)
    val sLibraryRestorePlaylists = stringResource(R.string.more_library_restore_playlists)
    val sExportProDialogTitle = stringResource(R.string.export_pro_dialog_title)
    val sExportProDialogMessage = stringResource(R.string.export_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    val isLite = EditionConfig.isLite
    val showSmpSyncDebug = BuildConfig.DEBUG && BuildConfig.FLAVOR == "labo"
    val currentEdition = EditionConfig.current
    val currentEditionLabel = when (currentEdition) {
        AppEdition.LITE -> stringResource(R.string.more_beta_mode_freemium)
        AppEdition.PRO -> stringResource(R.string.more_beta_mode_pro)
    }
    val workspaceSnapshot = remember(context) { WorkspaceResolver.resolve(context) }
    val workingFolderPath = remember(workspaceSnapshot) {
        workspaceSnapshot.workspaceRootUri?.let { uri ->
            when (uri.scheme?.lowercase()) {
                "file" -> uri.path.orEmpty()
                else -> uri.toString()
            }
        }?.takeIf { it.isNotBlank() } ?: "—"
    }

    fun applyLanguageSelection(languageTag: String?) {
        AppLanguagePrefs.setSavedLanguageTag(context, languageTag)
        val locales = if (languageTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        selectedLanguageTag = languageTag
        showLanguageDialog = false
    }

    val openUpgradeToPro: () -> Unit = remember(context) {
        {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=Stage Music Player Pro")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=Stage%20Music%20Player%20Pro&c=apps")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(webIntent)
            }
        }
    }

    val exportLiveSongsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                pickedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        exportLiveSongsTreeUri = pickedUri
        MoreLiveSongsExportPrefs.setTreeUri(context, pickedUri)
        scope.launch {
            isExportingLiveSongs = true
            exportLiveSongsDone = 0
            exportLiveSongsTotal = 0
            exportLiveSongsCurrentTitle = null

            val exportTarget = withContext(Dispatchers.IO) {
                buildLiveSongsExportTarget(context.applicationContext, pickedUri)
            }
            if (exportTarget == null) {
                exportLiveSongsResultMessage = sLiveSongsExportFailed
                isExportingLiveSongs = false
                return@launch
            }

            val allRuntimeSongs = withContext(Dispatchers.IO) {
                SmpLibraryScanner(context.applicationContext).listSongs()
            }
            val runtimeSongs = allRuntimeSongs.filter { it.arrangementSourceSongId == null }
            val runtimeParentIds = runtimeSongs.mapTo(linkedSetOf()) { it.id }
            val orphanArrangementVariants = allRuntimeSongs.filter { song ->
                val sourceSongId = song.arrangementSourceSongId
                sourceSongId != null && sourceSongId !in runtimeParentIds
            }
            val archiveCandidates = withContext(Dispatchers.IO) {
                SmpUserArchiveRebuilder(context.applicationContext).listUserArchiveCandidates()
            }
            val runtimeSongIds = allRuntimeSongs.map { it.id.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val workspaceArchivesToCopy = selectWorkspaceArchivesForBackup(
                runtimeSongIds = runtimeSongIds,
                candidates = archiveCandidates
            )
            exportLiveSongsTotal = runtimeSongs.size +
                orphanArrangementVariants.size +
                workspaceArchivesToCopy.size +
                2

            var successCount = 0
            var failureCount = 0
            var completedCount = 0

            runtimeSongs.forEach { song ->
                exportLiveSongsCurrentTitle = song.title
                val exported = withContext(Dispatchers.IO) {
                    exportLiveSongToTree(
                        context = context.applicationContext,
                        exportDir = exportTarget,
                        songId = song.id
                    )
                }
                if (exported) {
                    successCount += 1
                } else {
                    failureCount += 1
                }
                completedCount += 1
                exportLiveSongsDone = completedCount
            }

            orphanArrangementVariants.forEach { variant ->
                exportLiveSongsCurrentTitle = variant.title
                failureCount += 1
                completedCount += 1
                exportLiveSongsDone = completedCount
                Log.e(
                    "SMP_EXPORT",
                    "Variante Arrangement orpheline non sauvegardée: variantId=${variant.id} sourceSongId=${variant.arrangementSourceSongId}"
                )
            }

            workspaceArchivesToCopy.forEach { archive ->
                exportLiveSongsCurrentTitle = archive.displayName
                val copied = withContext(Dispatchers.IO) {
                    copyWorkspaceArchiveToTree(
                        context = context.applicationContext,
                        exportDir = exportTarget,
                        archive = archive
                    )
                }
                if (copied) {
                    successCount += 1
                } else {
                    failureCount += 1
                }
                completedCount += 1
                exportLiveSongsDone = completedCount
            }

            exportLiveSongsCurrentTitle = "state.json"
            val stateWritten = withContext(Dispatchers.IO) {
                writeLibraryBackupStateToTree(
                    context = context.applicationContext,
                    exportDir = exportTarget
                )
            }
            if (stateWritten) {
                successCount += 1
            } else {
                failureCount += 1
            }
            completedCount += 1
            exportLiveSongsDone = completedCount

            exportLiveSongsCurrentTitle = "prompters.json"
            val promptersWritten = withContext(Dispatchers.IO) {
                writeLibraryBackupPromptersToTree(
                    context = context.applicationContext,
                    exportDir = exportTarget
                )
            }
            if (promptersWritten) {
                successCount += 1
            } else {
                failureCount += 1
            }
            completedCount += 1
            exportLiveSongsDone = completedCount

            exportLiveSongsCurrentTitle = null
            exportLiveSongsResultMessage = context.getString(
                R.string.more_live_songs_export_result,
                successCount,
                failureCount,
                exportTarget.name ?: pickedUri.toString()
            )
            isExportingLiveSongs = false
        }
    }

    val restoreLibraryFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri ->
        if (pickedUri == null) {
            restoreLibraryStageText = null
            return@rememberLauncherForActivityResult
        }
        restoreLibraryStageText = sLibraryRestoreAnalyzing
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                pickedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        exportLiveSongsTreeUri = pickedUri
        MoreLiveSongsExportPrefs.setTreeUri(context, pickedUri)
        scope.launch {
            val scanResult = withContext(Dispatchers.IO) {
                scanLibraryRestoreFolder(
                    context = context.applicationContext,
                    treeUri = pickedUri
                )
            }
            pendingRestoreScan = when {
                scanResult == null -> {
                    restoreLibraryResultMessage = sLibraryRestoreScanFailed
                    restoreLibraryStageText = null
                    null
                }

                scanResult.songCount == 0 &&
                    scanResult.prompterCount == 0 &&
                    scanResult.stateJson == null -> {
                    restoreLibraryResultMessage = sLibraryRestoreEmpty
                    restoreLibraryStageText = null
                    null
                }

                else -> {
                    restoreLibraryStageText = null
                    scanResult
                }
            }
        }
    }

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(pickedUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                    importPlaylistFile(
                        context = context.applicationContext,
                        rawJson = raw
                    )
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

    fun startLibraryRestore(
        scan: LibraryRestoreScanResult,
        conflictMode: LibraryRestoreConflictMode
    ) {
        pendingRestoreScan = null
        scope.launch {
            isRestoringLibrary = true
            restoreLibraryStageText = sLibraryRestoreImporting
            restoreLibraryDone = 0
            restoreLibraryCurrentTitle = null
            restoreLibraryTotal = scan.songCount + scan.prompterCount + if (scan.stateJson != null) 1 else 0
            try {
                val result = withContext(Dispatchers.IO) {
                    restoreLibraryFromBackupFolder(
                        context = context.applicationContext,
                        scanResult = scan,
                        conflictMode = conflictMode
                    ) { done, total, currentTitle ->
                        restoreLibraryDone = done
                        restoreLibraryTotal = total
                        restoreLibraryCurrentTitle = currentTitle
                        restoreLibraryStageText = when {
                            currentTitle == "state.json" -> sLibraryRestorePlaylists
                            total > 0 && done >= scan.songCount -> sLibraryRestoreRebuilding
                            else -> sLibraryRestoreImporting
                        }
                    }
                }
                onAfterImport(result.lastPlayed)
                if (result.importedCount > 0) {
                    onAfterSmpRestore(result.importedCount, result.lastImportedSongId)
                }
                restoreLibraryResultMessage = formatLibraryRestoreResultMessage(
                    context = context,
                    result = result
                )
            } catch (_: Exception) {
                restoreLibraryResultMessage = sLibraryRestoreScanFailed
            } finally {
                restoreLibraryCurrentTitle = null
                restoreLibraryStageText = null
                isRestoringLibrary = false
            }
        }
    }

    // Même type de fond que la console / accordeur
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Carte principale "rack de paramètres"
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1B1B)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    // Bandeau titre type BUS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3A2C24),
                                        Color(0xFF4B372A),
                                        Color(0xFF3A2C24)
                                    )
                                ),
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = 0.dp,
                                    bottomEnd = 0.dp
                                )
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title).uppercase(),
                            color = Color(0xFFFFECB3),
                            fontSize = 15.sp,
                            letterSpacing = 2.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Bloc fonctionnel
                    SettingsHeader(stringResource(R.string.more_section_functions))
                    SettingsItem(stringResource(R.string.more_item_filler), onClick = onOpenFiller)
                    SettingsItem(stringResource(R.string.more_item_history), onClick = onOpenHistory)
                    SettingsItem(
                        label = stringResource(R.string.more_item_arrangement),
                        subtitle = null,
                        onClick = onOpenArrangement
                    )
                    val exportLiveSongsLabel = stringResource(R.string.more_item_export_live_songs)
                    SettingsItem(
                        label = exportLiveSongsLabel,
                        helpText = stringResource(R.string.more_item_export_live_songs_subtitle),
                        onHelpClick = { help -> openSettingsHelp(exportLiveSongsLabel, help) },
                        onClick = {
                            if (isExportingLiveSongs) return@SettingsItem
                            if (isLite) {
                                showExportProDialog = true
                                return@SettingsItem
                            }
                            exportLiveSongsFolderLauncher.launch(exportLiveSongsTreeUri)
                        }
                    )
                    val restoreLibraryLabel = stringResource(R.string.more_item_restore_library)
                    SettingsItem(
                        label = restoreLibraryLabel,
                        helpText = stringResource(R.string.more_item_restore_library_subtitle),
                        onHelpClick = { help -> openSettingsHelp(restoreLibraryLabel, help) },
                        onClick = {
                            if (isRestoringLibrary || isExportingLiveSongs) return@SettingsItem
                            restoreLibraryStageText = sLibraryRestorePreparing
                            restoreLibraryFolderLauncher.launch(exportLiveSongsTreeUri)
                        }
                    )
                    SettingsItem(
                        label = stringResource(R.string.more_item_import_playlist),
                        onClick = {
                            importPlaylistLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                    )
                    SettingsItem(
                        label = stringResource(R.string.more_item_second_screen),
                        subtitle = stringResource(R.string.more_item_second_screen_subtitle),
                        onClick = onOpenSecondScreen
                    )
                    if (showSmpSyncDebug) {
                        SettingsItem(
                            label = stringResource(R.string.more_item_smp_sync_debug),
                            subtitle = stringResource(R.string.more_item_smp_sync_debug_subtitle),
                            onClick = onOpenSmpSyncDebug
                        )
                    }

                    HorizontalDivider(color = Color(0xFF262626))

                    // Bloc interface / audio
                    SettingsItem(
                        label = stringResource(R.string.more_item_language),
                        value = stringResource(
                            R.string.settings_language_subtitle,
                            currentLanguageLabel
                        ),
                        onClick = { showLanguageDialog = true }
                    )

                    // Accordeur, dans le bloc Audio
                    SettingsItem(stringResource(R.string.more_item_tuner), onClick = onOpenTuner)

                    SettingsHeader(stringResource(R.string.settings_lyrics_section))
                    val lyricsTextSizeSettingLabel = stringResource(R.string.settings_lyrics_text_size)
                    SettingsItem(
                        label = lyricsTextSizeSettingLabel,
                        value = lyricsTextSizeLabel,
                        helpText = stringResource(R.string.settings_lyrics_text_size_hint),
                        onHelpClick = { help -> openSettingsHelp(lyricsTextSizeSettingLabel, help) },
                        onClick = { showLyricsTextSizeDialog = true }
                    )

                    val guidedReadingColorsLabel = stringResource(R.string.settings_guided_reading_colors)
                    SettingsItem(
                        label = guidedReadingColorsLabel,
                        value = guidedReadingColorsSubtitle,
                        helpText = stringResource(R.string.settings_guided_reading_colors_hint),
                        onHelpClick = { help -> openSettingsHelp(guidedReadingColorsLabel, help) },
                        onClick = { showGuidedReadingColorsDialog = true }
                    )

                    val showDjLabel = stringResource(R.string.more_show_dj_mode)
                    SwitchSettingItem(
                        label = showDjLabel,
                        checked = showDjMode,
                        helpText = stringResource(R.string.more_show_dj_mode_help),
                        onHelpClick = { help -> openSettingsHelp(showDjLabel, help) },
                        onCheckedChange = { enabled ->
                            showDjMode = enabled
                            UiEntryPrefs.setShowDjTab(context, enabled)
                            onShowDjTabChange(enabled)
                        }
                    )

                    val showMainBusLabel = stringResource(R.string.more_show_main_bus)
                    SwitchSettingItem(
                        label = showMainBusLabel,
                        checked = showMainBusMode,
                        helpText = stringResource(R.string.more_show_main_bus_help),
                        onHelpClick = { help -> openSettingsHelp(showMainBusLabel, help) },
                        onCheckedChange = { enabled ->
                            showMainBusMode = enabled
                            UiEntryPrefs.setShowMainBusTab(context, enabled)
                            onShowMainBusTabChange(enabled)
                        }
                    )

                    if (adaptiveTokens.tabletMode) {
                        SwitchSettingItem(
                            label = stringResource(R.string.more_tablet_experimental_mode),
                            checked = tabletExperimentalMode,
                            onCheckedChange = { enabled ->
                                tabletExperimentalMode = enabled
                                TabletExperimentalModePrefs.setEnabled(context, enabled)
                                onTabletExperimentalModeChange(enabled)
                            }
                        )
                    }

                    // 🔁 Retour auto vers la playlist (ON/OFF)
                    val autoReturnLabel = stringResource(R.string.more_auto_return_playlist)
                    SwitchSettingItem(
                        label = autoReturnLabel,
                        checked = autoReturnEnabled,
                        helpText = stringResource(R.string.more_auto_return_playlist_help),
                        onHelpClick = { help -> openSettingsHelp(autoReturnLabel, help) },
                        onCheckedChange = { enabled ->
                            autoReturnEnabled = enabled
                            AutoReturnPrefs.setEnabled(context, enabled)
                        }
                    )

                    val playerOpenModeLabel = stringResource(R.string.more_player_open_mode)
                    SettingsItem(
                        label = playerOpenModeLabel,
                        value = currentPlayerLaunchModeLabel,
                        helpText = stringResource(R.string.more_player_open_mode_help),
                        onHelpClick = { help -> openSettingsHelp(playerOpenModeLabel, help) },
                        onClick = { showPlayerLaunchDialog = true }
                    )

                    val manualCrossfadeLabel = stringResource(R.string.more_manual_crossfade_duration)
                    SettingsItem(
                        label = manualCrossfadeLabel,
                        value = currentManualCrossfadeDurationLabel,
                        helpText = stringResource(R.string.more_manual_crossfade_duration_help),
                        onHelpClick = { help -> openSettingsHelp(manualCrossfadeLabel, help) },
                        onClick = { showManualCrossfadeDurationDialog = true }
                    )

                    val lightIndicatorLabel = stringResource(R.string.more_show_light_indicator)
                    SwitchSettingItem(
                        label = lightIndicatorLabel,
                        checked = showLightIndicator,
                        helpText = stringResource(R.string.more_show_light_indicator_help),
                        onHelpClick = { help -> openSettingsHelp(lightIndicatorLabel, help) },
                        onCheckedChange = { enabled ->
                            showLightIndicator = enabled
                            LightIndicatorPrefs.setEnabled(context, enabled)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF262626))

                    SettingsHeader(stringResource(R.string.more_beta_mode_section))
                    SettingsInfoItem(
                        label = stringResource(R.string.more_beta_mode_current),
                        value = currentEditionLabel
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = betaCode,
                            onValueChange = { betaCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(text = stringResource(R.string.more_beta_mode_code_label)) },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val enabled = EditionConfig.tryEnablePro(context, betaCode)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            if (enabled) {
                                                R.string.more_beta_mode_pro_enabled
                                            } else {
                                                R.string.more_beta_mode_invalid_code
                                            }
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (enabled) betaCode = ""
                                }
                            ) {
                                Text(text = stringResource(R.string.more_beta_mode_activate))
                            }
                            TextButton(
                                onClick = {
                                    EditionConfig.revertToLite(context)
                                    betaCode = ""
                                }
                            ) {
                                Text(text = stringResource(R.string.more_beta_mode_back_to_freemium))
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF262626))

                    SettingsItem(stringResource(R.string.more_item_test_bluetooth_midi), onClick = {
                        MidiOutput.init(context)

                        val pc = testProgramChanges[testPcIndex]
                        MidiOutput.sendProgramChange(channel = 1, program = pc)

                        testPcIndex = (testPcIndex + 1) % testProgramChanges.size
                    })
                    SettingsInfoItem(
                        label = stringResource(R.string.more_working_folder),
                        value = workingFolderPath
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.more_footer_signature),
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        val languageOptions = listOf(
            null to R.string.settings_language_auto,
            "fr" to R.string.settings_language_fr,
            "en" to R.string.settings_language_en,
            "es" to R.string.settings_language_es
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(text = stringResource(R.string.settings_language)) },
            text = {
                Column {
                    languageOptions.forEach { (tag, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyLanguageSelection(tag) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguageTag == tag,
                                onClick = { applyLanguageSelection(tag) }
                            )
                            Text(
                                text = stringResource(labelRes),
                                color = Color(0xFFF5F5F5),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (settingsHelpTitle != null && settingsHelpText != null) {
        SettingHelpDialog(
            title = settingsHelpTitle.orEmpty(),
            text = settingsHelpText.orEmpty(),
            onDismiss = {
                settingsHelpTitle = null
                settingsHelpText = null
            }
        )
    }

    if (showPlayerLaunchDialog) {
        val playerLaunchOptions = listOf(
            PlayerLaunchMode.ALWAYS to R.string.player_open_mode_always,
            PlayerLaunchMode.NEVER to R.string.player_open_mode_never,
            PlayerLaunchMode.AUTOMATIC to R.string.player_open_mode_automatic
        )

        AlertDialog(
            onDismissRequest = { showPlayerLaunchDialog = false },
            title = { Text(text = stringResource(R.string.more_player_open_mode)) },
            text = {
                Column {
                    playerLaunchOptions.forEach { (mode, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerLaunchMode = mode
                                    PlayerLaunchPrefs.setMode(context, mode)
                                    showPlayerLaunchDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playerLaunchMode == mode,
                                onClick = {
                                    playerLaunchMode = mode
                                    PlayerLaunchPrefs.setMode(context, mode)
                                    showPlayerLaunchDialog = false
                                }
                            )
                            Text(
                                text = stringResource(labelRes),
                                color = Color(0xFFF5F5F5),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlayerLaunchDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showManualCrossfadeDurationDialog) {
        val crossfadeDurationOptions = listOf(
            ManualCrossfadeDurationOption.SECONDS_2 to R.string.player_crossfade_duration_2s,
            ManualCrossfadeDurationOption.SECONDS_3 to R.string.player_crossfade_duration_3s,
            ManualCrossfadeDurationOption.SECONDS_5 to R.string.player_crossfade_duration_5s,
            ManualCrossfadeDurationOption.SECONDS_8 to R.string.player_crossfade_duration_8s,
            ManualCrossfadeDurationOption.SECONDS_10 to R.string.player_crossfade_duration_10s,
            ManualCrossfadeDurationOption.SECONDS_20 to R.string.player_crossfade_duration_20s
        )

        AlertDialog(
            onDismissRequest = { showManualCrossfadeDurationDialog = false },
            title = { Text(text = stringResource(R.string.more_manual_crossfade_duration)) },
            text = {
                Column {
                    crossfadeDurationOptions.forEach { (option, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    manualCrossfadeDurationOption = option
                                    ManualCrossfadePrefs.setDurationOption(context, option)
                                    showManualCrossfadeDurationDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = manualCrossfadeDurationOption == option,
                                onClick = {
                                    manualCrossfadeDurationOption = option
                                    ManualCrossfadePrefs.setDurationOption(context, option)
                                    showManualCrossfadeDurationDialog = false
                                }
                            )
                            Text(
                                text = stringResource(labelRes),
                                color = Color(0xFFF5F5F5),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showManualCrossfadeDurationDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showLyricsTextSizeDialog) {
        LyricsTextSizeDialog(
            selected = lyricsTextSize,
            onSelected = { size ->
                lyricsTextSize = size
                DisplayPrefs.setLyricsTextSize(context, size)
            },
            onDismiss = { showLyricsTextSizeDialog = false }
        )
    }

    if (showGuidedReadingColorsDialog) {
        GuidedReadingColorsDialog(
            enabled = guidedReadingColorsEnabled,
            colorA = guidedReadingColorA,
            colorB = guidedReadingColorB,
            onEnabledChange = { enabled ->
                guidedReadingColorsEnabled = enabled
                DisplayPrefs.setGuidedReadingColorsEnabled(context, enabled)
            },
            onColorAChange = { color ->
                guidedReadingColorA = color
                DisplayPrefs.setGuidedReadingColorA(context, color)
            },
            onColorBChange = { color ->
                guidedReadingColorB = color
                DisplayPrefs.setGuidedReadingColorB(context, color)
            },
            onDismiss = { showGuidedReadingColorsDialog = false }
        )
    }

    if (isExportingLiveSongs) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = stringResource(R.string.more_live_songs_export_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(
                            R.string.more_live_songs_export_progress,
                            exportLiveSongsDone,
                            exportLiveSongsTotal
                        ),
                        color = Color(0xFFF5F5F5)
                    )
                    exportLiveSongsCurrentTitle?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (restoreLibraryStageText != null || isRestoringLibrary) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1B1B1B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF90CAF9))
                    Text(
                        text = restoreLibraryStageText ?: sLibraryRestorePreparing,
                        color = Color(0xFFF5F5F5),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isRestoringLibrary && restoreLibraryTotal > 0) {
                    Text(
                        text = stringResource(
                            R.string.more_library_restore_progress,
                            restoreLibraryDone,
                            restoreLibraryTotal
                        ),
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    }
                    restoreLibraryCurrentTitle?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    exportLiveSongsResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { exportLiveSongsResultMessage = null },
            title = {
                Text(text = stringResource(R.string.more_live_songs_export_title))
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = { exportLiveSongsResultMessage = null }) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        )
    }

    restoreLibraryResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreLibraryResultMessage = null },
            title = {
                Text(text = stringResource(R.string.more_library_restore_title))
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = { restoreLibraryResultMessage = null }) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        )
    }

    playlistImportResultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { playlistImportResultMessage = null },
            title = {
                Text(text = stringResource(R.string.more_item_import_playlist))
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = { playlistImportResultMessage = null }) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        )
    }

    pendingRestoreScan?.let { scan ->
        Dialog(
            onDismissRequest = { pendingRestoreScan = null }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1B1B1B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.more_library_restore_title),
                        color = Color(0xFFF5F5F5),
                        fontSize = 20.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = context.getString(
                                R.string.more_library_restore_summary,
                                scan.songCount,
                                scan.playlistCount,
                                scan.prompterCount,
                                scan.conflictCount
                            ),
                            color = Color(0xFFF5F5F5)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                startLibraryRestore(scan, LibraryRestoreConflictMode.Preserve)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.more_library_restore_keep_existing))
                        }

                        TextButton(
                            onClick = {
                                startLibraryRestore(scan, LibraryRestoreConflictMode.Replace)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.more_library_restore_replace_existing))
                        }

                        TextButton(
                            onClick = { pendingRestoreScan = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }

    if (showExportProDialog) {
        AlertDialog(
            onDismissRequest = { showExportProDialog = false },
            title = { Text(text = sExportProDialogTitle) },
            text = { Text(text = sExportProDialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportProDialog = false
                        openUpgradeToPro()
                    }
                ) {
                    Text(text = sUpgradeToPro)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportProDialog = false }) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
        )
    }

}

@Composable
private fun SettingsHeader(label: String) {
    Text(
        text = label.uppercase(),
        color = Color(0xFFB0BEC5),
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsItem(
    label: String,
    subtitle: String? = null,
    value: String? = null,
    helpText: String? = subtitle,
    onHelpClick: (String) -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFF5F5F5),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(0.7f, fill = false)
            )
        }
        if (!helpText.isNullOrBlank()) {
            SettingsHelpIcon(
                onClick = { onHelpClick(helpText) },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

@Composable
private fun SwitchSettingItem(
    label: String,
    checked: Boolean,
    helpText: String? = null,
    onHelpClick: (String) -> Unit = {},
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFF5F5F5),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        if (!helpText.isNullOrBlank()) {
            SettingsHelpIcon(
                onClick = { onHelpClick(helpText) },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

@Composable
private fun SettingsHelpIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(32.dp)
            .height(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            color = Color(0xFFFFECB3),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingHelpDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Text(
                text = text,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                fontSize = 14.sp,
                color = Color(0xFFE0E0E0)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun LyricsTextSizeDialog(
    selected: DisplayPrefs.LyricsTextSize,
    onSelected: (DisplayPrefs.LyricsTextSize) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        DisplayPrefs.LyricsTextSize.SMALL,
        DisplayPrefs.LyricsTextSize.NORMAL,
        DisplayPrefs.LyricsTextSize.LARGE,
        DisplayPrefs.LyricsTextSize.EXTRA_LARGE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_lyrics_text_size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_lyrics_text_size_hint),
                    color = Color(0xFFBDBDBD),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { onSelected(option) }
                        )
                        Text(
                            text = lyricsTextSizeOptionLabel(option),
                            color = Color(0xFFF5F5F5),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun lyricsTextSizeOptionLabel(size: DisplayPrefs.LyricsTextSize): String {
    return when (size) {
        DisplayPrefs.LyricsTextSize.SMALL -> stringResource(R.string.settings_lyrics_text_size_small)
        DisplayPrefs.LyricsTextSize.NORMAL -> stringResource(R.string.settings_lyrics_text_size_normal)
        DisplayPrefs.LyricsTextSize.LARGE -> stringResource(R.string.settings_lyrics_text_size_large)
        DisplayPrefs.LyricsTextSize.EXTRA_LARGE -> stringResource(R.string.settings_lyrics_text_size_extra_large)
    }
}

@Composable
private fun GuidedReadingColorsDialog(
    enabled: Boolean,
    colorA: Int,
    colorB: Int,
    onEnabledChange: (Boolean) -> Unit,
    onColorAChange: (Int) -> Unit,
    onColorBChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        GuidedReadingColorOption(DisplayPrefs.DEFAULT_GUIDED_READING_COLOR_A, R.string.settings_guided_reading_color_white),
        GuidedReadingColorOption(DisplayPrefs.DEFAULT_GUIDED_READING_COLOR_B, R.string.settings_guided_reading_color_soft_yellow),
        GuidedReadingColorOption(0xFF90CAF9.toInt(), R.string.settings_guided_reading_color_soft_blue),
        GuidedReadingColorOption(0xFFA5D6A7.toInt(), R.string.settings_guided_reading_color_soft_green)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_guided_reading_colors)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_guided_reading_colors_enabled),
                        color = Color(0xFFF5F5F5),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange
                    )
                }
                Text(
                    text = stringResource(R.string.settings_guided_reading_colors_hint),
                    color = Color(0xFFBDBDBD),
                    fontSize = 12.sp
                )
                GuidedReadingColorPickerRow(
                    label = stringResource(R.string.settings_guided_reading_color_a),
                    selectedColor = colorA,
                    options = options,
                    onColorChange = onColorAChange
                )
                GuidedReadingColorPickerRow(
                    label = stringResource(R.string.settings_guided_reading_color_b),
                    selectedColor = colorB,
                    options = options,
                    onColorChange = onColorBChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun GuidedReadingColorPickerRow(
    label: String,
    selectedColor: Int,
    options: List<GuidedReadingColorOption>,
    onColorChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = Color(0xFFF5F5F5),
            fontSize = 13.sp
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onColorChange(option.argb) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedColor == option.argb,
                    onClick = { onColorChange(option.argb) }
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(20.dp)
                        .background(Color(option.argb), RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(option.labelRes),
                    color = Color(0xFFF5F5F5),
                    fontSize = 13.sp
                )
            }
        }
    }
}

private data class GuidedReadingColorOption(
    val argb: Int,
    val labelRes: Int
)

@Composable
private fun SettingsInfoItem(
    label: String,
    value: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFF5F5F5),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Color(0xFF9E9E9E),
            fontSize = 12.sp
        )
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

private fun buildLiveSongsExportTarget(
    context: Context,
    treeUri: android.net.Uri
): DocumentFile? {
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    if (!root.isDirectory) return null
    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
    val folderName = "Export_${formatter.format(Date())}"
    return root.findFile(folderName)?.takeIf { it.isDirectory }
        ?: root.createDirectory(folderName)
}

private fun exportLiveSongToTree(
    context: Context,
    exportDir: DocumentFile,
    songId: String
): Boolean {
    val song = SmpLibraryScanner(context).findSongById(songId) ?: return false
    val cacheFile = SmpExporter.exportSongUnitToCacheSmp(context, song) ?: return false
    return try {
        val targetName = resolveAvailableBackupExportName(
            exportDir = exportDir,
            desiredName = cacheFile.name.ifBlank { "${song.title}.smp" }
        )
        val targetFile = exportDir.createFile("application/octet-stream", targetName)
            ?: return false
        context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
            cacheFile.inputStream().use { input ->
                input.copyTo(output)
            }
            output.flush()
        } ?: return false
        true
    } catch (_: Throwable) {
        false
    } finally {
        runCatching { cacheFile.delete() }
    }
}

private data class LibraryBackupArchiveItem(
    val archiveUri: Uri,
    val displayName: String
)

private enum class LibraryRestoreConflictMode {
    Preserve,
    Replace
}

private data class LibraryRestoreSmpFile(
    val uri: Uri,
    val displayName: String,
    val stableSongId: String?,
    val conflictWithRuntime: Boolean
)

private data class LibraryRestorePrompterItem(
    val id: String,
    val title: String,
    val text: String,
    val conflictWithRuntime: Boolean
)

private data class LibraryRestoreScanResult(
    val folderUri: Uri,
    val smpFiles: List<LibraryRestoreSmpFile>,
    val stateJson: String?,
    val prompterItems: List<LibraryRestorePrompterItem>,
    val playlistCount: Int,
    val conflictCount: Int
) {
    val songCount: Int
        get() = smpFiles.size

    val prompterCount: Int
        get() = prompterItems.size
}

private data class LibraryRestoreExecutionResult(
    val importedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val promptersImportedCount: Int,
    val promptersSkippedCount: Int,
    val stateRestored: Boolean,
    val stateWarningCount: Int,
    val stateFailureCount: Int,
    val lastImportedSongId: String? = null,
    val lastPlayed: BackupManager.LastPlayed? = null
)

private fun selectWorkspaceArchivesForBackup(
    runtimeSongIds: Set<String>,
    candidates: List<SmpUserArchiveCandidate>
): List<LibraryBackupArchiveItem> {
    val selectedSongIds = linkedSetOf<String>()
    return candidates
        .distinctBy { it.archiveUri.toString() }
        .mapNotNull { candidate ->
            val stableSongId = candidate.stableSongId?.trim()?.takeIf { it.isNotEmpty() }
            if (stableSongId != null) {
                if (stableSongId in runtimeSongIds) {
                    return@mapNotNull null
                }
                if (!selectedSongIds.add(stableSongId)) {
                    return@mapNotNull null
                }
            }
            LibraryBackupArchiveItem(
                archiveUri = candidate.archiveUri,
                displayName = candidate.archiveUri.lastPathSegment ?: "archive.smp"
            )
        }
}

private fun scanLibraryRestoreFolder(
    context: Context,
    treeUri: Uri
): LibraryRestoreScanResult? {
    Log.d(RESTORE_DIAG_TAG, "selectedRootUri=$treeUri")
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    if (!root.isDirectory) return null

    val runtimeSongsById = SmpLibraryScanner(context)
        .listSongs()
        .mapNotNull { song ->
            val songId = song.id.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            songId to song
        }
        .toMap()
    val runtimeSongIds = runtimeSongsById.keys
    val runtimePrompterIds = TextSongRepository.exportAll(context).keys
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    val selectedSongIds = linkedSetOf<String>()
    val selectedUris = linkedSetOf<String>()
    val selectedNameSizes = linkedSetOf<String>()
    var stateJson: String? = null
    var promptersJson: String? = null
    val directFiles = root.listFiles()
        .orEmpty()
        .filter { it.isFile }
        .sortedBy { it.name.orEmpty().lowercase() }
    val smpFiles = mutableListOf<LibraryRestoreSmpFile>()
    var rawSmpCount = 0
    val parent = root.uri.toString()
    directFiles.forEach { file ->
        val name = file.name.orEmpty()
        when {
            name.equals("state.json", ignoreCase = true) -> {
                stateJson = runCatching {
                    context.contentResolver.openInputStream(file.uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                Log.d(
                    RESTORE_DIAG_TAG,
                    "stateJsonFound=${stateJson != null} uri=${file.uri}"
                )
            }

            name.equals("prompters.json", ignoreCase = true) -> {
                promptersJson = runCatching {
                    context.contentResolver.openInputStream(file.uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }

            isMacBackupNoiseFile(name) -> {
                if (SmpWorkspaceArchiveStore.isSupportedArchiveFileName(name)) {
                    rawSmpCount += 1
                }
                Log.d(
                    RESTORE_DIAG_TAG,
                    "ignoredMacResourceFork name=$name uri=${file.uri} parent=$parent size=${file.length()}"
                )
            }

            !SmpWorkspaceArchiveStore.isSupportedArchiveFileName(name) -> Unit

            else -> {
                rawSmpCount += 1
                val index = rawSmpCount
                val uriString = file.uri.toString()
                val size = file.length()
                val stableSongId = SmpArchiveSongIdResolver.readStableSongId(context, file.uri)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                Log.d(
                    RESTORE_DIAG_TAG,
                    "discoveredSmp index=$index name=$name uri=${file.uri} parent=$parent size=$size songId=${stableSongId ?: "null"} source=selected_folder"
                )

                if (stableSongId != null && !selectedSongIds.add(stableSongId)) {
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "duplicateBySongId=$stableSongId name=$name uri=${file.uri} size=$size"
                    )
                    return@forEach
                }

                if (!selectedUris.add(uriString)) {
                    Log.d(RESTORE_DIAG_TAG, "duplicateByUri=$uriString name=$name size=$size")
                    return@forEach
                }

                val nameSizeKey = "${name.lowercase(Locale.ROOT)}|$size"
                if (!selectedNameSizes.add(nameSizeKey)) {
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "duplicateByName=$name size=$size uri=${file.uri}"
                    )
                    return@forEach
                }

                val existingSong = stableSongId?.let { runtimeSongsById[it] }
                if (stableSongId != null && existingSong != null) {
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "conflict songId=$stableSongId existingTitle=${existingSong.title} incomingTitle=$name incomingUri=${file.uri}"
                    )
                }
                smpFiles += LibraryRestoreSmpFile(
                    uri = file.uri,
                    displayName = name,
                    stableSongId = stableSongId,
                    conflictWithRuntime = existingSong != null
                )
            }
        }
    }

    val playlistCount = parseBackupPlaylistCount(stateJson)
    Log.d(
        RESTORE_DIAG_TAG,
        "discoveredSmp rawCount=$rawSmpCount count=${smpFiles.size} source=selected_folder stateJsonFound=${stateJson != null}"
    )
    val prompterItems = parseBackupPrompterItems(
        rawJson = promptersJson,
        runtimePrompterIds = runtimePrompterIds
    )
    return LibraryRestoreScanResult(
        folderUri = treeUri,
        smpFiles = smpFiles,
        stateJson = stateJson,
        prompterItems = prompterItems,
        playlistCount = playlistCount,
        conflictCount = smpFiles.count { it.conflictWithRuntime } +
            prompterItems.count { it.conflictWithRuntime }
    )
}

private fun isMacBackupNoiseFile(name: String): Boolean {
    val cleanName = name.trim()
    return cleanName.equals(".DS_Store", ignoreCase = true) ||
        cleanName.startsWith("._") ||
        cleanName.startsWith(".")
}

private fun parseBackupPlaylistCount(stateJson: String?): Int {
    if (stateJson.isNullOrBlank()) return 0
    return runCatching {
        JSONObject(stateJson).optJSONObject("playlists")?.length() ?: 0
    }.getOrDefault(0)
}

private fun logPlaylistRestoreDiagnostics(
    stateJson: String,
    runtimeSongIds: Set<String>
) {
    runCatching {
        val playlists = JSONObject(stateJson).optJSONObject("playlists") ?: return
        val names = playlists.keys()
        while (names.hasNext()) {
            val playlistName = names.next()
            Log.d(RESTORE_DIAG_TAG, "playlistRestore name=$playlistName source=state_json")
            val arr = playlists.optJSONArray(playlistName) ?: continue
            for (index in 0 until arr.length()) {
                val entry = arr.opt(index)
                val uri = when (entry) {
                    is JSONObject -> entry.optString("uri", "").trim()
                    else -> entry?.toString().orEmpty().trim()
                }
                if (uri.isBlank() || isGroupHeader(uri) || isGroupEnd(uri) || isPrompterItem(uri)) {
                    continue
                }
                val songId = when (entry) {
                    is JSONObject -> entry.optString("songId", "").trim().ifBlank { null }
                    else -> null
                } ?: getSmpSongId(uri) ?: extractRuntimeSongIdForRestoreDiag(uri)
                Log.d(
                    RESTORE_DIAG_TAG,
                    "playlistItem songId=${songId ?: "null"} playlistItemExistsInLibrary=${songId != null && songId in runtimeSongIds}"
                )
                Log.d(
                    RESTORE_DIAG_TAG,
                    "playlistRestoreReady libraryContains songId=${songId ?: "null"} ${songId != null && songId in runtimeSongIds}"
                )
            }
        }
    }.onFailure { error ->
        Log.w(RESTORE_DIAG_TAG, "playlistRestore diagnostics failed=${error.message}")
    }
}

private fun extractRuntimeSongIdForRestoreDiag(uriString: String): String? {
    val path = runCatching { Uri.parse(uriString).path }.getOrNull()
        ?.replace('\\', '/')
        ?: return null
    val marker = "/tracks/"
    val markerIndex = path.lastIndexOf(marker)
    if (markerIndex < 0) return null
    val remainder = path.substring(markerIndex + marker.length)
    val separatorIndex = remainder.indexOf('/')
    if (separatorIndex <= 0) return null
    return remainder.substring(0, separatorIndex)
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun parseBackupPrompterItems(
    rawJson: String?,
    runtimePrompterIds: Set<String>
): List<LibraryRestorePrompterItem> {
    if (rawJson.isNullOrBlank()) return emptyList()
    return runCatching {
        val root = JSONObject(rawJson)
        val items = root.optJSONArray("prompters") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isEmpty()) continue
                add(
                    LibraryRestorePrompterItem(
                        id = id,
                        title = item.optString("title", "").trim(),
                        text = item.optString("text", "").trim(),
                        conflictWithRuntime = id in runtimePrompterIds
                    )
                )
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

private fun restoreLibraryFromBackupFolder(
    context: Context,
    scanResult: LibraryRestoreScanResult,
    conflictMode: LibraryRestoreConflictMode,
    onProgress: (done: Int, total: Int, currentTitle: String?) -> Unit
): LibraryRestoreExecutionResult {
    val scanner = SmpLibraryScanner(context)
    val importer = SmpImporter(context)
    val runtimeSongsById = scanner.listSongs().associateBy { it.id.trim() }
    Log.d(RESTORE_DIAG_TAG, "libraryIndexCountBeforeRefresh=${runtimeSongsById.size}")
    val importedSongs = mutableListOf<BackupBundleImportedSong>()
    val importedOrExistingSongIds = linkedSetOf<String>()
    var importedCount = 0
    var skippedCount = 0
    var failedCount = 0
    var promptersImportedCount = 0
    var promptersSkippedCount = 0
    var completed = 0
    var lastImportedSongId: String? = null
    val total = scanResult.songCount +
        scanResult.prompterCount +
        if (scanResult.stateJson != null) 1 else 0

    scanResult.smpFiles.forEach { smpFile ->
        onProgress(completed, total, smpFile.displayName)
        val stableSongId = smpFile.stableSongId
        val existingSong = stableSongId?.let { runtimeSongsById[it] }
        val shouldImport = when {
            stableSongId == null -> true
            existingSong == null -> true
            conflictMode == LibraryRestoreConflictMode.Replace -> true
            else -> false
        }
        if (!shouldImport && existingSong != null) {
            val variantsOutcome = importer.restoreArrangementVariantsOnly(
                uri = smpFile.uri,
                sourceSong = existingSong,
                replaceExisting = false
            )
            if (variantsOutcome.success) {
                skippedCount += 1
            } else {
                failedCount += 1
            }
            importedOrExistingSongIds += existingSong.id
            Log.d(
                RESTORE_DIAG_TAG,
                "skippedDuplicateSongId=$stableSongId importedSongId=${existingSong.id}"
            )
            importedSongs += BackupBundleImportedSong(
                bundleSongId = stableSongId,
                importedSongId = existingSong.id,
                storageFolder = existingSong.storageFolder
            )
        } else {
            Log.d(RESTORE_DIAG_TAG, "importing uri=${smpFile.uri}")
            val importedSong = importer.importSmp(smpFile.uri)
            val runtimePath = importedSong?.storageFolder
            val runtimeExists = runtimePath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.isDirectory == true
            Log.d(
                RESTORE_DIAG_TAG,
                "importedResult success=${importedSong != null && runtimeExists} songId=${importedSong?.id ?: "null"} title=${importedSong?.title ?: "null"}"
            )
            Log.d(
                RESTORE_DIAG_TAG,
                "importedSongId=${importedSong?.id ?: "null"} importedTitle=${importedSong?.title ?: "null"} importedDisplayTitle=${importedSong?.title ?: "null"} importedAudioPath=${importedSong?.audioPath ?: "null"}"
            )
            Log.d(
                RESTORE_DIAG_TAG,
                "runtimePath=${runtimePath ?: "null"} runtimeExists=$runtimeExists"
            )
            if (importedSong != null && runtimeExists) {
                importedCount += 1
                lastImportedSongId = importedSong.id
                importedOrExistingSongIds += importedSong.id
                val cleanImportedTitle = importedSong.title
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                if (cleanImportedTitle != null) {
                    TitleAliasesStore.setTitleForTrack(
                        context = context,
                        trackUriString = buildSmpItem(importedSong.id),
                        newTitle = cleanImportedTitle
                    )
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "libraryRegister songId=${importedSong.id} updatedLibraryTitle=$cleanImportedTitle"
                    )
                }
                Log.d(
                    RESTORE_DIAG_TAG,
                    "libraryRegister songId=${importedSong.id}"
                )
                stableSongId?.let { bundleSongId ->
                    importedSongs += BackupBundleImportedSong(
                        bundleSongId = bundleSongId,
                        importedSongId = importedSong.id,
                        storageFolder = importedSong.storageFolder
                    )
                }
            } else {
                failedCount += 1
                Log.d(
                    RESTORE_DIAG_TAG,
                    "importedResult success=false songId=${stableSongId ?: "null"} title=${smpFile.displayName} failure=${importer.lastFailureReason ?: "unknown"}"
                )
            }
        }
        completed += 1
        onProgress(completed, total, smpFile.displayName)
    }

    scanResult.prompterItems.forEach { prompter ->
        onProgress(completed, total, "prompters.json")
        val existingPrompter = TextSongRepository.get(context, prompter.id)
        val shouldImport = when {
            existingPrompter == null -> true
            conflictMode == LibraryRestoreConflictMode.Replace -> true
            else -> false
        }
        if (shouldImport) {
            TextSongRepository.importOne(
                context = context,
                id = prompter.id,
                title = prompter.title,
                content = prompter.text
            )
            promptersImportedCount += 1
        } else {
            promptersSkippedCount += 1
        }
        completed += 1
        onProgress(completed, total, "prompters.json")
    }

    var stateRestored = false
    var stateWarningCount = 0
    var stateFailureCount = 0
    var lastPlayed: BackupManager.LastPlayed? = null
    Log.d(
        RESTORE_DIAG_TAG,
        "importedSuccessCount=$importedCount importedFailCount=$failedCount"
    )
    Log.d(RESTORE_DIAG_TAG, "libraryIndexCountBeforeRefresh=${runtimeSongsById.size}")
    val songsAfterImportById = scanner.listSongs()
        .associateBy { it.id.trim() }
    Log.d(RESTORE_DIAG_TAG, "libraryIndexCountAfterRefresh=${songsAfterImportById.size}")
    val missingImportedSongIds = importedOrExistingSongIds
        .filter { it !in songsAfterImportById.keys }
    importedOrExistingSongIds.forEach { songId ->
        Log.d(
            RESTORE_DIAG_TAG,
            "playlistRestoreReady libraryContains songId=$songId ${songId in songsAfterImportById.keys}"
        )
    }
    missingImportedSongIds.forEach { songId ->
        Log.w(
            RESTORE_DIAG_TAG,
            "playlistRestoreLibraryIndexPending songId=$songId libraryIndexCountAfterRefresh=${songsAfterImportById.size}"
        )
    }

    scanResult.stateJson?.let { stateJson ->
        onProgress(completed, total, "state.json")
        when (val remapResult = BackupStateRemapper.remapBundleStateJson(stateJson, importedSongs)) {
            is BackupStateRemapResult.Success -> {
                stateWarningCount = remapResult.warnings.size + missingImportedSongIds.size
                logPlaylistRestoreDiagnostics(
                    stateJson = remapResult.stateJson,
                    runtimeSongIds = songsAfterImportById.keys
                )
                BackupManager.importState(
                    context = context,
                    json = remapResult.stateJson,
                    mergePlaylists = true
                ) { lastPlayed = it }
                val saved = PlaylistStateStore.savePlaylistsSnapshot(context)
                val restored = if (saved) {
                    PlaylistStateStore.restorePlaylistsIntoRepository(
                        context = context,
                        preferInternalState = true
                    )
                } else {
                    null
                }
                val restoredPlaylistCount = restored?.restoredPlaylistCount
                    ?: PlaylistRepository.getPlaylists().size
                Log.d(
                    RESTORE_DIAG_TAG,
                    "libraryIndexCountAfterRestart=${scanner.listSongs().size} playlistStoreSaved=$saved playlistStoreReload=${restored?.success ?: false} restoredPlaylistCount=$restoredPlaylistCount"
                )
                stateRestored = true
            }

            is BackupStateRemapResult.Failure -> {
                stateFailureCount = remapResult.failures.size
            }
        }
        completed += 1
        onProgress(completed, total, "state.json")
    }

    return LibraryRestoreExecutionResult(
        importedCount = importedCount,
        skippedCount = skippedCount,
        failedCount = failedCount,
        promptersImportedCount = promptersImportedCount,
        promptersSkippedCount = promptersSkippedCount,
        stateRestored = stateRestored,
        stateWarningCount = stateWarningCount,
        stateFailureCount = stateFailureCount,
        lastImportedSongId = lastImportedSongId,
        lastPlayed = lastPlayed
    )
}

private fun formatLibraryRestoreResultMessage(
    context: Context,
    result: LibraryRestoreExecutionResult
): String {
    val stateLine = when {
        result.stateRestored && result.stateWarningCount > 0 -> context.getString(
            R.string.more_library_restore_result_state_with_warnings,
            result.stateWarningCount
        )

        result.stateRestored -> context.getString(R.string.more_library_restore_result_state_restored)

        result.stateFailureCount > 0 -> context.getString(
            R.string.more_library_restore_result_state_failed,
            result.stateFailureCount
        )

        else -> context.getString(R.string.more_library_restore_result_state_missing)
    }
    return context.getString(
        R.string.more_library_restore_result,
        result.importedCount,
        result.skippedCount,
        result.failedCount,
        result.promptersImportedCount,
        result.promptersSkippedCount,
        stateLine
    )
}

private fun copyWorkspaceArchiveToTree(
    context: Context,
    exportDir: DocumentFile,
    archive: LibraryBackupArchiveItem
): Boolean {
    val desiredName = queryBackupSourceDisplayName(context, archive.archiveUri)
        ?.takeIf { SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it) }
        ?: buildFallbackWorkspaceArchiveName(context, archive.archiveUri)
    val targetName = resolveAvailableBackupExportName(exportDir, desiredName)
    val targetFile = exportDir.createFile("application/octet-stream", targetName) ?: return false
    return try {
        context.contentResolver.openInputStream(archive.archiveUri)?.use { input ->
            context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
                input.copyTo(output)
                output.flush()
            }
        } != null
    } catch (_: Throwable) {
        false
    }
}

private fun writeLibraryBackupStateToTree(
    context: Context,
    exportDir: DocumentFile
): Boolean {
    val stateJson = BackupManager.exportState(
        context = context,
        lastPlayer = null,
        libraryFolders = listOfNotNull(
            WorkspaceResolver.resolve(context).workspaceRootUri?.toString()?.takeIf { it.isNotBlank() }
        )
    )
    val stateFile = exportDir.findFile("state.json")
        ?.takeIf { it.isFile }
        ?: exportDir.createFile("application/json", "state.json")
        ?: return false
    return try {
        context.contentResolver.openOutputStream(stateFile.uri, "w")?.use { output ->
            output.write(stateJson.toByteArray(Charsets.UTF_8))
            output.flush()
        } != null
    } catch (_: Throwable) {
        false
    }
}

private fun writeLibraryBackupPromptersToTree(
    context: Context,
    exportDir: DocumentFile
): Boolean {
    val items = TextSongRepository.exportAll(context)
        .toSortedMap()
        .map { (id, data) ->
            JSONObject().apply {
                put("id", id)
                put("title", data.title)
                put("text", data.content)
            }
        }
    val root = JSONObject().apply {
        put("version", 1)
        put("prompters", JSONArray(items))
    }
    val targetFile = exportDir.findFile("prompters.json")
        ?.takeIf { it.isFile }
        ?: exportDir.createFile("application/json", "prompters.json")
        ?: return false
    return try {
        context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
            output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            output.flush()
        } != null
    } catch (_: Throwable) {
        false
    }
}

private fun queryBackupSourceDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun buildFallbackWorkspaceArchiveName(
    context: Context,
    uri: Uri
): String {
    val stableSongId = SmpArchiveSongIdResolver.readStableSongId(context, uri)
        ?.takeIf { it.isNotBlank() }
    return when {
        stableSongId != null -> "$stableSongId.smp"
        else -> "archive_${System.currentTimeMillis()}.smp"
    }
}

private fun resolveAvailableBackupExportName(
    exportDir: DocumentFile,
    desiredName: String
): String {
    val cleanName = desiredName.trim().ifBlank { "song_export.smp" }
    val dotIndex = cleanName.lastIndexOf('.')
    val baseName = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
    val extension = if (dotIndex > 0) cleanName.substring(dotIndex) else ""
    var attempt = 0
    while (true) {
        val suffix = if (attempt == 0) "" else "_${attempt + 1}"
        val candidateName = "$baseName$suffix$extension"
        if (exportDir.findFile(candidateName) == null) {
            return candidateName
        }
        attempt += 1
    }
}
