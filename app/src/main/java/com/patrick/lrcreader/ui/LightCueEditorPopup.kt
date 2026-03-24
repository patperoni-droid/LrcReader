package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.light.LightAction
import com.patrick.lrcreader.core.light.LightCue
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpLightCueBridge

private const val POPUP_RED_ARGB = 0xFFFF0000L
private const val POPUP_BLUE_ARGB = 0xFF0000FFL
private const val POPUP_GREEN_ARGB = 0xFF00FF00L
private const val POPUP_WHITE_ARGB = 0xFFFFFFFFL
private const val DEFAULT_STROBE_HZ = 8f

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
    val safeMarkerTimeMs = markerTimeMs.coerceAtLeast(0L)
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

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(
                    R.string.light_cue_timeline_dialog_title,
                    formatTimelineLightCueTime(safeMarkerTimeMs)
                ),
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = cueType == EditableLightCueType.COLOR,
                        onClick = { cueType = EditableLightCueType.COLOR },
                        label = { Text(stringResource(R.string.light_cue_type_color)) }
                    )
                    FilterChip(
                        selected = cueType == EditableLightCueType.STROBE,
                        onClick = { cueType = EditableLightCueType.STROBE },
                        label = { Text(stringResource(R.string.light_cue_type_strobe)) }
                    )
                    FilterChip(
                        selected = cueType == EditableLightCueType.BLACKOUT,
                        onClick = { cueType = EditableLightCueType.BLACKOUT },
                        label = { Text(stringResource(R.string.light_cue_type_blackout)) }
                    )
                }

                if (cueType == EditableLightCueType.COLOR) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LightColorChip(
                            label = stringResource(R.string.light_color_red),
                            selected = colorArgb == POPUP_RED_ARGB,
                            onClick = { colorArgb = POPUP_RED_ARGB }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_blue),
                            selected = colorArgb == POPUP_BLUE_ARGB,
                            onClick = { colorArgb = POPUP_BLUE_ARGB }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_green),
                            selected = colorArgb == POPUP_GREEN_ARGB,
                            onClick = { colorArgb = POPUP_GREEN_ARGB }
                        )
                        LightColorChip(
                            label = stringResource(R.string.light_color_white),
                            selected = colorArgb == POPUP_WHITE_ARGB,
                            onClick = { colorArgb = POPUP_WHITE_ARGB }
                        )
                    }

                    OutlinedTextField(
                        value = intensityPercentText,
                        onValueChange = { input ->
                            intensityPercentText = input.filter { it.isDigit() }
                        },
                        label = { Text(stringResource(R.string.light_cue_intensity_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = fadeMsText,
                        onValueChange = { input ->
                            fadeMsText = input.filter { it.isDigit() }
                        },
                        label = { Text(stringResource(R.string.light_cue_fade_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (cueType == EditableLightCueType.STROBE) {
                    OutlinedTextField(
                        value = hzText,
                        onValueChange = { input ->
                            hzText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text(stringResource(R.string.light_cue_strobe_hz_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                if (cueType == EditableLightCueType.BLACKOUT) {
                    OutlinedTextField(
                        value = fadeMsText,
                        onValueChange = { input ->
                            fadeMsText = input.filter { it.isDigit() }
                        },
                        label = { Text(stringResource(R.string.light_cue_fade_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedIntensity = (intensityPercentText.toFloatOrNull() ?: 100f)
                        .coerceIn(0f, 100f) / 100f
                    val normalizedFadeMs = fadeMsText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    val normalizedHz = hzText
                        .replace(',', '.')
                        .toFloatOrNull()
                        ?.coerceAtLeast(0.1f)
                        ?: DEFAULT_STROBE_HZ

                    val cue = when (cueType) {
                        EditableLightCueType.COLOR -> LightCue(
                            timeMs = safeMarkerTimeMs,
                            action = LightAction.Color(argb = colorArgb),
                            intensity = normalizedIntensity,
                            fadeMs = normalizedFadeMs
                        )
                        EditableLightCueType.STROBE -> LightCue(
                            timeMs = safeMarkerTimeMs,
                            action = LightAction.Strobe(hz = normalizedHz),
                            intensity = 1f,
                            fadeMs = 0L
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
            ) {
                Text(stringResource(R.string.common_ok), color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) {
                    TextButton(
                        onClick = {
                            SmpLightCueBridge.deleteCueAtTime(
                                context = context,
                                trackUriString = trackUri,
                                timeMs = safeMarkerTimeMs
                            )
                            onSaved()
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
        label = { Text(label) }
    )
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
