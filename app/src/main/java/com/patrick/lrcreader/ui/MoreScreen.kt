package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.MidiOutput
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.patrick.lrcreader.core.AppLanguagePrefs
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.BackupManager
import com.patrick.lrcreader.core.LegacyLibraryVisibilityPrefs
import com.patrick.lrcreader.core.LightIndicatorPrefs
import com.patrick.lrcreader.core.UiEntryPrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.exo.R

/* ─────────────────────────────
   Écran "Plus" (Paramètres)
   ───────────────────────────── */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    currentWaveformSongId: String? = null,
    requestedRoute: String? = null,
    requestedRouteToken: Int = 0,
    showDjTab: Boolean = false,
    showMainBusTab: Boolean = false,
    onShowDjTabChange: (Boolean) -> Unit = {},
    onShowMainBusTabChange: (Boolean) -> Unit = {},
    onAfterImport: (BackupManager.LastPlayed?) -> Unit = {},
    onOpenTuner: () -> Unit = {}     // callback pour l'accordeur
) {
    var current by remember { mutableStateOf(MoreSection.Root) }

    LaunchedEffect(requestedRouteToken) {
        val route = requestedRoute ?: return@LaunchedEffect
        current = MoreSection.entries.firstOrNull { it.route == route } ?: MoreSection.Root
    }

    fun navigate(route: String) {
        current = MoreSection.entries.firstOrNull { it.route == route } ?: MoreSection.Root
    }

    when (current) {
        MoreSection.Root -> MoreRootScreen(
            modifier = modifier,
            onOpenBackup = { navigate("backup") },
            onOpenFiller = { navigate("filler") },
            onOpenHistory = { navigate("history") },
            onOpenWaveformPreview = { navigate("waveform_preview") },
            showDjTab = showDjTab,
            showMainBusTab = showMainBusTab,
            onShowDjTabChange = onShowDjTabChange,
            onShowMainBusTabChange = onShowMainBusTabChange,
            onOpenTuner = onOpenTuner
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
            initialSongId = currentWaveformSongId
        )
    }
}

private enum class MoreSection(val route: String) {
    Root("root"),
    Backup("backup"),
    Filler("filler"),
    History("history"),
    WaveformPreview("waveform_preview")
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
    onOpenWaveformPreview: () -> Unit,
    showDjTab: Boolean,
    showMainBusTab: Boolean,
    onShowDjTabChange: (Boolean) -> Unit,
    onShowMainBusTabChange: (Boolean) -> Unit,
    onOpenTuner: () -> Unit
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(AppLanguagePrefs.getSavedLanguageTag(context)) }
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
