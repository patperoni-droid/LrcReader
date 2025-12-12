package com.patrick.lrcreader.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.patrick.lrcreader.core.TunerEngine
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Accordeur "pro scène" (sobre + lisible), branché sur TunerEngine.
 * Look plus classe : 1 accent couleur, jauge horizontale, moins de dégradés.
 */
@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // --- Permission micro ---
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasMicPermission = granted }
    )

    // --- État de l'accordeur (TunerEngine) ---
    val tunerState by TunerEngine.state.collectAsState()

    // Démarrage / arrêt du moteur quand l’écran est visible
    DisposableEffect(hasMicPermission) {
        if (hasMicPermission) TunerEngine.start()
        onDispose { TunerEngine.stop() }
    }

    // Fond Live in Pocket (dark)
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            TunerUi.BgTop,
            TunerUi.BgMid,
            TunerUi.BgBottom
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ───── BARRE DU HAUT ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = TunerUi.TextMain
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Accordeur",
                        color = TunerUi.TextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mode scène",
                        color = TunerUi.TextSoft,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = TunerUi.Accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ───── MODULE PRINCIPAL ─────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = TunerUi.Surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                val centsOffset: Float? = tunerState.cents?.toFloat()
                val currentNote = tunerState.noteName
                val currentFreqText = tunerState.frequency?.let { "${it.toInt()} Hz" } ?: "—"

                val absOffset = centsOffset?.let { abs(it) } ?: 999f
                val isOk = centsOffset != null && absOffset < 5f

                val noteColor = if (isOk) TunerUi.Accent else TunerUi.TextMain

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Bandeau "module" (sobre)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(
                                color = TunerUi.Surface2,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0x22FFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TUNER",
                            color = TunerUi.TextSoft,
                            fontSize = 12.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // ───── ZONE CENTRALE : NOTE + METER ─────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF121212),
                                        Color(0xFF0C0C0C)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0x22FFFFFF),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // NOTE (grosse, premium, lisible)
                            Text(
                                text = currentNote,
                                color = noteColor,
                                fontSize = 88.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-2).sp
                            )

                            // Jauge horizontale pro
                            TunerMeterPro(
                                centsOffset = centsOffset,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Infos secondaires (sobres)
                            Text(
                                text = currentFreqText,
                                color = TunerUi.TextSoft,
                                fontSize = 14.sp
                            )

                            val centsText = centsOffset?.let { "${it.roundToInt()} cents" } ?: "— cents"
                            val centsColor = when {
                                centsOffset == null -> TunerUi.TextSoft
                                absOffset < 5f -> TunerUi.Accent
                                absOffset < 15f -> TunerUi.TextSoft
                                else -> TunerUi.Bad
                            }

                            Text(
                                text = centsText,
                                color = centsColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ───── BARRE DE CONTROLE / MIC / PERMISSION ─────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = if (hasMicPermission && tunerState.isListening)
                                    TunerUi.Accent
                                else
                                    TunerUi.TextDim,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (!hasMicPermission)
                                    "Micro non autorisé"
                                else if (!tunerState.isListening)
                                    "En attente du signal…"
                                else
                                    "Entrée micro active",
                                color = TunerUi.TextSoft,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Niveau d'entrée (vu simple)
                        val level = tunerState.inputLevel.coerceIn(0f, 1f)
                        Text(
                            text = "Niveau d’entrée : ${(level * 100).toInt()} %",
                            color = TunerUi.TextDim,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = level,
                            onValueChange = { /* read only */ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            enabled = false
                        )

                        Spacer(Modifier.height(4.dp))

                        if (!hasMicPermission) {
                            TextButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            ) {
                                Text(
                                    text = "Autoriser le micro",
                                    color = TunerUi.Accent,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Joue une note près du micro.",
                                color = TunerUi.TextDim,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bandeau bas type rack (sobre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1A1A1A),
                                Color(0xFF0E0E0E),
                                Color(0xFF1A1A1A)
                            )
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
        }
    }
}

private object TunerUi {
    /**
     * ⚠️ Mets ici LA couleur accent de Live in Pocket (rouge, jaune, etc.)
     * (j’ai laissé ton jaune actuel par défaut)
     */
    val Accent = Color(0xFFFFC107)

    val BgTop = Color(0xFF171717)
    val BgMid = Color(0xFF101010)
    val BgBottom = Color(0xFF181410)

    val Surface = Color(0xFF1B1B1B)
    val Surface2 = Color(0xFF141414)

    val TextMain = Color(0xFFEEEEEE)
    val TextSoft = Color(0xFFB0BEC5)
    val TextDim = Color(0xFF757575)

    val Bad = Color(0xFFFF5252)
}

/**
 * Jauge d'accordage horizontale "pro scène"
 * - plage : -50..+50 cents
 * - centre = 0 cents
 */
@Composable
private fun TunerMeterPro(
    centsOffset: Float?,
    modifier: Modifier = Modifier
) {
    val clamped = (centsOffset ?: 0f).coerceIn(-50f, 50f)
    val target = (clamped / 50f) // -1..+1

    val pos by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 120),
        label = "tunerMeterPos"
    )

    val absOffset = abs(clamped)
    val isOk = centsOffset != null && absOffset < 5f
    val pointerColor = if (isOk) TunerUi.Accent else TunerUi.TextSoft.copy(alpha = 0.9f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(TunerUi.Surface2, RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midX = w / 2f
                val midY = h / 2f

                // Rail
                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 6f
                )

                // Centre (0)
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(midX, midY - 18f),
                    end = Offset(midX, midY + 18f),
                    strokeWidth = 5f
                )

                // Graduations
                val ticks = 10
                for (i in 1..ticks) {
                    val xL = midX - (w * 0.45f) * (i / ticks.toFloat())
                    val xR = midX + (w * 0.45f) * (i / ticks.toFloat())
                    val len = if (i % 5 == 0) 16f else 10f
                    val alpha = if (i % 5 == 0) 0.20f else 0.12f

                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(xL, midY - len),
                        end = Offset(xL, midY + len),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(xR, midY - len),
                        end = Offset(xR, midY + len),
                        strokeWidth = 3f
                    )
                }

                // Curseur
                val range = w * 0.45f
                val x = midX + (pos * range)

                val pointerTop = midY - 26f
                val pointerBot = midY + 26f

                drawLine(
                    color = pointerColor,
                    start = Offset(x, pointerTop),
                    end = Offset(x, pointerBot),
                    strokeWidth = 8f
                )

                // Glow discret
                drawCircle(
                    color = pointerColor.copy(alpha = 0.18f),
                    radius = 22f,
                    center = Offset(x, midY)
                )
            }
        }
    }
}
