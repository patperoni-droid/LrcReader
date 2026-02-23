package com.patrick.lrcreader.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.MidiOutput
import com.patrick.lrcreader.exo.R

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 20.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = stringResource(R.string.settings_midi_bluetooth_section),
                    fontSize = 16.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        // 🔵 Test Bluetooth MIDI : doit faire clignoter la LED du WIDI
                        MidiOutput.sendProgramChange(
                            channel = 1,
                            program = 1
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_test_bluetooth_midi))
                }
            }
        }
    }
}
