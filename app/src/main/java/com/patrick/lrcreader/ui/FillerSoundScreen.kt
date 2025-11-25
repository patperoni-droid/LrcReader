/**
 * Écran : FillerSoundScreen
 *
 * Rôle :
 *  - Configurer le fond sonore (dossier + volume)
 *  - Gérer 5 “ambiances rapides” (nom + dossier)
 *  - Piloter l’ambiance sélectionnée avec 3 gros boutons ⏮ ▶⏸ ⏭
 */
package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import kotlin.math.cbrt

@Composable
fun FillerSoundScreen(
    context: Context,
    onBack: () -> Unit
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)
    val card = Color(0xFF141414)
    val accent = Color(0xFFE040FB)

    var isEnabled by remember { mutableStateOf(FillerSoundPrefs.isEnabled(context)) }
    var fillerUri by remember { mutableStateOf(FillerSoundPrefs.getFillerFolder(context)) }
    var fillerName by remember {
        mutableStateOf(fillerUri?.lastPathSegment ?: "Aucun son sélectionné")
    }

    // mapping courbe : curseur “doux” en bas
    fun uiToRealVolume(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        return clamped * clamped * clamped // u³
    }

    fun realToUiVolume(r: Float): Float {
        val clamped = r.coerceIn(0f, 1f)
        return cbrt(clamped.toDouble()).toFloat() // racine cubique
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

    // ambiance en cours de lecture (pour colorer en violet)
    var activeIndex by remember { mutableStateOf<Int?>(null) }

    // ambiance sélectionnée (pilotée par les gros boutons)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // index du slot pour lequel on choisit un dossier
    var pendingSlotIndex by remember { mutableStateOf<Int?>(null) }

    // état de lecture pour le gros bouton Play/Pause
    var isPlaying by remember { mutableStateOf(false) }

    // indicateurs pour le démarrage asynchrone
    var isStarting by remember { mutableStateOf(false) }
    var shouldStart by remember { mutableStateOf(false) }
    var startTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Sélecteur de dossier pour un SLOT d’ambiance
    val slotFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val index = pendingSlotIndex
        if (uri != null && index != null && index in slots.indices) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            val newSlot = slots[index].copy(folderUri = uri)
            slots[index] = newSlot
            AmbiancePrefs.saveSlot(context, newSlot)

            // on met aussi ce dossier comme "global" (affichage)
            FillerSoundPrefs.saveFillerFolder(context, uri)
            fillerUri = uri
            fillerName = uri.lastPathSegment ?: newSlot.name

            Toast.makeText(
                context,
                "Dossier associé à \"${newSlot.name}\"",
                Toast.LENGTH_SHORT
            ).show()

            // auto-sélection du slot quand on revient
            selectedIndex = index
        }
        pendingSlotIndex = null
    }

    // Dialog de renommage
    var slotToRenameIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    DarkBlueGradientBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 8.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER
            TextButton(onClick = onBack) {
                Text("← Retour", color = onBg)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Fond sonore",
                color = onBg,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(10.dp))

            // ───────── CARTE PRINCIPALE (réglages + gros boutons) ─────────
            Card(
                colors = CardDefaults.cardColors(containerColor = card)
            ) {
                Column(Modifier.padding(12.dp)) {

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
                            Text(
                                text = "Lecture automatique après la fin d’un morceau.",
                                color = sub,
                                fontSize = 12.sp
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
                                    shouldStart = false
                                    startTargetIndex = null
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
                        modifier = Modifier.fillMaxWidth()
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
                                fillerName =
                                    slot.folderUri.lastPathSegment ?: slot.name

                                FillerSoundManager.previous(context)
                                FillerSoundManager.setVolume(
                                    uiToRealVolume(uiFillerVolume)
                                )
                                activeIndex = selectedIndex
                                isPlaying = true
                                isStarting = false
                                shouldStart = false
                                startTargetIndex = null
                            },
                            enabled = canControlSelected,
                            modifier = Modifier.size(72.dp)  // GROS bouton
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Précédent",
                                tint = if (canControlSelected) onBg else sub,
                                modifier = Modifier.size(40.dp) // Grosse icône
                            )
                        }

                        // PLAY / PAUSE
                        // PLAY / PAUSE
                        IconButton(
                            onClick = {
                                if (!canControlSelected) return@IconButton

                                val slot = currentSelectedSlot ?: return@IconButton
                                val targetIndex = selectedIndex ?: return@IconButton

                                // on mémorise le dossier choisi dans les prefs
                                FillerSoundPrefs.saveFillerFolder(context, slot.folderUri!!)
                                fillerUri = slot.folderUri
                                fillerName = slot.folderUri.lastPathSegment ?: slot.name

                                if (!isEnabled) {
                                    isEnabled = true
                                    FillerSoundPrefs.setEnabled(context, true)
                                }

                                // Est-ce que C’EST cette ambiance-là qui joue actuellement ?
                                val isPlayingThis =
                                    FillerSoundManager.isPlaying() && activeIndex == targetIndex

                                if (!isPlayingThis) {
                                    // ➜ DÉMARRER
                                    isStarting = true
                                    startTargetIndex = targetIndex
                                    shouldStart = true
                                } else {
                                    // ➜ STOPPER
                                    FillerSoundManager.fadeOutAndStop(200)
                                    isPlaying = false
                                    isStarting = false
                                    shouldStart = false
                                    startTargetIndex = null
                                    activeIndex = null
                                }
                            },
                            enabled = canControlSelected,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(80.dp) // Play encore plus gros
                        ) {
                            Icon(
                                imageVector = if (FillerSoundManager.isPlaying() && activeIndex == selectedIndex)
                                    Icons.Filled.Pause
                                else
                                    Icons.Filled.PlayArrow,
                                contentDescription = "Play / Pause",
                                tint = if (canControlSelected) onBg else sub,
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
                                fillerName =
                                    slot.folderUri.lastPathSegment ?: slot.name

                                FillerSoundManager.next(context)
                                FillerSoundManager.setVolume(
                                    uiToRealVolume(uiFillerVolume)
                                )
                                activeIndex = selectedIndex
                                isPlaying = true
                                isStarting = false
                                shouldStart = false
                                startTargetIndex = null
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
                }
            }

            // ───────────────────────────────────────────────
            //  AMBIANCES RAPIDES (liste compacte)
            // ───────────────────────────────────────────────
            Spacer(Modifier.height(6.dp))

            Text(
                text = "Ambiances rapides",
                color = onBg,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "5 ambiances personnalisables.",
                color = sub,
                fontSize = 10.sp
            )

            Spacer(Modifier.height(4.dp))

            slots.forEachIndexed { index, slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bloc gauche : icône dossier + nom
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 📁 icône cliquable pour choisir le dossier
                        Text(
                            text = "📁",
                            fontSize = 12.sp,
                            color = if (slot.folderUri != null) accent else sub,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable {
                                    pendingSlotIndex = index
                                    slotFolderLauncher.launch(null)
                                }
                        )

                        // Nom ambiance : clic = sélection pour les gros boutons
                        Text(
                            text = slot.name,
                            fontSize = 11.sp,
                            color = when {
                                activeIndex == index -> accent
                                selectedIndex == index -> Color(0xFFB388FF)
                                else -> onBg
                            },
                            modifier = Modifier.clickable {
                                selectedIndex = index
                            }
                        )
                    }

                    // Bloc droit : ✎ rename
                    Text(
                        text = "✎",
                        fontSize = 11.sp,
                        color = accent,
                        modifier = Modifier
                            .clickable {
                                slotToRenameIndex = index
                                renameText = slot.name
                            }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
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
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { slotToRenameIndex = null }) {
                        Text("Annuler")
                    }
                },
                title = { Text("Renommer l’ambiance") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Nom de l’ambiance") }
                    )
                }
            )
        }
    }

    // ───────────────────────────────────────────────
    //  LANCEMENT RÉEL DU FILLER APRÈS RECOMPOSITION
    // ───────────────────────────────────────────────
    LaunchedEffect(shouldStart) {
        if (shouldStart) {
            val targetIndex = startTargetIndex
            if (targetIndex != null) {
                FillerSoundManager.startIfConfigured(context)
                FillerSoundManager.setVolume(
                    uiToRealVolume(uiFillerVolume)
                )
                // on marque l’ambiance comme active
                activeIndex = targetIndex
                isPlaying = FillerSoundManager.isPlaying()
            }
            isStarting = false
            shouldStart = false
        }
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