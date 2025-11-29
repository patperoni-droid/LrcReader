package com.patrick.lrcreader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.FillerSoundPrefs
import com.patrick.lrcreader.core.PlayerBusController
import com.patrick.lrcreader.core.PlayerVolumePrefs
import com.patrick.lrcreader.core.DjBusController
import kotlinx.coroutines.launch

/**
 * Maquette "console analogique"
 * - Faders réalistes
 * - Bouton accordeur SUPPRIMÉ
 * - Textes réduits (pas de retour à la ligne)
 */
@Composable
fun MixerHomePreviewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenFondSonore: () -> Unit = {},
    onOpenDj: () -> Unit = {},
    onOpenTuner: () -> Unit = {} // conservé mais non utilisé
) {

    val context = LocalContext.current

    // Affichage du bloc-notes par-dessus le BUS PRINCIPAL
    var showNotes by remember { mutableStateOf(false) }

    // === mêmes courbes que dans FillerSoundScreen =========================

    fun uiToRealVolume(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        return clamped * clamped * clamped // u³
    }

    fun realToUiVolume(r: Float): Float {
        val clamped = r.coerceIn(0f, 1f)
        // racine cubique
        return kotlin.math.cbrt(clamped.toDouble()).toFloat()
    }

    // Volume réel stocké dans les prefs (0..1)
    val initialRealFond = FillerSoundPrefs.getFillerVolume(context)
    // Volume "UI" (0..1) pour le fader FOND
    val fondInitialUi = realToUiVolume(initialRealFond)

    // Volume LECTEUR vient aussi des prefs (0..1 UI)
    val lecteurInitialUi = PlayerVolumePrefs.load(context).coerceIn(0f, 1f)

    // DJ bus : on lit le niveau centralisé (même source que l'écran DJ)
    val djInitialUi = DjBusController.getUiLevel().coerceIn(0f, 1f)

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

        Column(modifier = Modifier.fillMaxSize()) {

            // ---- BARRE DU HAUT ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color(0xFFEEEEEE)
                    )
                }
                Spacer(Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Live in Pocket",
                        color = Color(0xFFFFE082),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Console Studio",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                // Icône existante (EQ) – décorative
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(Modifier.width(4.dp))

                // Nouvelle icône : bloc-notes
                IconButton(onClick = { showNotes = true }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Bloc-notes",
                        tint = Color(0xFFFFF59D)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- PANNEAU PRINCIPAL ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {

                    // Bandeau BUS PRINCIPAL
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
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
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BUS PRINCIPAL",
                            color = Color(0xFFFFECB3),
                            fontSize = 13.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // ---- LES 3 TRANCHES ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // LECTEUR = branché sur PlayerBusController
                        MixerChannelColumn(
                            label = "LECTEUR",
                            subtitle = "Playlists",
                            icon = Icons.Filled.MusicNote,
                            faderColor = Color(0xFF81C784),
                            meterColor = Color(0xFF66BB6A),
                            onClick = onOpenPlayer,
                            initialLevel = lecteurInitialUi
                        ) { uiLevel ->
                            // 🔊 Bus principal -> bus lecteur (prefs + MediaPlayer attaché)
                            PlayerBusController.setUiLevelFromBusUi(context, uiLevel)
                        }

                        // FOND = 🔥 branché sur FillerSound (sécurisé)
                        MixerChannelColumn(
                            label = "FOND",
                            subtitle = "Ambiance",
                            icon = Icons.Filled.LibraryMusic,
                            faderColor = Color(0xFFFFC107),
                            meterColor = Color(0xFFFFA000),
                            onClick = onOpenFondSonore,
                            initialLevel = fondInitialUi
                        ) { uiLevel ->
                            try {
                                val real = uiToRealVolume(uiLevel)
                                FillerSoundPrefs.saveFillerVolume(context, real)
                                FillerSoundManager.setVolume(real)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // DJ → FADER RELIÉ AU MASTER DJ via DjBusController
                        MixerChannelColumn(
                            label = "DJ",
                            subtitle = "Bus DJ",
                            icon = Icons.Filled.Headphones,
                            faderColor = Color(0xFF64B5F6),
                            meterColor = Color(0xFF42A5F5),
                            onClick = onOpenDj,
                            initialLevel = djInitialUi
                        ) { uiLevel ->
                            DjBusController.setUiLevel(uiLevel)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bandeau bas style rack
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3E2723),
                                Color(0xFF212121),
                                Color(0xFF3E2723)
                            )
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
        }

        // ─────────────────────────────
        //  OVERLAY : BLOC-NOTES
        // ─────────────────────────────
        if (showNotes) {
            NotesScreen(
                modifier = Modifier.fillMaxSize(),
                context = context,
                onClose = { showNotes = false }
            )
        }
    }
}

/**
 * Une tranche : vu-mètre + long fader + bouton.
 * onLevelChange est appelé pour tous les canaux (LECTEUR, FOND, DJ…),
 * mais pour l’instant on ne l’exploite que pour LECTEUR, FOND, DJ.
 */
@Composable
private fun MixerChannelColumn(
    label: String,
    subtitle: String,
    icon: ImageVector,
    faderColor: Color,
    meterColor: Color,
    onClick: () -> Unit,
    initialLevel: Float = 0.75f,
    onLevelChange: (Float) -> Unit = {}
) {

    val scope = rememberCoroutineScope()

    // IMPORTANT : lié à initialLevel pour pouvoir se resynchroniser
    var level by remember(initialLevel) { mutableFloatStateOf(initialLevel.coerceIn(0f, 1f)) }

    // Animation VU
    val infinite = rememberInfiniteTransition(label)
    val vuAnim by infinite.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1400 + (0..600).random(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Titres — police réduite
        Text(
            text = label,
            color = Color(0xFFFFF3E0),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color(0xFFB0BEC5),
            fontSize = 9.sp
        )

        Spacer(Modifier.height(10.dp))

        // VU-mètre
        Box(
            modifier = Modifier
                .height(90.dp)
                .width(28.dp)
                .background(Color(0xFF050505), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(3.dp),
            contentAlignment = Alignment.BottomCenter
        ) {

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(10) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                if (index >= 7) Color(0x55FF5252)
                                else Color(0x33555555),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(vuAnim.coerceIn(0f, 1f))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                meterColor.copy(alpha = 0.1f),
                                meterColor,
                                Color(0xFFFF5252)
                            )
                        ),
                        RoundedCornerShape(6.dp)
                    )
            )
        }

        Spacer(Modifier.height(16.dp))

        // FADER ANALOGIQUE — VERSION LONGUE (doux au toucher)
        val dragRangePx = 720f   // grande course => plus progressif

        Box(
            modifier = Modifier
                .height(310.dp)
                .width(40.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        // delta > 0 = doigt vers le bas => on baisse le niveau
                        val fraction = -delta / dragRangePx
                        val newLevel = (level + fraction).coerceIn(0f, 1f)
                        level = newLevel

                        // On remonte la valeur pour que le parent fasse ce qu’il veut
                        onLevelChange(newLevel)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {

            // Gorge
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF050608), Color(0xFF15171B))
                        ),
                        RoundedCornerShape(999.dp)
                    )
            )

            // Curseur (avec fix pour éviter weight(0f))
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val clamped = level.coerceIn(0f, 1f)

                val topWeight = (1f - clamped).coerceAtLeast(0.0001f)
                val bottomWeight = clamped.coerceAtLeast(0.0001f)

                Spacer(Modifier.weight(topWeight))

                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFE0E0E0),
                                    Color(0xFFBDBDBD)
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(faderColor, RoundedCornerShape(999.dp))
                            .align(Alignment.Center)
                    )
                }

                Spacer(Modifier.weight(bottomWeight))
            }
        }

        Spacer(Modifier.height(12.dp))

        // BOUTON (ICÔNE SEULE)
        Card(
            onClick = { scope.launch { onClick() } },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2725)),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = faderColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}