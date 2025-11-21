package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.ui.theme.DarkBlueGradientBackground

@Composable
fun TunerScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    DarkBlueGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding() + 12.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Accordeur",
                fontSize = 26.sp,
                color = Color.White
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF141414)
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Accordeur en préparation…",
                        fontSize = 16.sp,
                        color = Color(0xFFBBBBBB)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "🎵 Fonction en développement",
                        fontSize = 12.sp,
                        color = Color(0xFF7AA8FF)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // BOUTON RETOUR
            FilledTonalButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF3A3A3A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text("Retour", fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}