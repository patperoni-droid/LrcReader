package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.adaptive.rememberSmpAdaptiveTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

private fun defaultDocumentsTreeUriOrNull(): Uri? {
    // ⚠️ Sur la majorité des Android, l’autorité “externalstorage” existe.
    // Sur certains modèles, ce hint peut être ignoré, mais il ne casse rien.
    return runCatching {
        DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents"
        )
    }.getOrNull()
}
@Composable
fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit,
    isMainPlaybackPlaying: Boolean = false,
    currentMainTrackGainDb: Int = 0,
    liveGainControlsEnabled: Boolean = false,
    onMainLiveGainDelta: (Int) -> Unit = {},
    getMainPositionMs: () -> Long = { 0L },
    getMainDurationMs: () -> Long = { 0L },
    seekMainToMs: (Long) -> Unit = {},
    onMainPlaybackPlayPause: () -> Unit = {},
    onMainPlaybackLivePlay: (() -> Unit)? = null,
    mainPlaybackSelectionInSync: Boolean = true
) {
    val adaptiveTokens = rememberSmpAdaptiveTokens()
    // ✅ IMPORTANT :
    // stringResource() est @Composable -> on l’utilise UNIQUEMENT dans le "corps" Composable
    // et pas dans remember { ... } ni dans des init de state.
    val sAssetsIntegrated = stringResource(R.string.filler_source_assets)

    // Palette cohérente avec la console & l’accordeur
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF171717),
            Color(0xFF101010),
            Color(0xFF181410)
        )
    )

    val onBg = Color(0xFFFFF8E1)
    val sub = Color(0xFFB0BEC5)
    val card = Color(0xFF1B1B1B)
    val accent = Color(0xFFFFC107)
    val customFolderAvailable = EditionConfig.isPro
    val sCustomFolderProDialogTitle = stringResource(R.string.filler_custom_folder_pro_dialog_title)
    val sCustomFolderProDialogMessage = stringResource(R.string.filler_custom_folder_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)

    fun normalizeToTreeUri(u: Uri?): Uri? {
        if (u == null) return null
        val p = u.path ?: return u

        // Déjà un tree uri
        if (p.contains("/tree/")) return u

        // Si on a un document uri, on le reconvertit en tree uri
        return runCatching {
            val docId = DocumentsContract.getDocumentId(u)
            DocumentsContract.buildTreeDocumentUri(u.authority, docId)
        }.getOrElse { u }
    }

    var isEnabled by remember { mutableStateOf(FillerSoundPrefs.isEnabled(context)) }
    var isUsingCustomSource by remember { mutableStateOf(FillerSoundPrefs.isUsingCustomFolder(context)) }
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getActiveFillerFolder(context)) }
    var showCustomFolderProDialog by remember { mutableStateOf(false) }

    // ⚠️ NE PAS mettre stringResource() dans remember { ... }
    var fillerName by remember(fillerUri) {
        mutableStateOf(fillerUri?.lastPathSegment ?: sAssetsIntegrated)
    }

    // mapping courbe : curseur “doux” en bas
    fun uiToRealVolume(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        return clamped * clamped * clamped // u³
    }

    fun realToUiVolume(r: Float): Float {
        val clamped = r.coerceIn(0f, 1f)
        return clamped.toDouble().pow(1.0 / 3.0).toFloat() // racine cubique
    }

    val initialReal = FillerSoundPrefs.getFillerVolume(context)
    var uiFillerVolume by remember { mutableStateOf(realToUiVolume(initialReal)) }

    fun setFillerVolumeReal(real: Float) {
        val safeReal = real.coerceIn(0f, 1f)
        uiFillerVolume = realToUiVolume(safeReal)
        FillerSoundPrefs.saveFillerVolume(context, safeReal)
        FillerSoundManager.setVolume(safeReal)
    }

    // état de lecture pour le gros bouton Play/Pause
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableIntStateOf(0) }
    var playbackDurationMs by remember { mutableIntStateOf(0) }
    var playbackDragging by remember { mutableStateOf(false) }
    var playbackDragPositionMs by remember { mutableIntStateOf(0) }
    var mainPlaybackPositionMs by remember { mutableIntStateOf(0) }
    var mainPlaybackDurationMs by remember { mutableIntStateOf(0) }
    var mainPlaybackDragging by remember { mutableStateOf(false) }
    var mainPlaybackDragPositionMs by remember { mutableIntStateOf(0) }
    val latestGetMainPositionMs by rememberUpdatedState(getMainPositionMs)
    val latestGetMainDurationMs by rememberUpdatedState(getMainDurationMs)
    val latestIsMainPlaybackPlaying by rememberUpdatedState(isMainPlaybackPlaying)

    // ✅ démarrage fiable (on lance directement en coroutine, pas via LaunchedEffect)
    val scope = rememberCoroutineScope()
    var isStarting by remember { mutableStateOf(false) }
    var startJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val managerPlaying = FillerSoundManager.isPlaying()
            isPlaying = managerPlaying
            playbackDurationMs = FillerSoundManager.getDurationMs()
            if (!playbackDragging) {
                playbackPositionMs = FillerSoundManager.getCurrentPositionMs()
            }
            delay(if (managerPlaying) 250L else 500L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            mainPlaybackDurationMs = latestGetMainDurationMs()
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            if (!mainPlaybackDragging) {
                mainPlaybackPositionMs = latestGetMainPositionMs()
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
            delay(if (latestIsMainPlaybackPlaying) 250L else 500L)
        }
    }

    fun startFillerFromUi() {
        if (isStarting) return

        if (!isEnabled) {
            isEnabled = true
            FillerSoundPrefs.setEnabled(context, true)
        }

        if (!FillerSoundManager.isPlaying()) {
            isStarting = true
            startJob?.cancel()
            startJob = scope.launch {
                runCatching {
                    FillerSoundManager.startFromUi(context)
                    FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                }
                isPlaying = FillerSoundManager.isPlaying()
                playbackDurationMs = FillerSoundManager.getDurationMs()
                playbackPositionMs = FillerSoundManager.getCurrentPositionMs()
                isStarting = false
            }
        }
    }

    fun stopFillerFromUi() {
        FillerSoundManager.fadeOutAndStop(200)
        isPlaying = false
        isStarting = false
    }

    // ✅ Hint d’ouverture : essayer d’ouvrir directement dans "Documents"
    // (Android peut l’ignorer selon le téléphone, mais quand il l’accepte ça évite le piège "Téléchargements")
    val initialDocumentsUri = remember { defaultDocumentsTreeUriOrNull() }
    val pickFillerFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { picked ->
            if (picked == null) return@rememberLauncherForActivityResult

            val treeUri = normalizeToTreeUri(picked) ?: picked

            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            FillerSoundPrefs.saveFillerFolder(context, treeUri)
            FillerSoundPrefs.setUseCustomFolder(context, true)
            isUsingCustomSource = true
            fillerUri = treeUri
            fillerName = treeUri.lastPathSegment ?: sAssetsIntegrated
            Toast.makeText(
                context,
                context.getString(R.string.filler_folder_linked, fillerName),
                Toast.LENGTH_SHORT
            ).show()
        }
    )

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

    // ─────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 210.dp)
        ) {
            Spacer(Modifier.height(10.dp))

        // ───────── CARTE PRINCIPALE (réglages + gros boutons) ─────────
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.padding(12.dp)) {

                // Bandeau façon console
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF3A2C24),
                                        Color(0xFF4B372A),
                                        Color(0xFF3A2C24)
                                    )
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.filler_bus_title),
                            color = Color(0xFFFFECB3),
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // ON / OFF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.filler_enable),
                            color = onBg,
                            fontSize = 14.sp
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            FillerSoundPrefs.setEnabled(context, checked)
                            if (!checked) {
                                FillerSoundManager.fadeOutAndStop(0)
                                isPlaying = false
                                isStarting = false
                                startJob?.cancel()
                                startJob = null
                            }
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                val realDisplay = uiToRealVolume(uiFillerVolume)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Slider(
                        value = uiFillerVolume,
                        onValueChange = { v ->
                            setFillerVolumeReal(uiToRealVolume(v))
                        },
                        valueRange = 0f..1f,
                        enabled = isEnabled,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = accent,
                            inactiveTrackColor = Color(0xFF424242),
                            thumbColor = accent
                        )
                    )
                    Text(
                        text = stringResource(R.string.filler_percent, (realDisplay * 100).toInt()),
                        color = onBg,
                        fontSize = 11.sp,
                        modifier = Modifier.width(48.dp)
                    )
                }

                if (isStarting) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = onBg
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.filler_starting),
                            color = sub,
                            fontSize = 11.sp
                        )
                    }
                }

            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.filler_source_title),
            color = onBg,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                val isDefaultSource = !customFolderAvailable || !isUsingCustomSource
                val defaultSourceOption: @Composable (Modifier) -> Unit = { optionModifier ->
                    Box(modifier = optionModifier) {
                        SourceOptionRow(
                            title = stringResource(R.string.filler_source_default_title),
                            subtitle = stringResource(R.string.filler_source_default_subtitle),
                            selected = isDefaultSource,
                            activeColor = accent,
                            titleColor = onBg,
                            subtitleColor = sub,
                            onClick = {
                                FillerSoundPrefs.setUseCustomFolder(context, false)
                                isUsingCustomSource = false
                                fillerName = sAssetsIntegrated
                            }
                        )
                    }
                }
                val customSourceOption: @Composable (Modifier) -> Unit = { optionModifier ->
                    Box(modifier = optionModifier) {
                        SourceOptionRow(
                            title = stringResource(R.string.filler_source_custom_folder),
                            subtitle = null,
                            selected = !isDefaultSource,
                            activeColor = accent,
                            titleColor = onBg,
                            subtitleColor = sub,
                            onClick = {
                                if (EditionConfig.isLite) {
                                    showCustomFolderProDialog = true
                                } else {
                                    FillerSoundPrefs.setUseCustomFolder(context, true)
                                    isUsingCustomSource = true
                                }
                            }
                        )
                    }
                }

                if (adaptiveTokens.tabletMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        defaultSourceOption(Modifier.weight(1f))
                        customSourceOption(Modifier.weight(1f))
                    }
                } else {
                    defaultSourceOption(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    customSourceOption(Modifier.fillMaxWidth())
                }
                if (customFolderAvailable) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { pickFillerFolderLauncher.launch(initialDocumentsUri) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.filler_choose_custom_folder), color = onBg)
                    }
                }
            }
        }

        if (showCustomFolderProDialog) {
            AlertDialog(
                onDismissRequest = { showCustomFolderProDialog = false },
                title = {
                    Text(
                        text = sCustomFolderProDialogTitle,
                        color = onBg
                    )
                },
                text = {
                    Text(
                        text = sCustomFolderProDialogMessage,
                        color = sub
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCustomFolderProDialog = false
                            openUpgradeToPro()
                        }
                    ) {
                        Text(sUpgradeToPro)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCustomFolderProDialog = false }
                    ) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))
    }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
            FillerControlLabel(
                text = stringResource(R.string.filler_local_controller_title),
                color = Color(0xFFB0BEC5)
            )
            Spacer(Modifier.height(4.dp))
            FillerLocalPlaybackControls(
                positionMs = if (playbackDragging) playbackDragPositionMs else playbackPositionMs,
                durationMs = playbackDurationMs,
                onSeekLivePreview = { newPos ->
                    if (playbackDurationMs > 0) {
                        playbackDragging = true
                        playbackDragPositionMs = newPos
                    }
                },
                onSeekCommit = { newPos ->
                    val safe = newPos.coerceIn(0, playbackDurationMs.coerceAtLeast(0))
                    playbackDragging = false
                    playbackPositionMs = safe
                    FillerSoundManager.seekTo(safe)
                },
                isPlaying = isPlaying,
                isStarting = isStarting,
                onPlay = ::startFillerFromUi,
                onStop = ::stopFillerFromUi,
                onPrev = {
                    playbackPositionMs = 0
                    playbackDragPositionMs = 0
                    FillerSoundManager.seekTo(0)
                }
            )
            Spacer(Modifier.height(10.dp))
            FillerControlLabel(
                text = stringResource(R.string.filler_main_playback_controller_title),
                color = Color(0xFFFFECB3)
            )
            Spacer(Modifier.height(4.dp))
            PlaybackControl(
                positionMs = if (mainPlaybackDragging) mainPlaybackDragPositionMs else mainPlaybackPositionMs,
                durationMs = mainPlaybackDurationMs,
                onSeekLivePreview = { newPos ->
                    if (mainPlaybackDurationMs > 0) {
                        mainPlaybackDragging = true
                        mainPlaybackDragPositionMs = newPos
                    }
                },
                onSeekCommit = { newPos ->
                    val safe = newPos.coerceIn(0, mainPlaybackDurationMs.coerceAtLeast(0))
                    mainPlaybackDragging = false
                    mainPlaybackPositionMs = safe
                    seekMainToMs(safe.toLong())
                },
                highlightColor = accent,
                isPlaying = isMainPlaybackPlaying,
                onPlayPause = onMainPlaybackPlayPause,
                onPrev = {
                    mainPlaybackPositionMs = 0
                    mainPlaybackDragPositionMs = 0
                    seekMainToMs(0L)
                },
                onNext = {},
                gainDb = currentMainTrackGainDb,
                onGainDelta = { deltaDb ->
                    if (liveGainControlsEnabled) {
                        onMainLiveGainDelta(deltaDb)
                    }
                },
                liveConsoleMode = adaptiveTokens.tabletMode,
                liveSelectionInSync = mainPlaybackSelectionInSync,
                onLivePlay = onMainPlaybackLivePlay
            )
        }
    }
}

