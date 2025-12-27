package com.patrick.lrcreader.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

import com.patrick.lrcreader.core.TrackEqEngine
import com.patrick.lrcreader.core.TrackEqPrefs
import com.patrick.lrcreader.core.TrackEqSettings
import kotlin.math.roundToInt

@Composable
fun TrackMixScreen(
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFFE040FB),

    currentTrackGainDb: Int,
    onTrackGainChange: (Int) -> Unit,

    tempo: Float,
    onTempoChange: (Float) -> Unit,

    pitchSemi: Int,
    onPitchSemiChange: (Int) -> Unit,

    currentTrackUri: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val minDb = -12
    val maxDb = 0
    val defaultDb = -5 // ✅ voulu par toi

    val minTempo = 0.8f
    val maxTempo = 1.2f
    val minSemi = -6
    val maxSemi = 6

    /**
     * ✅ FIX IMPORTANT :
     * - On NE considère plus "0" comme une valeur spéciale.
     *   0 dB est une vraie valeur (et c’est ton maxDb).
     * - La source de vérité = currentTrackGainDb (déjà chargé depuis prefs dans MainActivity).
     */
    var displayGainDb by remember(currentTrackUri) {
        mutableIntStateOf(currentTrackGainDb.coerceIn(minDb, maxDb))
    }

    // Resync UI quand on change de titre OU quand le parent change la valeur
    LaunchedEffect(currentTrackUri, currentTrackGainDb) {
        val clamped = currentTrackGainDb.coerceIn(minDb, maxDb)
        displayGainDb = clamped
    }

    // mapping slider 0..1 pour le fader gain
    var gain01 by remember(displayGainDb) {
        mutableFloatStateOf(
            ((displayGainDb - minDb).toFloat() / (maxDb - minDb)).coerceIn(0f, 1f)
        )
    }

    var tempo01 by remember(tempo) {
        mutableFloatStateOf(((tempo - minTempo) / (maxTempo - minTempo)).coerceIn(0f, 1f))
    }

    // ✅ Anti-craquement SPEED : on évite 200 updates/sec vers ExoPlayer
    val scope = rememberCoroutineScope()
    var tempoApplyJob by remember { mutableStateOf<Job?>(null) }
    var tempoPending by remember { mutableFloatStateOf(tempo) }

    fun scheduleTempoApply(newTempo: Float) {
        tempoPending = newTempo
        tempoApplyJob?.cancel()
        tempoApplyJob = scope.launch {
            delay(90)
            onTempoChange(tempoPending)
        }
    }

    var pitch01 by remember(pitchSemi) {
        mutableFloatStateOf(((pitchSemi - minSemi).toFloat() / (maxSemi - minSemi)).coerceIn(0f, 1f))
    }

    val initialEq = remember(currentTrackUri) {
        currentTrackUri?.let { TrackEqPrefs.load(context, it) } ?: TrackEqSettings(0f, 0f, 0f)
    }

    var lowGain by remember(currentTrackUri) { mutableFloatStateOf(initialEq.low) }
    var midGain by remember(currentTrackUri) { mutableFloatStateOf(initialEq.mid) }
    var highGain by remember(currentTrackUri) { mutableFloatStateOf(initialEq.high) }

    fun applyAndSaveEq() {
        TrackEqEngine.setBands(lowGain, midGain, highGain)
        currentTrackUri?.let {
            TrackEqPrefs.save(context, it, TrackEqSettings(lowGain, midGain, highGain))
        }
    }

    LaunchedEffect(currentTrackUri) { applyAndSaveEq() }

    // ─────────────────────────────────────────────
    //  LOOK CONSOLE ANALOGIQUE
    // ─────────────────────────────────────────────

    val bg = Brush.verticalGradient(
        listOf(Color(0xFF0B0B0C), Color(0xFF121014), Color(0xFF0A0A0B))
    )
    val plate = Brush.verticalGradient(
        listOf(Color(0xFF2A2B2F), Color(0xFF1D1E22), Color(0xFF16171A))
    )
    val bevel = Color(0xFF3A3C42)
    val textMain = Color(0xFFF5F0E6)
    val textSub = Color(0xFFB7C0C7)
    val amber = Color(0xFFFFC107)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> change.consume() } }
            .pointerInput(Unit) { detectTapGestures { /* consume */ } }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Bandeau "console"
            ConsoleHeader(
                title = "TRACK CONSOLE",
                subtitle = currentTrackUri?.takeLast(26) ?: "Aucun titre",
                accent = amber
            )

            Spacer(Modifier.height(10.dp))

            // ── Plate principale (gain + knobs + vu)
            PlateCard(plate = plate, bevel = bevel) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // FADER GAIN (gros, analog)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LEVEL", color = textSub, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text(
                            text = "${if (displayGainDb >= 0) "+$displayGainDb" else displayGainDb} dB",
                            color = textMain,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        AnalogFader(
                            value01 = gain01,
                            accent = highlightColor,
                            tickColor = Color(0xFF5C6068),
                            knobColor = Color(0xFFECECEC),
                            height = 210.dp,
                            onChange = { v ->
                                gain01 = v
                                val newDb = (minDb + v * (maxDb - minDb))
                                    .toInt()
                                    .coerceIn(minDb, maxDb)
                                displayGainDb = newDb

                                // ✅ On applique + on sauvegarde via le parent (TrackVolumePrefs etc.)
                                onTrackGainChange(newDb)
                            },
                            // ✅ commit optionnel (utile si plus tard tu veux "save only on release")
                            onCommit = { v ->
                                val finalDb = (minDb + v * (maxDb - minDb))
                                    .toInt()
                                    .coerceIn(minDb, maxDb)
                                displayGainDb = finalDb
                                onTrackGainChange(finalDb)
                            },
                            labelMin = "$minDb",
                            labelMax = "$maxDb"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("ANTI-CLIP", color = Color(0xFF9AA6AF), fontSize = 10.sp)
                    }

                    // Knobs TEMPO / PITCH
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SPEED", color = textSub, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))

                        AnalogKnob(
                            value01 = tempo01,
                            accent = Color(0xFF80CBC4),
                            label = String.format("x%.2f", tempo),
                            onChange = { v ->
                                tempo01 = v
                                val newTempo = (minTempo + v * (maxTempo - minTempo))
                                    .coerceIn(minTempo, maxTempo)
                                scheduleTempoApply(newTempo)
                            },
                            onCommit = { v ->
                                tempoApplyJob?.cancel()
                                val finalTempo = (minTempo + v * (maxTempo - minTempo))
                                    .coerceIn(minTempo, maxTempo)
                                onTempoChange(finalTempo)
                            }
                        )

                        TextButton(onClick = {
                            onTempoChange(1f)
                            tempo01 = ((1f - minTempo) / (maxTempo - minTempo))
                        }) {
                            Text("Reset 1.00x", color = Color(0xFF80CBC4), fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(10.dp))

                        Text("PITCH", color = textSub, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))

                        AnalogKnob(
                            value01 = pitch01,
                            accent = Color(0xFFCE93D8),
                            label = "${if (pitchSemi >= 0) "+$pitchSemi" else pitchSemi} st",
                            onChange = { v ->
                                pitch01 = v
                                val semi = (minSemi + v * (maxSemi - minSemi))
                                    .roundToInt()
                                    .coerceIn(minSemi, maxSemi)
                                onPitchSemiChange(semi)
                            }
                        )

                        TextButton(onClick = {
                            onPitchSemiChange(0)
                            pitch01 = ((0 - minSemi).toFloat() / (maxSemi - minSemi))
                        }) {
                            Text("Reset 0", color = Color(0xFFCE93D8), fontSize = 11.sp)
                        }
                    }

                    // VU (simple, décoratif mais utile)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VU", color = textSub, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))

                        val vu01 = ((displayGainDb - minDb).toFloat() / (maxDb - minDb))
                            .coerceIn(0f, 1f)

                        VuMeter(
                            value01 = vu01,
                            warnFrom01 = 0.83f,
                            base = Color(0xFF2F3137),
                            ok = Color(0xFF4CAF50),
                            warn = amber,
                            clip = Color(0xFFE53935)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("KEEP IT CLEAN", color = Color(0xFF8FA0AA), fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── EQ Plate
            PlateCard(plate = plate, bevel = bevel) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EQUALIZER", color = textMain, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text("3 BANDS", color = textSub, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EqAnalogFader("LOW", lowGain, { lowGain = it; applyAndSaveEq() }, Color(0xFF4CAF50))
                        EqAnalogFader("MID", midGain, { midGain = it; applyAndSaveEq() }, amber)
                        EqAnalogFader("HIGH", highGain, { highGain = it; applyAndSaveEq() }, Color(0xFF42A5F5))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Zone retour (style “soft label”)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable { onClose() }
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TOUCH HERE TO RETURN",
                    color = Color(0x66FFFFFF),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/* ───────────────────────────────────────────── */
/*  UI COMPONENTS “CONSOLE”                      */
/* ───────────────────────────────────────────── */

@Composable
private fun ConsoleHeader(title: String, subtitle: String, accent: Color) {
    val shape = RoundedCornerShape(14.dp)
    val grad = Brush.horizontalGradient(
        listOf(Color(0xFF3B2A20), Color(0xFF4E382A), Color(0xFF3B2A20))
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(grad, shape)
            .border(1.dp, Color(0x553A2A20), shape)
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(title, color = Color(0xFFFFF3D6), fontSize = 14.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = accent.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

@Composable
private fun PlateCard(
    plate: Brush,
    bevel: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .background(plate, shape)
            .border(1.dp, bevel.copy(alpha = 0.55f), shape)
            .padding(2.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1B1F), Color(0xFF121318))),
                RoundedCornerShape(16.dp)
            )
    ) {
        content()
    }
}

/* ─────────── VU meter ─────────── */

@Composable
private fun VuMeter(
    value01: Float,
    warnFrom01: Float,
    base: Color,
    ok: Color,
    warn: Color,
    clip: Color
) {
    Canvas(
        modifier = Modifier
            .width(46.dp)
            .height(220.dp)
    ) {
        val w = size.width
        val h = size.height
        val pad = w * 0.18f

        drawRoundRect(
            color = base,
            topLeft = Offset(pad, 0f),
            size = Size(w - 2 * pad, h),
            cornerRadius = CornerRadius((w - 2 * pad) / 2f)
        )

        val v = value01.coerceIn(0f, 1f)
        val fillH = h * v
        val top = h - fillH

        val col = when {
            v >= 0.95f -> clip
            v >= warnFrom01 -> warn
            else -> ok
        }

        drawRoundRect(
            color = col,
            topLeft = Offset(pad, top),
            size = Size(w - 2 * pad, fillH),
            cornerRadius = CornerRadius((w - 2 * pad) / 2f)
        )

        val tickX0 = pad * 0.55f
        val tickX1 = w - tickX0
        for (i in 1..9) {
            val y = h * (i / 10f)
            val alpha = if (i == 8 || i == 9) 0.7f else 0.35f
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(tickX0, h - y),
                end = Offset(tickX1, h - y),
                strokeWidth = 1f
            )
        }
    }
}

