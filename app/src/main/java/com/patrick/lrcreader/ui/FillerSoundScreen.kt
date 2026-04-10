package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.Job
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
    onBack: () -> Unit
) {
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
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getActiveFillerFolder(context)) }

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

    // état de lecture pour le gros bouton Play/Pause
    var isPlaying by remember { mutableStateOf(false) }

    // ✅ démarrage fiable (on lance directement en coroutine, pas via LaunchedEffect)
    val scope = rememberCoroutineScope()
    var isStarting by remember { mutableStateOf(false) }
    var startJob by remember { mutableStateOf<Job?>(null) }

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
            fillerUri = treeUri
            fillerName = treeUri.lastPathSegment ?: sAssetsIntegrated
            Toast.makeText(
                context,
                context.getString(R.string.filler_folder_linked, fillerName),
                Toast.LENGTH_SHORT
            ).show()
        }
    )

    // ─────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            )
            .verticalScroll(rememberScrollState())
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

                // VOLUME GLOBAL
                Text(stringResource(R.string.filler_volume), color = sub, fontSize = 11.sp)

                Slider(
                    value = uiFillerVolume,
                    onValueChange = { v ->
                        uiFillerVolume = v
                        val real = uiToRealVolume(v)
                        FillerSoundPrefs.saveFillerVolume(context, real)
                        FillerSoundManager.setVolume(real)
                    },
                    valueRange = 0f..1f,
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accent,
                        inactiveTrackColor = Color(0xFF424242),
                        thumbColor = accent
                    )
                )

                val realDisplay = uiToRealVolume(uiFillerVolume)
                Text(
                    text = stringResource(R.string.filler_percent, (realDisplay * 100).toInt()),
                    color = onBg,
                    fontSize = 11.sp
                )

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

                // ───────── GROS BOUTONS DE TRANSPORT ─────────
                Spacer(Modifier.height(8.dp))

                val canControlSelected = isEnabled

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PREVIOUS
                    IconButton(
                        onClick = {
                            if (!canControlSelected) return@IconButton

                            FillerSoundManager.previous(context)
                            FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                            isPlaying = true
                            isStarting = false
                            startJob?.cancel()
                            startJob = null
                        },
                        enabled = canControlSelected,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.filler_prev),
                            tint = if (canControlSelected) onBg else sub,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // PLAY / PAUSE
                    IconButton(
                        onClick = {
                            if (!canControlSelected) return@IconButton
                            if (isStarting) return@IconButton

                            if (!isEnabled) {
                                isEnabled = true
                                FillerSoundPrefs.setEnabled(context, true)
                            }

                            val isPlayingThis = FillerSoundManager.isPlaying()

                            if (!isPlayingThis) {
                                isStarting = true

                                startJob?.cancel()
                                startJob = scope.launch {
                                    runCatching {
                                        FillerSoundManager.startFromUi(context)
                                        FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                                    }

                                    isPlaying = FillerSoundManager.isPlaying()
                                    isStarting = false
                                }
                            } else {
                                FillerSoundManager.fadeOutAndStop(200)
                                isPlaying = false
                                isStarting = false
                            }
                        },
                        enabled = canControlSelected,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(80.dp)
                    ) {
                        val showPause = FillerSoundManager.isPlaying()

                        Icon(
                            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.filler_play_pause),
                            tint = if (canControlSelected) accent else sub,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    // NEXT
                    IconButton(
                        onClick = {
                            if (!canControlSelected) return@IconButton

                            FillerSoundManager.next(context)
                            FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                            isPlaying = true
                            isStarting = false
                            startJob?.cancel()
                            startJob = null
                        },
                        enabled = canControlSelected,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.filler_next),
                            tint = if (canControlSelected) onBg else sub,
                            modifier = Modifier.size(40.dp)
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
                val isDefaultSource = fillerUri == null
                SourceOptionRow(
                    title = stringResource(R.string.filler_source_default_title),
                    subtitle = stringResource(R.string.filler_source_default_subtitle),
                    selected = isDefaultSource,
                    activeColor = accent,
                    titleColor = onBg,
                    subtitleColor = sub,
                    onClick = {
                        FillerSoundPrefs.setUseCustomFolder(context, false)
                        fillerUri = null
                        fillerName = sAssetsIntegrated
                    }
                )
                Spacer(Modifier.height(10.dp))
                SourceOptionRow(
                    title = stringResource(R.string.filler_source_custom_folder),
                    subtitle = stringResource(R.string.filler_source_custom_subtitle),
                    selected = !isDefaultSource,
                    activeColor = accent,
                    titleColor = onBg,
                    subtitleColor = sub,
                    onClick = {
                        if (fillerUri != null) {
                            FillerSoundPrefs.setUseCustomFolder(context, true)
                        }
                    }
                )
                if (!isDefaultSource) {
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

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SourceOptionRow(
    title: String,
    subtitle: String,
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 10.sp
            )
        }
    }
}
