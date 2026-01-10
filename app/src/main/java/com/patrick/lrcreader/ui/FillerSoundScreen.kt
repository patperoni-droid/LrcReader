package com.patrick.lrcreader.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.core.LibraryIndexCache
import kotlin.math.pow

@Composable
fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
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

    // ✅ Racine Music persistée par Bibliothèque
    val libraryRoot = remember { BackupFolderPrefs.get(context) }

    var isEnabled by remember { mutableStateOf(FillerSoundPrefs.isEnabled(context)) }
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerFolder(context)) }
    var fillerName by remember {
        mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné")
    }

    // ✅ Au premier affichage : si aucun dossier filler n’est défini, on prend Music de la Bibliothèque
    LaunchedEffect(Unit) {
        if (fillerUri == null && libraryRoot != null) {
            FillerSoundPrefs.saveFillerFolder(context, libraryRoot)
            fillerUri = libraryRoot
            fillerName = libraryRoot.lastPathSegment ?: "Music"
        }
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
    var uiFillerVolume by remember {
        mutableStateOf(realToUiVolume(initialReal))
    }

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
    // état de lecture pour le gros bouton Play/Pause
    var isPlaying by remember { mutableStateOf(false) }

// ✅ démarrage fiable (on lance directement en coroutine, pas via LaunchedEffect)
    val scope = rememberCoroutineScope()
    var isStarting by remember { mutableStateOf(false) }
    var startJob by remember { mutableStateOf<Job?>(null) }

    // ───────────── Sélection dossier (explorateur) ─────────────
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickSlotIndex by remember { mutableStateOf<Int?>(null) }

    // Dialog de renommage
    var slotToRenameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    // ─────────────────────────────────────────────────────────────
    //  FOND + LAYOUT
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
                            text = "BUS FOND SONORE",
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
                            text = "Activer le fond sonore",
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
                Text("Volume", color = sub, fontSize = 11.sp)

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
                    text = "${(realDisplay * 100).toInt()} %",
                    color = onBg,
                    fontSize = 11.sp
                )

                // petit texte d’attente
                if (isStarting) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Démarrage de l’ambiance...",
                        color = sub,
                        fontSize = 11.sp
                    )
                }

                // ───────── GROS BOUTONS DE TRANSPORT ─────────
                Spacer(Modifier.height(8.dp))

                val currentSelectedSlot =
                    selectedIndex?.let { idx -> slots.getOrNull(idx) }
                val canControlSelected =
                    isEnabled && currentSelectedSlot?.folderUri != null

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
                            contentDescription = "Précédent",
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

                            // mémorise le dossier choisi dans les prefs
                            FillerSoundPrefs.saveFillerFolder(context, slot.folderUri!!)
                            fillerUri = slot.folderUri
                            fillerName = slot.folderUri!!.lastPathSegment ?: slot.name

                            if (!isEnabled) {
                                isEnabled = true
                                FillerSoundPrefs.setEnabled(context, true)
                            }

                            val isPlayingThis = FillerSoundManager.isPlaying() && activeIndex == targetIndex

                            if (!isPlayingThis) {
                                // ✅ START (direct)
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
                                // ✅ STOP
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
                        val showPause = FillerSoundManager.isPlaying() && activeIndex == selectedIndex

                        Icon(
                            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play / Pause",
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
                            contentDescription = "Suivant",
                            tint = if (canControlSelected) onBg else sub,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // ✅ Petit rappel si la bibliothèque n’est pas configurée
                if (libraryRoot == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "⚠️ Va dans Bibliothèque → “Choisir dossier Music” (1 fois).",
                        color = sub,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ───────────────────────────────────────────────
        //  AMBIANCES RAPIDES (liste compacte)
        // ───────────────────────────────────────────────
        Spacer(Modifier.height(10.dp))

        Text(
            text = "Ambiances rapides",
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
                    // 📁 : ouvre notre picker explorateur
                    Text(
                        text = "📁",
                        fontSize = 12.sp,
                        color = if (slot.folderUri != null) accent else sub,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                folderPickSlotIndex = index
                                showFolderPicker = true
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

    // ───────────────────────────────────────────────
    //  PICKER DOSSIER (explorateur depuis index Bibliothèque)
    // ───────────────────────────────────────────────
    if (showFolderPicker) {

        // ✅ index chargé depuis le cache Bibliothèque (source unique)
        val allIndexForPicker = LibraryIndexCache.load(context).orEmpty()

        // ✅ point de départ = Music (racine bibliothèque)
        val root = libraryRoot

        // état navigation dans le picker
        var pickerFolderUri by remember { mutableStateOf<Uri?>(root) }
        var pickerStack by remember { mutableStateOf<List<Uri>>(emptyList()) }

        // enfants du dossier courant (uniquement dossiers)
        val pickerEntries = remember(allIndexForPicker, pickerFolderUri) {
            val cur = pickerFolderUri
            if (cur == null) emptyList()
            else {
                LibraryIndexCache.childrenOf(allIndexForPicker, cur)
                    .filter { it.isDirectory }
                    .filter { it.name.isNotBlank() }
                    .filter { !it.name.startsWith(".") }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showFolderPicker = false
                folderPickSlotIndex = null
                pickerFolderUri = root
                pickerStack = emptyList()
            },
            title = { Text("Choisir un dossier (dans Music)", color = onBg) },
            text = {
                if (root == null) {
                    Text(
                        "Bibliothèque non configurée.\nVa dans Bibliothèque → “Choisir dossier Music”.",
                        color = sub,
                        fontSize = 12.sp
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {

                        // ⬅️ Retour (si on est dans un sous-dossier)
                        if (pickerStack.isNotEmpty()) {
                            Text(
                                text = "⬅️ Retour",
                                color = accent,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        val newStack = pickerStack.dropLast(1)
                                        val parent = newStack.lastOrNull() ?: root
                                        pickerStack = newStack
                                        pickerFolderUri = parent
                                    }
                            )
                        }

                        // ✅ Choisir CE dossier (dossier courant)
                        Text(
                            text = "✅ Choisir ce dossier",
                            color = Color(0xFFB388FF),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    val idx = folderPickSlotIndex
                                    val chosen = pickerFolderUri
                                    if (idx != null && chosen != null && idx in slots.indices) {

                                        val newSlot = slots[idx].copy(folderUri = chosen)
                                        slots[idx] = newSlot
                                        AmbiancePrefs.saveSlot(context, newSlot)

                                        // ✅ on NE change PAS le global ici
                                        selectedIndex = idx

                                        Toast.makeText(
                                            context,
                                            "Dossier associé à \"${newSlot.name}\"",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    showFolderPicker = false
                                    folderPickSlotIndex = null
                                    pickerFolderUri = root
                                    pickerStack = emptyList()
                                }
                        )

                        Spacer(Modifier.height(6.dp))

                        if (pickerEntries.isEmpty()) {
                            Text("Aucun sous-dossier ici.", color = sub, fontSize = 12.sp)
                        } else {
                            pickerEntries.forEach { e ->
                                Text(
                                    text = "📁 ${e.name}",
                                    color = onBg,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable {
                                            val next = Uri.parse(e.uriString)
                                            pickerFolderUri?.let { pickerStack = pickerStack + it }
                                            pickerFolderUri = next
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
                    pickerFolderUri = root
                    pickerStack = emptyList()
                }) { Text("Fermer", color = onBg) }
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
                ) { Text("OK", color = onBg) }
            },
            dismissButton = {
                TextButton(onClick = { slotToRenameIndex = null }) {
                    Text("Annuler", color = sub)
                }
            },
            title = { Text("Renommer l’ambiance", color = onBg) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nom de l’ambiance") }
                )
            },
            containerColor = Color(0xFF222222)
        )
    }

    // ───────────────────────────────────────────────


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
            val name =
                prefs.getString(keyName(i), "Ambiance ${i + 1}") ?: "Ambiance ${i + 1}"
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