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
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.DjIndexCache
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

@Composable
fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
    // ✅ IMPORTANT :
    // stringResource() est @Composable -> on l’utilise UNIQUEMENT dans le "corps" Composable
    // et pas dans remember { ... } ni dans des init de state.
    val sNoneSelected = stringResource(R.string.filler_none_selected)
    val sDefaultMusic = stringResource(R.string.filler_default_music_name)

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

    // ✅ Racine Music persistée par Bibliothèque
    val libraryRoot = remember { BackupFolderPrefs.get(context) }

    var isEnabled by remember { mutableStateOf(FillerSoundPrefs.isEnabled(context)) }
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerFolder(context)) }

    // ⚠️ NE PAS mettre stringResource() dans remember { ... }
    var fillerName by remember(fillerUri) {
        mutableStateOf(fillerUri?.lastPathSegment ?: sNoneSelected)
    }

    // ✅ Au premier affichage : si aucun dossier filler n’est défini, on prend Music de la Bibliothèque
    LaunchedEffect(Unit) {
        if (fillerUri == null && libraryRoot != null) {
            FillerSoundPrefs.saveFillerFolder(context, libraryRoot)
            fillerUri = libraryRoot
            fillerName = libraryRoot.lastPathSegment ?: sDefaultMusic
        }
    }

    fun canOpenTree(u: Uri?): Boolean {
        val tree = normalizeToTreeUri(u) ?: return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, tree)
            doc != null && doc.exists() && doc.canRead()
        }.getOrDefault(false)
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

    // ─────────────────────────────────────────────────────────────
    //  Ambiances rapides : 5 slots
    // ─────────────────────────────────────────────────────────────
    val slots = remember {
        mutableStateListOf<AmbianceSlot>().apply {
            addAll(AmbiancePrefs.loadSlots(context, 5))
        }
    }

    // ✅ Option : si slots vides, on met Music pour tester vite
    LaunchedEffect(libraryRoot) {
        if (libraryRoot != null) {
            slots.forEachIndexed { idx, slot ->
                if (slot.folderUri == null) {
                    slots[idx] = slot.copy(folderUri = libraryRoot)
                    AmbiancePrefs.saveSlot(context, slots[idx])
                }
            }
        }
    }

    // ambiance en cours de lecture (pour colorer en accent)
    var activeIndex by remember { mutableStateOf<Int?>(null) }

    // ambiance sélectionnée (pilotée par les gros boutons)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // état de lecture pour le gros bouton Play/Pause
    var isPlaying by remember { mutableStateOf(false) }

    // ✅ démarrage fiable (on lance directement en coroutine, pas via LaunchedEffect)
    val scope = rememberCoroutineScope()
    var isStarting by remember { mutableStateOf(false) }
    var startJob by remember { mutableStateOf<Job?>(null) }

    // ✅ Scan DJ (indépendant du playback)
    var isDjScanning by remember { mutableStateOf(false) }
    var djScanProgressText by remember {
        mutableStateOf(context.getString(R.string.filler_scan_dj_running))
    }

    // Picker
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showDjInfoDialog by remember { mutableStateOf(false) }
    var pendingSlotIndex by remember { mutableStateOf<Int?>(null) }

    // Dialog de renommage
    var slotToRenameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    // ─────────────────────────────────────────────────────────────
    //  1) Autorisation + scan DJ (1ère fois)
    // ─────────────────────────────────────────────────────────────
    val pickDjFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { picked ->
            if (picked == null) return@rememberLauncherForActivityResult

            val treeUri = normalizeToTreeUri(picked) ?: picked

            // permission persistante
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            // ✅ on enregistre la racine DJ (partagée avec l’écran DJ)
            DjFolderPrefs.save(context, treeUri)
            DjFolderPrefs.setScanned(context, false)

            // ✅ feedback immédiat (sinon l’utilisateur reclique)
            isDjScanning = true
            djScanProgressText = context.getString(R.string.filler_scan_dj_long)

            scope.launch {
                try {
                    val newDjIndex = withContext(Dispatchers.IO) {
                        buildDjFullIndex(context, treeUri) // ⚠️ treeUri, pas picked
                    }
                    DjIndexCache.save(context, newDjIndex)
                    DjFolderPrefs.setScanned(context, true)

                    // ✅ ouvrir le picker seulement quand le scan est terminé
                    showFolderPicker = true
                } catch (t: Throwable) {
                    android.util.Log.e("DJ_SCAN", "Scan DJ crash: ${t.message}", t)
                    Toast.makeText(
                        context,
                        context.getString(R.string.filler_dj_scan_error),
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isDjScanning = false
                }
            }
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
                                activeIndex = null
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

                if (isStarting || isDjScanning) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = onBg
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = if (isDjScanning)
                                stringResource(R.string.filler_scan_dj_wait_end)
                            else
                                stringResource(R.string.filler_starting),
                            color = sub,
                            fontSize = 11.sp
                        )
                    }
                }

                // ───────── GROS BOUTONS DE TRANSPORT ─────────
                Spacer(Modifier.height(8.dp))

                val currentSelectedSlot = selectedIndex?.let { idx -> slots.getOrNull(idx) }
                val canControlSelected = isEnabled && currentSelectedSlot?.folderUri != null

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
                            val slot = currentSelectedSlot!!
                            FillerSoundPrefs.saveFillerFolder(context, slot.folderUri!!)
                            fillerUri = slot.folderUri
                            fillerName = slot.folderUri!!.lastPathSegment ?: slot.name

                            FillerSoundManager.previous(context)
                            FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                            activeIndex = selectedIndex
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

                            val slot = currentSelectedSlot ?: return@IconButton
                            val targetIndex = selectedIndex ?: return@IconButton

                            FillerSoundPrefs.saveFillerFolder(context, slot.folderUri!!)
                            fillerUri = slot.folderUri
                            fillerName = slot.folderUri!!.lastPathSegment ?: slot.name

                            if (!isEnabled) {
                                isEnabled = true
                                FillerSoundPrefs.setEnabled(context, true)
                            }

                            val isPlayingThis =
                                FillerSoundManager.isPlaying() && activeIndex == targetIndex

                            if (!isPlayingThis) {
                                isStarting = true
                                activeIndex = targetIndex

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
                                activeIndex = null
                            }
                        },
                        enabled = canControlSelected,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(80.dp)
                    ) {
                        val showPause =
                            FillerSoundManager.isPlaying() && activeIndex == selectedIndex

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
                            val slot = currentSelectedSlot!!
                            FillerSoundPrefs.saveFillerFolder(context, slot.folderUri!!)
                            fillerUri = slot.folderUri
                            fillerName = slot.folderUri!!.lastPathSegment ?: slot.name

                            FillerSoundManager.next(context)
                            FillerSoundManager.setVolume(uiToRealVolume(uiFillerVolume))
                            activeIndex = selectedIndex
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

                if (libraryRoot == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.filler_library_missing),
                        color = sub,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.filler_quick_ambiences),
            color = onBg,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))

        slots.forEachIndexed { index, slot ->
            val isActive = activeIndex == index
            val isSelected = selectedIndex == index

            val rowBg =
                when {
                    isActive -> Color(0x33FFC107)
                    isSelected -> Color(0x221E88E5)
                    else -> Color.Transparent
                }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(rowBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "📁",
                        fontSize = 12.sp,
                        color = if (slot.folderUri != null) accent else sub,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                if (isDjScanning) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.filler_dj_scan_wait),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@clickable
                                }

                                pendingSlotIndex = index

                                val djRoot = DjFolderPrefs.get(context)
                                val djCache = DjIndexCache.load(context).orEmpty()
                                val needScan =
                                    (djRoot == null) || djCache.isEmpty() || !DjFolderPrefs.isScanned(context)

                                if (needScan) {
                                    showDjInfoDialog = true
                                } else {
                                    folderPickSlotIndex = index
                                    showFolderPicker = true
                                }
                            }
                    )

                    Text(
                        text = slot.name,
                        fontSize = 11.sp,
                        color = when {
                            isActive -> accent
                            isSelected -> Color(0xFFB388FF)
                            else -> onBg
                        },
                        modifier = Modifier.clickable { selectedIndex = index }
                    )
                }

                Text(
                    text = "✎",
                    fontSize = 11.sp,
                    color = accent,
                    modifier = Modifier.clickable {
                        slotToRenameIndex = index
                        renameText = slot.name
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showDjInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDjInfoDialog = false },
            title = { Text(stringResource(R.string.filler_dj_required_title), color = onBg) },
            text = {
                Text(
                    stringResource(R.string.filler_dj_required_text),
                    color = sub,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDjInfoDialog = false
                    folderPickSlotIndex = pendingSlotIndex
                    pickDjFolderLauncher.launch(null)
                }) { Text(stringResource(R.string.filler_choose_dj), color = onBg) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDjInfoDialog = false
                    pendingSlotIndex = null
                }) { Text(stringResource(R.string.common_cancel), color = sub) }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ───────────────────────────────────────────────
    //  PICKER DOSSIER (SAF direct, navigation DocumentFile)
    // ───────────────────────────────────────────────
    if (showFolderPicker) {

        val djRootTree = normalizeToTreeUri(DjFolderPrefs.get(context))

        val rootDoc: DocumentFile? = remember(showFolderPicker, djRootTree) {
            djRootTree?.let { DocumentFile.fromTreeUri(context, it) }
        }

        LaunchedEffect(rootDoc) {
            android.util.Log.e("DJ_PICKER", "djRootTree=$djRootTree")
            android.util.Log.e("DJ_PICKER", "rootDoc=${rootDoc?.uri} name=${rootDoc?.name}")
            android.util.Log.e("DJ_PICKER", "childrenCount=${rootDoc?.listFiles()?.size ?: -1}")
        }

        var pickerDoc by remember(showFolderPicker, rootDoc) { mutableStateOf<DocumentFile?>(rootDoc) }
        var pickerStack by remember(showFolderPicker, rootDoc) { mutableStateOf<List<DocumentFile>>(emptyList()) }

        val pickerEntries: List<DocumentFile> = remember(pickerDoc) {
            val cur = pickerDoc ?: return@remember emptyList()
            cur.listFiles()
                .filter { it.isDirectory }
                .filter { !it.name.isNullOrBlank() }
                .filter { !(it.name ?: "").startsWith(".") }
                .sortedBy { (it.name ?: "").lowercase() }
        }

        AlertDialog(
            onDismissRequest = {
                showFolderPicker = false
                folderPickSlotIndex = null
                pickerDoc = rootDoc
                pickerStack = emptyList()
            },
            title = { Text(stringResource(R.string.filler_picker_title), color = onBg) },
            text = {
                if (djRootTree == null || rootDoc == null) {
                    Text(
                        stringResource(R.string.filler_dj_missing),
                        color = sub,
                        fontSize = 12.sp
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {

                        Text(
                            text = stringResource(
                                R.string.filler_current_folder,
                                pickerDoc?.name ?: "(racine)"
                            ),
                            color = sub,
                            fontSize = 11.sp
                        )

                        if (pickerStack.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.filler_back),
                                color = accent,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        val newStack = pickerStack.dropLast(1)
                                        val parent = newStack.lastOrNull() ?: rootDoc
                                        pickerStack = newStack
                                        pickerDoc = parent
                                    }
                            )
                        }

                        Text(
                            text = stringResource(R.string.filler_choose_this),
                            color = Color(0xFFB388FF),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    val idx = folderPickSlotIndex
                                    val chosenDoc = pickerDoc
                                    if (idx != null && chosenDoc != null && idx in slots.indices) {
                                        val chosenUri = chosenDoc.uri
                                        val newSlot = slots[idx].copy(folderUri = chosenUri)
                                        slots[idx] = newSlot
                                        AmbiancePrefs.saveSlot(context, newSlot)
                                        selectedIndex = idx

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.filler_folder_linked, newSlot.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    showFolderPicker = false
                                    folderPickSlotIndex = null
                                    pickerDoc = rootDoc
                                    pickerStack = emptyList()
                                }
                        )

                        Spacer(Modifier.height(6.dp))

                        if (pickerEntries.isEmpty()) {
                            Text(stringResource(R.string.filler_no_subfolder), color = sub, fontSize = 12.sp)
                        } else {
                            pickerEntries.forEach { f ->
                                val name = f.name ?: context.getString(R.string.filler_no_name)
                                Text(
                                    text = "📁 $name",
                                    color = onBg,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable {
                                            pickerDoc?.let { pickerStack = pickerStack + it }
                                            pickerDoc = f
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFolderPicker = false
                    folderPickSlotIndex = null
                    pickerDoc = rootDoc
                    pickerStack = emptyList()
                }) { Text(stringResource(R.string.common_close), color = onBg) }
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ───────────────────────────────────────────────
    //  DIALOG DE RENOMMAGE
    // ───────────────────────────────────────────────
    if (slotToRenameIndex != null) {
        AlertDialog(
            onDismissRequest = { slotToRenameIndex = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idx = slotToRenameIndex
                        if (idx != null && idx in slots.indices) {
                            val updated = slots[idx].copy(
                                name = renameText.ifBlank { slots[idx].name }
                            )
                            slots[idx] = updated
                            AmbiancePrefs.saveSlot(context, updated)
                        }
                        slotToRenameIndex = null
                    }
                ) { Text(stringResource(R.string.common_ok), color = onBg) }
            },
            dismissButton = {
                TextButton(onClick = { slotToRenameIndex = null }) {
                    Text(stringResource(R.string.common_cancel), color = sub)
                }
            },
            title = { Text(stringResource(R.string.filler_rename_title), color = onBg) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.filler_rename_label)) }
                )
            },
            containerColor = Color(0xFF222222)
        )
    }
}

/* ─────────────────────────────────────────────
   Stockage des 5 ambiances (nom + dossier)
   ───────────────────────────────────────────── */

private data class AmbianceSlot(
    val index: Int,
    val name: String,
    val folderUri: Uri?
)

private object AmbiancePrefs {
    private const val PREFS_NAME = "ambiance_prefs"

    private fun keyName(index: Int) = "ambiance_${index}_name"
    private fun keyUri(index: Int) = "ambiance_${index}_uri"

    fun loadSlots(context: Context, count: Int): List<AmbianceSlot> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (0 until count).map { i ->
            val defaultName = context.getString(R.string.ambiance_default, i + 1)
            val name = prefs.getString(keyName(i), defaultName) ?: defaultName
            val uriString = prefs.getString(keyUri(i), null)
            val uri = uriString?.let { Uri.parse(it) }
            AmbianceSlot(index = i, name = name, folderUri = uri)
        }
    }

    fun saveSlot(context: Context, slot: AmbianceSlot) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(keyName(slot.index), slot.name)
            .putString(keyUri(slot.index), slot.folderUri?.toString())
            .apply()
    }
}