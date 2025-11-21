package com.patrick.lrcreader.ui

import kotlin.math.log2
import kotlin.math.abs
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.patrick.lrcreader.core.TunerEngine
import com.patrick.lrcreader.core.TunerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val tunerState by TunerEngine.state.collectAsState()

    // permission micro
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            TunerEngine.start()
        }
    }

    // mode : chromatique ou guitare
    var guitarMode by remember { mutableStateOf(true) }

    // calibration A4 (slider UI, sync avec engine)
    var a4Ui by remember {
        mutableFloatStateOf(TunerEngine.getReferenceA4())
    }

    // à la fermeture de l’écran, on coupe tout
    DisposableEffect(Unit) {
        onDispose {
            TunerEngine.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 12.dp
            )
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Accordeur",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                TunerEngine.stop()
                onClose()
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!hasPermission) {
            // écran d’explication permission
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Autorise le micro pour utiliser l’accordeur.",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Text("Autoriser le micro")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Le micro sert uniquement à analyser le son de ta guitare.",
                        color = Color(0xFFBBBBBB),
                        fontSize = 13.sp
                    )
                }
            }
            return
        }

        // si on a la permission, on démarre au premier affichage
        LaunchedEffect(Unit) {
            TunerEngine.start()
        }

        // --- Barre mode + calibration ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                TextButton(onClick = { guitarMode = false }) {
                    Text(
                        "Chromatique",
                        color = if (!guitarMode) Color(0xFFE040FB) else Color(0xFFBBBBBB),
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = { guitarMode = true }) {
                    Text(
                        "Guitare",
                        color = if (guitarMode) Color(0xFFE040FB) else Color(0xFFBBBBBB),
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                text = "A4 = ${a4Ui.toInt()} Hz",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
        }

        Slider(
            value = a4Ui,
            onValueChange = { v ->
                val clamped = v.coerceIn(430f, 450f)
                a4Ui = clamped
                TunerEngine.setReferenceA4(clamped)
            },
            valueRange = 430f..450f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // --- Niveau d’entrée ---
        Text(
            text = "Niveau d’entrée",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        LinearProgressIndicator(
            progress = { tunerState.inputLevel },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color(0xFFE040FB),
            trackColor = Color(0x33E040FB)
        )

        Spacer(Modifier.height(16.dp))

        // --- Affichage principal (note / corde + fréquence) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            MainTunerView(
                state = tunerState,
                guitarMode = guitarMode
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Boutons Start / Stop au cas où ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { TunerEngine.start() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Start")
            }
            Button(
                onClick = { TunerEngine.stop() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF8A80)
                )
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun MainTunerView(
    state: TunerState,
    guitarMode: Boolean
) {
    val freq = state.frequency
    val cents = state.cents

    // mapping des cordes de guitare (standard)
    // E2 82.41 Hz, A2 110, D3 146.83, G3 196, B3 246.94, E4 329.63
    val guitarStrings = listOf(
        82.41f to "E2",
        110.00f to "A2",
        146.83f to "D3",
        196.00f to "G3",
        246.94f to "B3",
        329.63f to "E4"
    )

    var displayNote = state.noteName
    var displayCents = cents
    var infoLine = ""

    if (freq != null) {
        if (guitarMode) {
            // on cherche la corde la plus proche
            val nearest = guitarStrings.minByOrNull { (f, _) ->
                abs(log2(freq / f))
            }
            if (nearest != null) {
                val (targetFreq, label) = nearest
                // écart en cents par rapport à la corde cible
                val centsToString =
                    (1200 * log2(freq / targetFreq)).toInt().coerceIn(-100, 100)
                displayNote = label
                displayCents = centsToString
                infoLine = "${"%.1f".format(freq)} Hz (cible ${"%.1f".format(targetFreq)} Hz)"
            } else {
                infoLine = "${"%.1f".format(freq)} Hz"
            }
        } else {
            // mode chromatique
            infoLine = "${"%.1f".format(freq)} Hz"
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = displayNote,
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (freq != null) infoLine else "Joue une corde…",
            color = Color(0xFFB0BEC5),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        CentsGauge(displayCents)
    }
}

@Composable
private fun CentsGauge(
    cents: Int?
) {
    val c = cents ?: 0
    val normalized = (c / 50f).coerceIn(-1f, 1f) // -1..1

    Text(
        text = if (cents == null) "—" else "$cents cents",
        color = Color.White,
        fontSize = 14.sp
    )

    Spacer(Modifier.height(8.dp))

    // jauge horizontale simple
    Box(
        modifier = Modifier
            .width(260.dp)
            .height(24.dp)
            .background(Color(0xFF202020))
    ) {
        // zone centrale "juste"
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(60.dp)
                .height(24.dp)
                .background(Color(0xFF1B5E20).copy(alpha = 0.4f))
        )

        // trait central
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(24.dp)
                .background(Color(0xFF4CAF50))
        )

        // indicateur
        val offsetPx = normalized * (260 - 20) / 2f // 20dp = largeur indicateur
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetPx.dp)
                .width(20.dp)
                .height(24.dp)
                .background(Color(0xFFE040FB))
        )
    }

    Spacer(Modifier.height(8.dp))

    val txt = when {
        cents == null -> ""
        cents < -5 -> "Trop grave"
        cents > 5 -> "Trop aigu"
        else -> "Parfait 👍"
    }

    if (txt.isNotEmpty()) {
        Text(
            text = txt,
            color = Color(0xFFB0BEC5),
            fontSize = 14.sp
        )
    }
}