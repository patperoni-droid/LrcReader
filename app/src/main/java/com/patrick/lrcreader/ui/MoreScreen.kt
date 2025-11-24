package com.patrick.lrcreader.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

/* ─────────────────────────────
   Écran "Plus" (Paramètres)
   ───────────────────────────── */
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    context: Context,
    onAfterImport: () -> Unit = {}
) {
    var current by remember { mutableStateOf(MoreSection.Root) }

    when (current) {
        MoreSection.Root -> MoreRootScreen(
            modifier = modifier,
            onOpenBackup = { current = MoreSection.Backup },
            onOpenFiller = { current = MoreSection.Filler },
            onOpenEdit = { current = MoreSection.Edit }
        )

        MoreSection.Backup -> BackupScreen(
            context = context,
            onAfterImport = onAfterImport,
            onBack = { current = MoreSection.Root }
        )

        MoreSection.Filler -> FillerSoundScreen(
            context = context,
            onBack = { current = MoreSection.Root }
        )

        MoreSection.Edit -> EditSoundScreen(
            context = context,
            onBack = { current = MoreSection.Root }
        )
    }
}

private enum class MoreSection { Root, Backup, Filler, Edit }

/* ─────────────────────────────
   Menu principal
   ───────────────────────────── */

@Composable
private fun MoreRootScreen(
    modifier: Modifier = Modifier,
    onOpenBackup: () -> Unit,
    onOpenFiller: () -> Unit,
    onOpenEdit: () -> Unit
) {
    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    top = 16.dp,
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 8.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Paramètres",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(4.dp)
            )
            Spacer(Modifier.height(10.dp))

            SettingsItem("🎧  Fond sonore", onClick = onOpenFiller)
            SettingsItem("💾  Sauvegarde / Restauration", onClick = onOpenBackup)
            SettingsItem("🛠  Édition de titre", onClick = onOpenEdit)

            HorizontalDivider(color = Color(0xFF1E1E1E))
            SettingsItem("🎨  Interface", onClick = {})
            SettingsItem("🔊  Audio", onClick = {})
            SettingsItem("⚙️  Avancé", onClick = {})

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "LrcReader_EXO",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsItem(label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 15.sp)
    }
    HorizontalDivider(color = Color(0xFF1E1E1E))
}