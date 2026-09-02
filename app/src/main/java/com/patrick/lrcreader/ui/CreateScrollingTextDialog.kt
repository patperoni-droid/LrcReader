package com.patrick.lrcreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.exo.R

@Composable
internal fun CreateScrollingTextDialog(
    show: Boolean,
    title: String,
    contentValue: TextFieldValue,
    onTitleChange: (String) -> Unit,
    onContentValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ScrollingTextEditorDialog(
        show = show,
        dialogTitle = stringResource(R.string.quickplaylists_new_prompter_title),
        title = title,
        contentValue = contentValue,
        confirmLabel = stringResource(R.string.common_ok),
        confirmEnabled = title.isNotBlank() && contentValue.text.isNotBlank(),
        onTitleChange = onTitleChange,
        onContentValueChange = onContentValueChange,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
