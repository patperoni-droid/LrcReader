package com.patrick.lrcreader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.light.LightAction
import com.patrick.lrcreader.core.light.LightCue
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpLightCueBridge

private const val POPUP_RED_ARGB = 0xFFFF0000L
private const val POPUP_BLUE_ARGB = 0xFF0000FFL
private const val POPUP_GREEN_ARGB = 0xFF00FF00L
private const val POPUP_WHITE_ARGB = 0xFFFFFFFFL
private const val DEFAULT_STROBE_HZ = 8f
private val DURATION_PRESET_VALUES_MS = listOf(0L, 50L, 100L, 150L, 250L, 500L)
private val FADE_OUT_PRESET_VALUES_MS = listOf(0L, 50L, 100L, 200L, 400L)

private enum class EditableLightCueType {
    COLOR,
    STROBE,
    BLACKOUT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineLightCueEditorPopup(
    trackUri: String,
    markerTimeMs: Long,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isLite = EditionConfig.isLite
    val safeMarkerTimeMs = markerTimeMs.coerceAtLeast(0L)
    val sTimelineProDialogTitle = stringResource(R.string.timeline_config_pro_dialog_title)
    val sTimelineProDialogMessage = stringResource(R.string.timeline_config_pro_dialog_message)
    val sUpgradeToPro = stringResource(R.string.library_upgrade_to_pro)
    val existing = remember(context, trackUri, safeMarkerTimeMs) {
        SmpLightCueBridge.getCueAtTime(context, trackUri, safeMarkerTimeMs)
    }
    val initialType = when (existing?.action) {
        is LightAction.Color -> EditableLightCueType.COLOR
        is LightAction.Strobe -> EditableLightCueType.STROBE
        LightAction.Blackout -> EditableLightCueType.BLACKOUT
        null -> EditableLightCueType.COLOR
    }

    var cueType by remember(existing) { mutableStateOf(initialType) }
    var colorArgb by remember(existing) {
        mutableStateOf((existing?.action as? LightAction.Color)?.argb ?: POPUP_RED_ARGB)
    }
    var intensityPercentText by remember(existing) {
        mutableStateOf((((existing?.intensity ?: 1f).coerceIn(0f, 1f)) * 100f).toInt().toString())
    }
    var fadeMsText by remember(existing) {
        mutableStateOf((existing?.fadeMs ?: 0L).coerceAtLeast(0L).toString())
    }
    var hzText by remember(existing) {
        mutableStateOf(((existing?.action as? LightAction.Strobe)?.hz ?: DEFAULT_STROBE_HZ).toString())
    }
    val durationPresetValues = remember(existing) {
        (DURATION_PRESET_VALUES_MS + listOfNotNull(existing?.durationMs))
            .distinct()
            .sorted()
    }
    val fadeOutPresetValues = remember(existing) {
        (FADE_OUT_PRESET_VALUES_MS + listOfNotNull(existing?.fadeOutMs))
            .distinct()
            .sorted()
    }
    var durationPresetMs by remember(existing) {
        mutableStateOf(existing?.durationMs?.coerceAtLeast(0L) ?: 0L)
    }
    var fadeOutPresetMs by remember(existing) {
        mutableStateOf(existing?.fadeOutMs?.coerceAtLeast(0L) ?: 0L)
    }
    var showTimelineConfigProDialog by remember { mutableStateOf(false) }

    val openUpgradeToPro: () -> Unit = remember(context) {
        {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=MusiMio Pro")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=MusiMio%20Pro&c=apps")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(marketIntent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(webIntent)
            }
        }
    }

    fun onLiteConfigAttempt(block: () -> Unit) {
        if (isLite) {
            showTimelineConfigProDialog = true
        } else {
            block()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = stringResource(
                    R.string.light_cue_timeline_dialog_title,
                    formatTimelineLightCueTime(safeMarkerTimeMs)
                ),
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = cueType == EditableLightCueType.COLOR,
                        onClick = { onLiteConfigAttempt { cueType = EditableLightCueType.COLOR } },
                        label = { CompactChipText(stringResource(R.string.light_cue_type_color)) }
                    )
                    FilterChip(
                        selected = cueType == EditableLightCueType.STROBE,
                        onClick = { onLiteConfigAttempt { cueType = EditableLightCueType.STROBE } },
                        label = { CompactChipText(stringResource(R.string.light_cue_type_strobe)) }
                    )
                    FilterChip(
                        selected = cueType == EditableLightCueType.BLACKOUT,
                        onClick = { onLiteConfigAttempt { cueType = EditableLightCueType.BLACKOUT } },
                        label = { CompactChipText(stringResource(R.string.light_cue_type_blackout)) }
                    )
                }

