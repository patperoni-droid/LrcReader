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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current

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

    val tunerState by TunerEngine.state.collectAsState()

    DisposableEffect(hasMicPermission) {
        if (hasMicPermission) TunerEngine.start()
        onDispose { TunerEngine.stop() }
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(TunerUi.BgTop, TunerUi.BgMid, TunerUi.BgBottom)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

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

                // ✅ Détection notes sensibles (B + E aigu)
                val isB = currentNote.startsWith("B")
                val eOct = currentNote.drop(1).takeWhile { it.isDigit() }.toIntOrNull()
                val isHighE = currentNote.startsWith("E") && (eOct == null || eOct >= 5)

                // ✅ AIGUILLE (valeur réellement affichée)
                // IMPORTANT : on calcule UNE SEULE fois et on l'utilise
                // 1) pour l'affichage de la jauge
                // 2) pour déclencher le jaune (OK)
                val needleCents: Float? = centsOffset?.let { raw ->
                    magnetizeCents(raw = raw, isB = isB, isHighE = isHighE)
                }

                var holdUntilMs by remember { mutableStateOf(0L) }
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

                LaunchedEffect(Unit) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(50)
                    }
                }

                // ✅ RÈGLE D'OR : le jaune suit l'aiguille (pas le son brut)
                LaunchedEffect(needleCents, currentNote) {
                    val t = System.currentTimeMillis()
                    val absNeedle = needleCents?.let { abs(it) } ?: 999f

                    // Hold : B + E aigu un peu plus long
                    val holdMs = if (isB || isHighE) 1200L else 900L

                    // ✅ RÈGLE : jaune immédiat quand l’aiguille est au centre
                    // E aigu : plus permissif
                    val enterTol = when {
                        isHighE -> 4.5f
                        isB -> 3.0f
                        else -> 2.5f
                    }

                    // ✅ HYSTÉRÉSIS (anti-aléatoire) :
                    // pour rester jaune, on autorise un tout petit peu plus large que pour entrer
                    val stayTol = enterTol + 1.0f

                    // On considère "ok" si :
                    // - on est dans la zone centrale (enterTol)
                    // - OU on est déjà en hold et on n'est pas sorti trop loin (stayTol)
                    val currentlyHolding = holdUntilMs > t
                    val okNow = (needleCents != null && absNeedle < enterTol) ||
                            (currentlyHolding && needleCents != null && absNeedle < stayTol)

                    if (okNow) {
                        holdUntilMs = max(holdUntilMs, t + holdMs)
                    }
                }

                val displayOk = holdUntilMs > nowMs

                // ✅ L'affichage de la jauge : aiguille aimantée, puis figée au centre pendant le hold
                val shownCentsOffset: Float? = when {
                    needleCents == null -> null
                    displayOk -> 0f
                    else -> needleCents
                }

                val noteColor = if (displayOk) TunerUi.Accent else TunerUi.TextMain

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF121212), Color(0xFF0C0C0C))
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
                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                        ) {
                            Text(
                                text = currentNote,
                                color = noteColor,
                                fontSize = 88.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-2).sp
                            )

// ✅ Espace réservé pour éviter le "saut" de la lettre
                            Text(
                                text = "IN TUNE",
                                color = if (displayOk) TunerUi.Accent else Color.Transparent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 3.sp
                            )

                            TunerMeterPro(
                                centsOffset = shownCentsOffset,
                                isLockedOk = displayOk,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = currentFreqText,
                                color = TunerUi.TextSoft,
                                fontSize = 14.sp
                            )

                            val centsText =
                                shownCentsOffset?.let { "${it.roundToInt()} cents" } ?: "— cents"
                            val absShown = shownCentsOffset?.let { abs(it) } ?: 999f

                            val centsColor = when {
                                shownCentsOffset == null -> TunerUi.TextSoft
                                displayOk -> TunerUi.Accent
                                absShown < 15f -> TunerUi.TextSoft
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

                        val level = tunerState.inputLevel.coerceIn(0f, 1f)
                        Text(
                            text = "Niveau d’entrée : ${(level * 100).toInt()} %",
                            color = TunerUi.TextDim,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = level,
                            onValueChange = { },
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

// 🎯 Aimant : transforme le cents brut en valeur d’aiguille (celle affichée)
private fun magnetizeCents(raw: Float, isB: Boolean, isHighE: Boolean): Float {
    val a = abs(raw)

    // E aigu : aimant plus fort, B : moyen, le reste : normal
    val t1 = when {
        isHighE -> 7f
        isB -> 5f
        else -> 4f
    }
    val t2 = when {
        isHighE -> 14f
        isB -> 10f
        else -> 8f
    }
    val t3 = when {
        isHighE -> 22f
        isB -> 16f
        else -> 12f
    }

    return when {
        a < t1 -> raw * 0.10f
        a < t2 -> raw * 0.25f
        a < t3 -> raw * 0.55f
        else -> raw
    }
}

@Composable
private fun TunerMeterPro(
    centsOffset: Float?,
    isLockedOk: Boolean,
    modifier: Modifier = Modifier
) {
    val clamped = (centsOffset ?: 0f).coerceIn(-50f, 50f)
    val target = (clamped / 50f)

    val duration = if (isLockedOk) 320 else 140

    val pos by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = duration),
        label = "tunerMeterPos"
    )

    val absOffset = abs(clamped)
    val isOk = isLockedOk || (centsOffset != null && absOffset < 5f)
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
                val midX = w / 2f
                val midY = size.height / 2f

                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 6f
                )

                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(midX, midY - 18f),
                    end = Offset(midX, midY + 18f),
                    strokeWidth = 5f
                )

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

                val range = w * 0.45f
                val x = midX + (pos * range)

                drawLine(
                    color = pointerColor,
                    start = Offset(x, midY - 26f),
                    end = Offset(x, midY + 26f),
                    strokeWidth = 8f
                )

                drawCircle(
                    color = pointerColor.copy(alpha = 0.18f),
                    radius = 22f,
                    center = Offset(x, midY)
                )
            }
        }
    }
}