/* ─────────── Analog Fader (gain) ─────────── */

@Composable
private fun AnalogFader(
    value01: Float,
    accent: Color,
    tickColor: Color,
    knobColor: Color,
    height: androidx.compose.ui.unit.Dp,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit = {}, // ✅ FIX : optionnel (sinon “rouge”)
    labelMin: String,
    labelMax: String
) {
    val shape = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(labelMax, color = Color(0xFF9AA6AF), fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))

            val valueState = rememberUpdatedState(value01)
            val onChangeState = rememberUpdatedState(onChange)
            val onCommitState = rememberUpdatedState(onCommit)

            Canvas(
                modifier = Modifier
                    .width(64.dp)
                    .height(height)
                    .background(Color(0xFF0F1012), shape)
                    .border(1.dp, Color(0xFF3A3C42), shape)
                    .pointerInput(Unit) {
                        var startValue = 0f
                        var accDragPx = 0f

                        detectVerticalDragGestures(
                            onDragStart = {
                                startValue = valueState.value
                                accDragPx = 0f
                            },
                            onDragEnd = {
                                val pixelsForFullTravel = size.height * 3.5f
                                val delta01 = (-accDragPx / pixelsForFullTravel)
                                val next = (startValue + delta01).coerceIn(0f, 1f)
                                onCommitState.value(next)
                            },
                            onDragCancel = {
                                val pixelsForFullTravel = size.height * 3.5f
                                val delta01 = (-accDragPx / pixelsForFullTravel)
                                val next = (startValue + delta01).coerceIn(0f, 1f)
                                onCommitState.value(next)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accDragPx += dragAmount

                                val pixelsForFullTravel = size.height * 3.5f
                                val delta01 = (-accDragPx / pixelsForFullTravel)
                                val next = (startValue + delta01).coerceIn(0f, 1f)

                                onChangeState.value(next)
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val slotW = w * 0.22f
                val x = (w - slotW) / 2f

                drawRoundRect(
                    color = Color(0xFF2B2D33),
                    topLeft = Offset(x, 12f),
                    size = Size(slotW, h - 24f),
                    cornerRadius = CornerRadius(slotW)
                )

                val tx0 = x - w * 0.22f
                val tx1 = x - w * 0.06f
                val tx2 = x + slotW + w * 0.06f
                val tx3 = x + slotW + w * 0.22f
                for (i in 0..10) {
                    val y = 12f + (h - 24f) * (i / 10f)
                    val thick = if (i % 5 == 0) 2f else 1f
                    val a = if (i % 5 == 0) 0.75f else 0.35f
                    drawLine(tickColor.copy(alpha = a), Offset(tx0, y), Offset(tx1, y), thick)
                    drawLine(tickColor.copy(alpha = a), Offset(tx2, y), Offset(tx3, y), thick)
                }

                val v = value01.coerceIn(0f, 1f)
                val fillH = (h - 24f) * v
                drawRoundRect(
                    color = accent.copy(alpha = 0.22f),
                    topLeft = Offset(x, 12f + (h - 24f - fillH)),
                    size = Size(slotW, fillH),
                    cornerRadius = CornerRadius(slotW)
                )

                val knobY = 12f + (h - 24f) * (1f - v)
                drawRoundRect(
                    color = Color(0xFF3C3F46),
                    topLeft = Offset(w * 0.18f, knobY - 10f),
                    size = Size(w * 0.64f, 20f),
                    cornerRadius = CornerRadius(8f)
                )
                drawCircle(
                    color = knobColor,
                    radius = 5.5f,
                    center = Offset(w / 2f, knobY)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(labelMin, color = Color(0xFF9AA6AF), fontSize = 10.sp)
        }
    }
}

/* ─────────── Analog Knob ─────────── */

@Composable
private fun AnalogKnob(
    value01: Float,
    accent: Color,
    label: String,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit = {}
) {
    val sizeDp = 96.dp

    var internal by remember { mutableFloatStateOf(value01.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(value01) {
        if (!isDragging) internal = value01.coerceIn(0f, 1f)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Canvas(
            modifier = Modifier
                .size(sizeDp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            onCommit(internal)
                        },
                        onDragCancel = {
                            isDragging = false
                            onCommit(internal)
                        }
                    ) { change, dragAmount ->
                        change.consume()

                        val pixelsForFullTravel = 2600f
                        val delta = (-dragAmount / pixelsForFullTravel)

                        internal = (internal + delta).coerceIn(0f, 1f)
                        onChange(internal)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val r = (w.coerceAtMost(h)) * 0.42f
            val c = Offset(w / 2f, h / 2f)

            drawCircle(Color(0xFF2B2D33), r * 1.12f, c)
            drawCircle(Color(0xFF141519), r * 1.02f, c)

            val startDeg = 225f
            val sweepDeg = 270f
            val endDeg = startDeg + sweepDeg * internal

            for (i in 0..18) {
                val t = i / 18f
                val ang = (startDeg + sweepDeg * t) * (PI.toFloat() / 180f)
                val x0 = c.x + cos(ang) * (r * 1.08f)
                val y0 = c.y + sin(ang) * (r * 1.08f)
                val x1 = c.x + cos(ang) * (r * 1.18f)
                val y1 = c.y + sin(ang) * (r * 1.18f)
                val a = if (i % 6 == 0) 0.75f else 0.35f
                val sw = if (i % 6 == 0) 3f else 2f
                drawLine(Color.White.copy(alpha = a), Offset(x0, y0), Offset(x1, y1), sw)
            }

            drawArc(
                color = accent.copy(alpha = 0.85f),
                startAngle = startDeg,
                sweepAngle = (endDeg - startDeg),
                useCenter = false,
                topLeft = Offset(c.x - r * 1.22f, c.y - r * 1.22f),
                size = Size(r * 2.44f, r * 2.44f),
                style = Stroke(width = 8f)
            )

            rotate(endDeg - 90f, c) {
                drawRoundRect(
                    color = Color(0xFFEDEDED),
                    topLeft = Offset(c.x - 3f, c.y - (r * 0.95f)),
                    size = Size(6f, r * 0.35f),
                    cornerRadius = CornerRadius(3f)
                )
            }

            drawCircle(Color(0xFF3C3F46), r * 0.33f, c)
            drawCircle(Color(0xFF202127), r * 0.26f, c)
        }

        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFEFE8DA), fontSize = 12.sp)
        Text("drag ↑↓", color = Color(0xFF9AA6AF), fontSize = 10.sp)
    }
}

/* ─────────── EQ FADER (look analog) ─────────── */

@Composable
private fun EqAnalogFader(
    label: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    color: Color
) {
    var sliderValue by remember(gain) {
        mutableFloatStateOf(((gain + 12f) / 24f).coerceIn(0f, 1f))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Text(label, color = Color(0xFFF5F0E6), fontSize = 12.sp, letterSpacing = 2.sp)
        Text(String.format("%+d dB", gain.roundToInt()), color = Color(0xFFB7C0C7), fontSize = 11.sp)

        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .height(190.dp)
                .width(54.dp)
                .background(Color(0xFF0F1012), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3A3C42), RoundedCornerShape(14.dp))
                .pointerInput(sliderValue) {
                    var local = sliderValue
                    detectVerticalDragGestures { change, drag ->
                        change.consume()
                        local = (local - drag / size.height).coerceIn(0f, 1f)
                        sliderValue = local
                        onGainChange(local * 24f - 12f)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            val slotW = w * 0.22f
            val x = (w - slotW) / 2f
            val topPad = 10f
            val usableH = h - 2 * topPad

            drawRoundRect(
                color = Color(0xFF2B2D33),
                topLeft = Offset(x, topPad),
                size = Size(slotW, usableH),
                cornerRadius = CornerRadius(slotW)
            )

            val tickL0 = x - w * 0.20f
            val tickL1 = x - w * 0.05f
            val tickR0 = x + slotW + w * 0.05f
            val tickR1 = x + slotW + w * 0.20f
            for (i in 0..8) {
                val t = i / 8f
                val y = topPad + usableH * t
                val a = if (i % 4 == 0) 0.7f else 0.3f
                val sw = if (i % 4 == 0) 2.5f else 1.5f
                drawLine(Color.White.copy(alpha = a), Offset(tickL0, y), Offset(tickL1, y), sw)
                drawLine(Color.White.copy(alpha = a), Offset(tickR0, y), Offset(tickR1, y), sw)
            }

            val v = sliderValue.coerceIn(0f, 1f)
            val fillH = usableH * v
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = Offset(x, topPad + (usableH - fillH)),
                size = Size(slotW, fillH),
                cornerRadius = CornerRadius(slotW)
            )

            val knobY = topPad + usableH * (1f - v)
            drawRoundRect(
                color = Color(0xFF3C3F46),
                topLeft = Offset(w * 0.18f, knobY - 10f),
                size = Size(w * 0.64f, 20f),
                cornerRadius = CornerRadius(8f)
            )
            drawCircle(Color(0xFFECECEC), 5.5f, Offset(w / 2f, knobY))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TrackMixScreenPreview() {
    TrackMixScreen(
        currentTrackGainDb = -5,
        onTrackGainChange = {},
        tempo = 1f,
        onTempoChange = {},
        pitchSemi = 0,
        onPitchSemiChange = {},
        currentTrackUri = "content://demo/track",
        onClose = {}
    )
}