                if (cueType == EditableLightCueType.COLOR) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LightColorChip(
                            label = stringResource(R.string.light_color_red),
                            selected = colorArgb == POPUP_RED_ARGB,
                            onClick = { onLiteConfigAttempt { colorArgb = POPUP_RED_ARGB } }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_blue),
                            selected = colorArgb == POPUP_BLUE_ARGB,
                            onClick = { onLiteConfigAttempt { colorArgb = POPUP_BLUE_ARGB } }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_green),
                            selected = colorArgb == POPUP_GREEN_ARGB,
                            onClick = { onLiteConfigAttempt { colorArgb = POPUP_GREEN_ARGB } }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_white),
                            selected = colorArgb == POPUP_WHITE_ARGB,
                            onClick = { onLiteConfigAttempt { colorArgb = POPUP_WHITE_ARGB } }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactNumberField(
                            modifier = Modifier.weight(1f),
                            value = intensityPercentText,
                            onValueChange = { input ->
                                onLiteConfigAttempt {
                                    intensityPercentText = input.filter { it.isDigit() }
                                }
                            },
                            label = stringResource(R.string.light_cue_intensity_label),
                            keyboardType = KeyboardType.Number
                        )
                        CompactNumberField(
                            modifier = Modifier.weight(1f),
                            value = fadeMsText,
                            onValueChange = { input ->
                                onLiteConfigAttempt {
                                    fadeMsText = input.filter { it.isDigit() }
                                }
                            },
                            label = stringResource(R.string.light_cue_fade_label),
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                if (cueType == EditableLightCueType.STROBE) {
                    CompactNumberField(
                        modifier = Modifier.fillMaxWidth(),
                        value = hzText,
                        onValueChange = { input ->
                            onLiteConfigAttempt {
                                hzText = input.filter { it.isDigit() || it == '.' || it == ',' }
                            }
                        },
                        label = stringResource(R.string.light_cue_strobe_hz_label),
                        keyboardType = KeyboardType.Decimal
                    )
                }

                if (cueType != EditableLightCueType.BLACKOUT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.light_cue_duration_label),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                durationPresetValues.forEach { presetMs ->
                                    TimingPresetChip(
                                        label = presetMs.toPresetLabel(),
                                        selected = durationPresetMs == presetMs,
                                        onClick = {
                                            onLiteConfigAttempt {
                                                durationPresetMs = presetMs
                                                if (presetMs == 0L) {
                                                    fadeOutPresetMs = 0L
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.light_cue_fade_out_label),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                fadeOutPresetValues.forEach { presetMs ->
                                    TimingPresetChip(
                                        label = presetMs.toPresetLabel(),
                                        selected = fadeOutPresetMs == presetMs,
                                        enabled = durationPresetMs > 0L,
                                        onClick = {
                                            onLiteConfigAttempt {
                                                if (durationPresetMs > 0L) {
                                                    fadeOutPresetMs = presetMs
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (cueType == EditableLightCueType.BLACKOUT) {
                    CompactNumberField(
                        modifier = Modifier.fillMaxWidth(),
                        value = fadeMsText,
                        onValueChange = { input ->
                            onLiteConfigAttempt {
                                fadeMsText = input.filter { it.isDigit() }
                            }
                        },
                        label = stringResource(R.string.light_cue_fade_label),
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onLiteConfigAttempt {
                        val normalizedIntensity = (intensityPercentText.toFloatOrNull() ?: 100f)
                            .coerceIn(0f, 100f) / 100f
                        val normalizedFadeMs = fadeMsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        val normalizedHz = hzText
                            .replace(',', '.')
                            .toFloatOrNull()
                            ?.coerceAtLeast(0.1f)
                            ?: DEFAULT_STROBE_HZ
                        val normalizedDurationMs = durationPresetMs.takeIf { it > 0L }
                        val normalizedFadeOutMs = fadeOutPresetMs
                            .takeIf { durationPresetMs > 0L && it > 0L }

                        val cue = when (cueType) {
                            EditableLightCueType.COLOR -> LightCue(
                                timeMs = safeMarkerTimeMs,
                                action = LightAction.Color(argb = colorArgb),
                                intensity = normalizedIntensity,
                                fadeMs = normalizedFadeMs,
                                durationMs = normalizedDurationMs,
                                fadeOutMs = normalizedFadeOutMs
                            )
                            EditableLightCueType.STROBE -> LightCue(
                                timeMs = safeMarkerTimeMs,
                                action = LightAction.Strobe(hz = normalizedHz),
                                intensity = 1f,
                                fadeMs = 0L,
                                durationMs = normalizedDurationMs,
                                fadeOutMs = normalizedFadeOutMs
                            )
                            EditableLightCueType.BLACKOUT -> LightCue(
                                timeMs = safeMarkerTimeMs,
                                action = LightAction.Blackout,
                                intensity = 1f,
                                fadeMs = normalizedFadeMs
                            )
                        }

                        SmpLightCueBridge.upsertCueAtTime(
                            context = context,
                            trackUriString = trackUri,
                            cue = cue
                        )
                        onSaved()
                    }
                }
            ) {
                Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) {
                    TextButton(
                        onClick = {
                            onLiteConfigAttempt {
                                SmpLightCueBridge.deleteCueAtTime(
                                    context = context,
                                    trackUriString = trackUri,
                                    timeMs = safeMarkerTimeMs
                                )
                                onSaved()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.lyrics_editor_delete), color = Color(0xFFFF8A80))
                    }
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            }
        },
        containerColor = Color(0xFF222222)
    )

    if (showTimelineConfigProDialog) {
        AlertDialog(
            onDismissRequest = { showTimelineConfigProDialog = false },
            title = { Text(text = sTimelineProDialogTitle, color = Color.White) },
            text = { Text(text = sTimelineProDialogMessage, color = Color.White) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimelineConfigProDialog = false
                        openUpgradeToPro()
                    }
                ) {
                    Text(sUpgradeToPro, color = Color(0xFF80CBC4))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimelineConfigProDialog = false }) {
                    Text(stringResource(R.string.common_close), color = Color(0xFFB0BEC5))
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

@Composable
private fun LightColorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { CompactChipText(label) }
    )
}

@Composable
private fun TimingPresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { CompactChipText(label) }
    )
}

@Composable
private fun CompactChipText(label: String) {
    Text(text = label, fontSize = 12.sp)
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

private fun Long.toPresetLabel(): String {
    return if (this <= 0L) "0" else "$this ms"
}

private fun formatTimelineLightCueTime(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
