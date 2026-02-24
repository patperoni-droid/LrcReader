package com.patrick.lrcreader.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.patrick.lrcreader.core.AppLanguagePrefs
import com.patrick.lrcreader.core.MidiOutput
import com.patrick.lrcreader.exo.R

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(AppLanguagePrefs.getSavedLanguageTag(context)) }

    val currentLanguageLabel = when (selectedLanguageTag) {
        "fr" -> stringResource(R.string.language_french)
        "en" -> stringResource(R.string.language_english)
        "es" -> stringResource(R.string.language_spanish)
        else -> stringResource(R.string.language_system)
    }

    fun applyLanguageSelection(languageTag: String?) {
        AppLanguagePrefs.setSavedLanguageTag(context, languageTag)
        val locales = if (languageTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        selectedLanguageTag = languageTag
        showLanguageDialog = false
    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_language),
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentLanguageLabel,
                    fontSize = 14.sp,
                    color = Color(0xFFBDBDBD)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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

    if (showLanguageDialog) {
        val languageOptions = listOf(
            null to R.string.language_system,
            "fr" to R.string.language_french,
            "en" to R.string.language_english,
            "es" to R.string.language_spanish
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(text = stringResource(R.string.settings_language)) },
            text = {
                Column {
                    languageOptions.forEach { (tag, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyLanguageSelection(tag) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguageTag == tag,
                                onClick = { applyLanguageSelection(tag) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
