package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrick.lrcreader.exo.R

@Composable
internal fun CreateScrollingTextDialog(
    show: Boolean,
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF222222)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.quickplaylists_new_prompter_title),
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        label = { Text(stringResource(R.string.common_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel), color = Color.White)
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text(stringResource(R.string.common_ok), color = Color.White)
                    }
                }
            }
        }
    }
}
