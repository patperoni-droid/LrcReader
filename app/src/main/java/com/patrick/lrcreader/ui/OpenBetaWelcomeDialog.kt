package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.exo.R

@Composable
fun OpenBetaWelcomeDialog(
    onDismiss: () -> Unit,
    onSendFeedback: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.open_beta_welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.open_beta_welcome_status),
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.open_beta_welcome_evolving))
                Text(stringResource(R.string.open_beta_welcome_feedback_message))
                Text(
                    text = stringResource(R.string.open_beta_welcome_thanks),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSendFeedback) {
                Text(stringResource(R.string.open_beta_send_feedback))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
