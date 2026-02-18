package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.MidiOutput
import android.content.Context
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.patrick.lrcreader.core.AutoReturnPrefs
import com.patrick.lrcreader.core.BackupManager

/* ─────────────────────────────
   Écran "Plus" (Paramètres)
   ───────────────────────────── */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: (BackupManager.LastPlayed?) -> Unit = {},
    onOpenTuner: () -> Unit = {}     // callback pour l'accordeur
) {
    var current by remember { mutableStateOf(MoreSection.Root) }
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
            context = context,
            onBack = { navigate("root") }
        )

        MoreSection.WaveformPreview -> WaveformPreviewScreen(
            modifier = modifier,
            onBack = { navigate("root") }
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
    onOpenTuner: () -> Unit
) {
    val context = LocalContext.current
    // ✅ Séquence de Program Change pour le test MIDI
    val testProgramChanges = listOf(8, 39, 58, 127)
    var testPcIndex by remember { mutableStateOf(0) }


    // État du switch "retour auto"
    var autoReturnEnabled by remember {
        mutableStateOf(AutoReturnPrefs.isEnabled(context))
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
                            text = "PARAMÈTRES",
                            color = Color(0xFFFFECB3),
                            fontSize = 15.sp,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Bloc fonctionnel
                    SettingsHeader("Fonctions")
                    SettingsItem("🎧  Fond sonore", onClick = onOpenFiller)
                    SettingsItem("💾  Sauvegarde / Restauration", onClick = onOpenBackup)
                    SettingsItem("🕘  Historique", onClick = onOpenHistory)

                    HorizontalDivider(color = Color(0xFF262626))

                    // Bloc interface / audio
                    SettingsHeader("Audio & Interface")
                    SettingsItem("🛠  Édition", onClick = onOpenWaveformPreview)
                    SettingsItem("🔊  Audio", onClick = {})

                    // Accordeur, dans le bloc Audio
                    SettingsItem("🎸  Accordeur", onClick = onOpenTuner)

                    // 🔁 Retour auto vers la playlist (ON/OFF)
                    SwitchSettingItem(
                        label = "Retour auto vers la playlist (10 s avant la fin)",
                        checked = autoReturnEnabled,
                        onCheckedChange = { enabled ->
                            autoReturnEnabled = enabled
                            AutoReturnPrefs.setEnabled(context, enabled)
                        }
                    )

                    HorizontalDivider(color = Color(0xFF262626))

                    SettingsHeader("Avancé")
                    SettingsItem("⚙️  Avancé", onClick = {})
                    SettingsItem("📶  Test Bluetooth MIDI", onClick = {
                        MidiOutput.init(context)

                        val pc = testProgramChanges[testPcIndex]
                        MidiOutput.sendProgramChange(channel = 1, program = pc)

                        testPcIndex = (testPcIndex + 1) % testProgramChanges.size
                    })
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Live in Pocket · LrcReader_EXO",
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
private fun SettingsItem(label: String, onClick: () -> Unit) {
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
