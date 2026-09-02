package com.patrick.lrcreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
    ScrollingTextEditorDialog(
        show = show,
        dialogTitle = stringResource(R.string.quickplaylists_new_prompter_title),
        title = title,
        content = content,
        confirmLabel = stringResource(R.string.common_ok),
        confirmEnabled = title.isNotBlank() && content.isNotBlank(),
        onTitleChange = onTitleChange,
        onContentChange = onContentChange,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
