package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.patrick.lrcreader.core.PadsBusController
import com.patrick.lrcreader.exo.BuildConfig
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.exo.R
import kotlin.math.cbrt
import com.patrick.lrcreader.core.PlayerBusController


/**
 * Écran de mixage global :
 * - Lecteur, DJ, Fond sonore, plus Pads dans la V1 debug.
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
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val padsUiLevel by PadsBusController.uiLevel.collectAsState()
    val padsBusReady by PadsBusController.ready.collectAsState()
    LaunchedEffect(Unit) { if (BuildConfig.DEBUG || BuildConfig.FLAVOR == "labo") PadsBusController.initialize(context) }

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
    LaunchedEffect(djLevel) {
        uiDjVolume = realToUiVolume(djLevel)
    }
    // ---------- LECTEUR : on suppose que playerLevel est le volume RÉEL (0..1) ----------
    var uiPlayerVolume by remember {
        mutableStateOf(realToUiVolume(playerLevel))
    }
    LaunchedEffect(playerLevel) {
        uiPlayerVolume = realToUiVolume(playerLevel)
    }

    val cardColor = Color(0xFF141414)
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

    DarkBlueGradientBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .then(if (BuildConfig.DEBUG || BuildConfig.FLAVOR == "labo") Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // HEADER
            if (showBackButton) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.common_back_arrow), color = onBg)
                }

                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = stringResource(R.string.global_mix_title),
                color = onBg,
                fontSize = 20.sp
            )

            Text(
                text = stringResource(if (BuildConfig.DEBUG || BuildConfig.FLAVOR == "labo") R.string.global_mix_subtitle_pads else R.string.global_mix_subtitle),
                color = sub,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // CARTES DES FADERS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, shape = RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // --- FADER LECTEUR ---
                MixFader(
                    title = stringResource(R.string.global_mix_player_title),
                    subtitle = stringResource(R.string.global_mix_player_subtitle),
                    value = uiPlayerVolume,
                    onValueChange = { v ->
                        val ui = v.coerceIn(0f, 1f)
                        uiPlayerVolume = ui

                        val real = uiToRealVolume(ui) // ✅ courbe douce (cubique)
                        android.util.Log.d("BUS", "UI Lecteur=$ui -> REAL Lecteur=$real")
                        onPlayerLevelChange(real) // ✅ le parent doit appliquer à AudioEngine.setPlayerBusLevel(real)
                    }
                )

                // --- FADER DJ (lié au niveau DJ global) ---
                MixFader(
                    title = stringResource(R.string.global_mix_dj_title),
                    subtitle = stringResource(R.string.global_mix_dj_subtitle),
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
                    title = stringResource(R.string.global_mix_filler_title),
                    subtitle = stringResource(R.string.global_mix_filler_subtitle),
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
                if (BuildConfig.DEBUG || BuildConfig.FLAVOR == "labo") {
                    MixFader(
                        title = stringResource(R.string.soundpads_title_short),
                        subtitle = stringResource(R.string.mixer_channel_pads_subtitle),
                        value = padsUiLevel,
                        enabled = padsBusReady,
                        modifier = Modifier.testTag("pads-globalmix-fader").semantics {
                            contentDescription = context.getString(R.string.soundpads_bus_volume)
                        },
                        onValueChange = { PadsBusController.setUiLevel(context, it) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.global_mix_tip),
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
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val onBg = Color(0xFFEEEEEE)
    val sub = Color(0xFFB9B9B9)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = onBg, fontSize = 14.sp)
        Text(subtitle, color = sub, fontSize = 11.sp)

        Spacer(Modifier.height(6.dp))

        Slider(
            value = value,
            enabled = enabled,
            onValueChange = { v -> onValueChange(v.coerceIn(0f, 1f)) },
            valueRange = 0f..1f,
            modifier = modifier.fillMaxWidth()
        )

        val percent = (value * 100).toInt()
        Text(stringResource(R.string.common_percent_value, percent), color = onBg, fontSize = 11.sp)
    }
}