@Composable
private fun FillerLocalPlaybackControls(
    positionMs: Int,
    durationMs: Int,
    onSeekLivePreview: (Int) -> Unit,
    onSeekCommit: (Int) -> Unit,
    isPlaying: Boolean,
    isStarting: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onPrev: () -> Unit
) {
    val panelShape = RoundedCornerShape(10.dp)
    val buttonShape = RoundedCornerShape(6.dp)
    val controlButtonSize = 44.dp
    val controlIconSize = 28.dp
    val fillerAccent = Color(0xFF90A4AE)
    val fillerPanel = Color(0xFF171C1F)
    val fillerBorder = Color(0xFF78909C).copy(alpha = 0.30f)
    val playButtonColor = Color(0xFF455A64)
    val stopButtonColor = Color(0xFF5D4037)
    val disabledButtonColor = Color.White.copy(alpha = 0.08f)
    val disabledIconColor = Color.White.copy(alpha = 0.42f)
    val controlBorder = Color.White.copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(fillerPanel, panelShape)
            .border(1.dp, fillerBorder, panelShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        TimeBar(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeekLivePreview = onSeekLivePreview,
            onSeekCommit = onSeekCommit,
            highlightColor = fillerAccent
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(controlButtonSize)
                    .background(if (isStarting) disabledButtonColor else playButtonColor, buttonShape)
                    .border(1.dp, controlBorder, buttonShape)
                    .clickable(enabled = !isStarting && !isPlaying, onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_cd_play),
                    tint = if (isStarting) disabledIconColor else Color.White,
                    modifier = Modifier.size(controlIconSize)
                )
            }

            Box(
                modifier = Modifier
                    .size(controlButtonSize)
                    .background(if (isPlaying || isStarting) stopButtonColor else disabledButtonColor, buttonShape)
                    .border(1.dp, controlBorder, buttonShape)
                    .clickable(enabled = isPlaying || isStarting, onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.library_lufs_preview_stop),
                    tint = if (isPlaying || isStarting) Color.White else disabledIconColor,
                    modifier = Modifier.size(controlIconSize)
                )
            }

            IconButton(
                onClick = onPrev,
                modifier = Modifier.size(controlButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.player_cd_prev),
                    tint = Color.White,
                    modifier = Modifier.size(controlIconSize)
                )
            }
        }
    }
}

@Composable
private fun FillerControlLabel(
    text: String,
    color: Color
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun SourceOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    activeColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(14.dp)
                .background(
                    color = if (selected) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(3.dp)
                )
        ) {
            if (!selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .background(Color(0xFF505050), RoundedCornerShape(3.dp))
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 12.sp
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
