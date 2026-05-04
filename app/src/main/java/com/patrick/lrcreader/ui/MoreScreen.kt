package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import com.patrick.lrcreader.core.MidiOutput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.LegacyLibraryVisibilityPrefs
import com.patrick.lrcreader.core.LightIndicatorPrefs
import com.patrick.lrcreader.core.UiEntryPrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpArchiveSongIdResolver
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpUserArchiveRebuilder
import com.patrick.lrcreader.smp.SmpUserArchiveCandidate
import com.patrick.lrcreader.smp.SmpWorkspaceArchiveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ─────────────────────────────
   Écran "Plus" (Paramètres)
   ───────────────────────────── */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    currentWaveformSongId: String? = null,
    currentPlayingSongId: String? = null,
    requestedRoute: String? = null,
    requestedRouteToken: Int = 0,
    showDjTab: Boolean = false,
    showMainBusTab: Boolean = false,
    onShowDjTabChange: (Boolean) -> Unit = {},
    onShowMainBusTabChange: (Boolean) -> Unit = {},
    onAfterImport: (BackupManager.LastPlayed?) -> Unit = {},
    onOpenTuner: () -> Unit = {},     // callback pour l'accordeur
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

    when (current) {
        MoreSection.Root -> MoreRootScreen(
            modifier = modifier,
            currentPlayingSongId = currentPlayingSongId,
            onOpenBackup = { navigate("backup") },
            onOpenFiller = { navigate("filler") },
            onOpenHistory = { navigate("history") },
            onOpenArrangement = { navigate("arrangement") },
            onOpenWaveformPreview = { navigate("waveform_preview") },
            showDjTab = showDjTab,
            showMainBusTab = showMainBusTab,
            onShowDjTabChange = onShowDjTabChange,
            onShowMainBusTabChange = onShowMainBusTabChange,
            onOpenTuner = onOpenTuner
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
            onOpenCutting = { navigate("arrangement") },
            onBack = { navigate("root") }
        )

        MoreSection.Backup -> BackupScreen(
            context = context,
            onAfterImport = onAfterImport,
            onBack = { navigate("root") }
        )

        MoreSection.Filler -> FillerSoundScreen(
            context = context,
            onBack = { navigate("root") }
        )

        MoreSection.History -> HistoryScreen(
            modifier = modifier,
            context = context,
            onBack = { navigate("root") }
        )

        MoreSection.WaveformPreview -> WaveformPreviewScreen(
            modifier = modifier,
            onBack = { navigate("root") },
            initialSongId = currentWaveformSongId,
            currentPlayingSongId = currentPlayingSongId,
            onStopCurrentPlayback = onStopCurrentPlayback
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

/* ─────────────────────────────
   Menu principal – style rack analogique
   ───────────────────────────── */

@Composable
private fun MoreRootScreen(
    modifier: Modifier = Modifier,
    currentPlayingSongId: String?,
    onOpenBackup: () -> Unit,
    onOpenFiller: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenArrangement: () -> Unit,
    onOpenWaveformPreview: () -> Unit,
    showDjTab: Boolean,
    showMainBusTab: Boolean,
    onShowDjTabChange: (Boolean) -> Unit,
    onShowMainBusTabChange: (Boolean) -> Unit,
    onOpenTuner: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var exportLiveSongsTreeUri by remember {
        mutableStateOf(MoreLiveSongsExportPrefs.getTreeUri(context))
    }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExportProDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(AppLanguagePrefs.getSavedLanguageTag(context)) }
    var isExportingLiveSongs by remember { mutableStateOf(false) }
    var exportLiveSongsDone by remember { mutableStateOf(0) }
    var exportLiveSongsTotal by remember { mutableStateOf(0) }
    var exportLiveSongsCurrentTitle by remember { mutableStateOf<String?>(null) }
    var exportLiveSongsResultMessage by remember { mutableStateOf<String?>(null) }
    // ✅ Séquence de Program Change pour le test MIDI
    val testProgramChanges = listOf(8, 39, 58, 127)
    var testPcIndex by remember { mutableStateOf(0) }


    // État du switch "retour auto"
    var autoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
    }
    var showLightIndicator by remember {
        mutableStateOf(LightIndicatorPrefs.isEnabled(context))
    }
    var showOldWorldInLibrary by remember {
        mutableStateOf(LegacyLibraryVisibilityPrefs.isOldWorldVisible(context))
    }
    var showDjMode by remember { mutableStateOf(showDjTab) }
    var showMainBusMode by remember { mutableStateOf(showMainBusTab) }

    androidx.compose.runtime.LaunchedEffect(showDjTab) {
        showDjMode = showDjTab
    }
    androidx.compose.runtime.LaunchedEffect(showMainBusTab) {
        showMainBusMode = showMainBusTab
    }

    val currentLanguageLabel = when (selectedLanguageTag) {
        "fr" -> stringResource(R.string.settings_language_fr)
        "en" -> stringResource(R.string.settings_language_en)
        "es" -> stringResource(R.string.settings_language_es)
        else -> stringResource(R.string.settings_language_auto)
    }
    val sLiveSongsExportFailed = stringResource(R.string.more_live_songs_export_failed)
    val sExportProDialogTitle = stringResource(R.string.export_pro_dialog_title)
    val sExportProDialogMessage = stringResource(R.string.export_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    val isLite = EditionConfig.isLite
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

            val runtimeSongs = withContext(Dispatchers.IO) {
                SmpLibraryScanner(context.applicationContext).listSongs()
            }
            val archiveCandidates = withContext(Dispatchers.IO) {
                SmpUserArchiveRebuilder(context.applicationContext).listUserArchiveCandidates()
            }
            val runtimeSongIds = runtimeSongs.map { it.id.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val workspaceArchivesToCopy = selectWorkspaceArchivesForBackup(
                runtimeSongIds = runtimeSongIds,
                candidates = archiveCandidates
            )
            exportLiveSongsTotal = runtimeSongs.size + workspaceArchivesToCopy.size + 1

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
                            modifier = Modifier
                                .align(Alignment.Center)
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
                    SettingsItem(
                        label = stringResource(R.string.more_item_export_live_songs),
                        subtitle = stringResource(R.string.more_item_export_live_songs_subtitle),
                        onClick = {
                            if (isExportingLiveSongs) return@SettingsItem
                            if (isLite) {
                                showExportProDialog = true
                                return@SettingsItem
                            }
                            exportLiveSongsFolderLauncher.launch(exportLiveSongsTreeUri)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF262626))

                    // Bloc interface / audio
                    SettingsItem(
                        label = stringResource(R.string.more_item_language),
                        subtitle = stringResource(
                            R.string.settings_language_subtitle,
                            currentLanguageLabel
                        ),
                        onClick = { showLanguageDialog = true }
                    )

                    // Accordeur, dans le bloc Audio
                    SettingsItem(stringResource(R.string.more_item_tuner), onClick = onOpenTuner)

                    SwitchSettingItem(
                        label = stringResource(R.string.more_show_dj_mode),
                        checked = showDjMode,
                        onCheckedChange = { enabled ->
                            showDjMode = enabled
                            UiEntryPrefs.setShowDjTab(context, enabled)
                            onShowDjTabChange(enabled)
                        }
                    )

                    SwitchSettingItem(
                        label = stringResource(R.string.more_show_main_bus),
                        checked = showMainBusMode,
                        onCheckedChange = { enabled ->
                            showMainBusMode = enabled
                            UiEntryPrefs.setShowMainBusTab(context, enabled)
                            onShowMainBusTabChange(enabled)
                        }
                    )

                    // 🔁 Retour auto vers la playlist (ON/OFF)
                    SwitchSettingItem(
                        label = stringResource(R.string.more_auto_return_playlist),
                        checked = autoReturnEnabled,
                        onCheckedChange = { enabled ->
                            autoReturnEnabled = enabled
                            AutoReturnPrefs.setEnabled(context, enabled)
                        }
                    )

                    SwitchSettingItem(
                        label = stringResource(R.string.more_show_light_indicator),
                        checked = showLightIndicator,
                        onCheckedChange = { enabled ->
                            showLightIndicator = enabled
                            LightIndicatorPrefs.setEnabled(context, enabled)
                        }
                    )

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
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFF5F5F5),
            fontSize = 14.sp
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp
            )
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

@Composable
private fun SwitchSettingItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        }
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}

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
