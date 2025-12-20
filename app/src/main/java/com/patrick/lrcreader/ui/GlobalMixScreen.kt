package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import kotlin.math.cbrt

/**
 * Écran de mixage global :
 * - 3 faders : Lecteur, DJ, Fond sonore.
 * - Les volumes sont liés aux écrans correspondants.
 */
@Composable
fun GlobalMixScreen(
    modifier: Modifier = Modifier,
    playerLevel: Float,
    onPlayerLevelChange: (Float) -> Unit,
    djLevel: Float,                     // niveau DJ « réel » (0..1) venant du parent
    onDjLevelChange: (Float) -> Unit,   // on renvoie un niveau « réel » (0..1)
    fillerLevel: Float,                 // pas utilisé directement (on passe par les prefs)
    onFillerLevelChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    //---------------------------------------------------------
    // MAPPING doux (même logique que dans FillerSoundScreen)
    //---------------------------------------------------------
    fun uiToRealVolume(u: Float): Float {
        val c = u.coerceIn(0f, 1f)
        return c * c * c   // courbe douce : petit déplacement → petit changement
    }

    fun realToUiVolume(r: Float): Float {
        val c = r.coerceIn(0f, 1f)
        return cbrt(c.toDouble()).toFloat()
    }

    // ---------- FILLER : on lit la valeur réelle depuis les prefs ----------
    var uiFillerVolume by remember {
        mutableStateOf(realToUiVolume(FillerSoundPrefs.getFillerVolume(context)))
    }

    // ---------- DJ : on suppose que djLevel est le volume RéEL (0..1) ----------
    var uiDjVolume by remember {
        mutableStateOf(realToUiVolume(djLevel))
    }

    val cardColor = Color(0xFF141414)
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // HEADER
            TextButton(onClick = onBack) {
                Text("← Retour", color = onBg)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Mixage général",
                color = onBg,
                fontSize = 20.sp
            )

            Text(
                text = "Réglage des niveaux : Lecteur, DJ, Fond sonore.",
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // CARTES DES 3 FADERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, shape = RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // --- FADER LECTEUR ---
                MixFader(
                    title = "Lecteur (playback)",
                    subtitle = "Paroles + playback principal",
                    value = playerLevel,
                    onValueChange = { v ->
                        val safe = v.coerceIn(0f, 1f)
                        android.util.Log.d("BUS", "UI slider Lecteur = $safe")
                        onPlayerLevelChange(safe)
                    }

                )

                // --- FADER DJ (lié au niveau DJ global) ---
                MixFader(
                    title = "Mode DJ",
                    subtitle = "Crossfade, playlists DJ",
                    value = uiDjVolume,
                    onValueChange = { v ->
                        val ui = v.coerceIn(0f, 1f)
                        uiDjVolume = ui

                        // niveau « réel » (0..1) pour le moteur DJ
                        val real = uiToRealVolume(ui)
                        onDjLevelChange(real)  // tu appliques ce volume dans ton écran DJ
                    }
                )

                // --- FADER FOND SONORE (lié au FillerSoundManager) ---
                MixFader(
                    title = "Fond sonore",
                    subtitle = "Nappe entre les morceaux",
                    value = uiFillerVolume,
                    onValueChange = { v ->
                        val ui = v.coerceIn(0f, 1f)
                        uiFillerVolume = ui
                        onFillerLevelChange(ui) // si tu veux garder une copie dans ton ViewModel

                        val real = uiToRealVolume(ui)
                        // 1) on enregistre
                        FillerSoundPrefs.saveFillerVolume(context, real)
                        // 2) on applique immédiatement au lecteur de fond sonore
                        FillerSoundManager.setVolume(real)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Astuce : commence vers 70–80% partout, puis ajuste en live.",
                color = sub,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Petit bloc réutilisable : titre + sous-titre + slider + pourcentage.
 */
@Composable
private fun MixFader(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = onBg, fontSize = 14.sp)
        Text(subtitle, color = sub, fontSize = 11.sp)

        Spacer(Modifier.height(6.dp))

        Slider(
            value = value,
            onValueChange = { v -> onValueChange(v.coerceIn(0f, 1f)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )

        val percent = (value * 100).toInt()
        Text("$percent %", color = onBg, fontSize = 11.sp)
    }
}