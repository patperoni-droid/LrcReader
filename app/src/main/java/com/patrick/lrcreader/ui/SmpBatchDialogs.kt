package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.exo.R

@Composable
fun SmpPreparationNoticeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onContinue: (dontShowAgain: Boolean) -> Unit
) {
    if (!show) return

    var dontShowAgain by remember(show) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.smp_batch_notice_title),
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.smp_batch_notice_line_1),
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.smp_batch_notice_line_2),
                    color = Color(0xFFB0BEC5)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.smp_batch_notice_line_3),
                    color = Color(0xFFB0BEC5)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = stringResource(R.string.smp_batch_notice_hide_next_time),
                        color = Color.White
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onContinue(dontShowAgain) }) {
                Text(stringResource(R.string.smp_batch_notice_continue), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
            }
        },
        containerColor = Color(0xFF222222)
    )
}

@Composable
fun SmpBatchProgressDialog(
    show: Boolean,
    title: String,
    label: String,
    progress: Float?
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = title,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF222222),
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}
