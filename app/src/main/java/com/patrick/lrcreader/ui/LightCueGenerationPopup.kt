package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.light.LightCueAutoGenerator
import com.patrick.lrcreader.exo.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineLightCueGenerationPopup(
    durationMs: Long,
    onGenerate: (LightCueAutoGenerator.Style, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var selectedStyle by remember { mutableStateOf(LightCueAutoGenerator.Style.STANDARD) }
    var replaceExisting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(R.string.light_generate_dialog_title),
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.light_generate_style_label),
                    color = Color.White
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStyle == LightCueAutoGenerator.Style.SOFT,
                        onClick = { selectedStyle = LightCueAutoGenerator.Style.SOFT },
                        label = { Text(stringResource(R.string.light_generate_style_soft)) }
                    )
                    FilterChip(
                        selected = selectedStyle == LightCueAutoGenerator.Style.STANDARD,
                        onClick = { selectedStyle = LightCueAutoGenerator.Style.STANDARD },
                        label = { Text(stringResource(R.string.light_generate_style_standard)) }
                    )
                    FilterChip(
                        selected = selectedStyle == LightCueAutoGenerator.Style.ENERGETIC,
                        onClick = { selectedStyle = LightCueAutoGenerator.Style.ENERGETIC },
                        label = { Text(stringResource(R.string.light_generate_style_energetic)) }
                    )
                }

                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.light_generate_replace_existing),
                        color = Color.White,
                        modifier = androidx.compose.ui.Modifier.weight(1f)
                    )
                    Switch(
                        checked = replaceExisting,
                        onCheckedChange = { replaceExisting = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(selectedStyle, replaceExisting) },
                enabled = durationMs > 0L
            ) {
                Text(stringResource(R.string.light_generate_action), color = Color(0xFF80CBC4))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
            }
        },
        containerColor = Color(0xFF222222)
    )
}
