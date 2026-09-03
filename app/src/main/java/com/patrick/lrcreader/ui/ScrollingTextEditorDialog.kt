package com.patrick.lrcreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrick.lrcreader.exo.R

internal const val SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG = "scrolling_text_editor_title"
internal const val SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG = "scrolling_text_editor_content"
internal const val SCROLLING_TEXT_EDITOR_DISMISS_TAG = "scrolling_text_editor_dismiss"
internal const val SCROLLING_TEXT_EDITOR_CONFIRM_TAG = "scrolling_text_editor_confirm"
internal const val SCROLLING_TEXT_EDITOR_PALETTE_FIELD_TAG = "scrolling_text_editor_palette"
internal const val SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX = "scrolling_text_editor_chord_"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScrollingTextEditorDialog(
    show: Boolean,
    dialogTitle: String,
    title: String,
    contentValue: TextFieldValue,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onTitleChange: (String) -> Unit,
    onContentValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    paletteInput: String? = null,
    paletteChords: List<String> = emptyList(),
    onPaletteInputChange: (String) -> Unit = {}
) {
    if (!show) return
    val contentFocusRequester = remember { FocusRequester() }
    var isContentFocused by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .navigationBarsPadding()
                .imePadding()
                .background(Color(0xFF222222), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            if (!isContentFocused) {
                Text(
                    text = dialogTitle,
                    color = Color.White,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.common_title_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG)
                )

                Spacer(Modifier.height(12.dp))
            }

            if (paletteInput != null) {
                if (!isContentFocused) {
                    OutlinedTextField(
                        value = paletteInput,
                        onValueChange = onPaletteInputChange,
                        label = { Text(stringResource(R.string.chords_palette_input_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SCROLLING_TEXT_EDITOR_PALETTE_FIELD_TAG),
                        singleLine = true
                    )
                }

                if (paletteChords.isNotEmpty()) {
                    if (!isContentFocused) {
                        Spacer(Modifier.height(6.dp))
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paletteChords.forEach { chord ->
                            TextButton(
                                onClick = {
                                    onContentValueChange(
                                        insertChordProAtSelection(contentValue, chord)
                                    )
                                    contentFocusRequester.requestFocus()
                                },
                                modifier = Modifier.testTag(
                                    SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + chord
                                ).defaultMinSize(minWidth = 0.dp, minHeight = 34.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("[$chord]", color = Color(0xFF80CBC4), fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = contentValue,
                onValueChange = onContentValueChange,
                label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .onFocusChanged { isContentFocused = it.isFocused }
                    .focusRequester(contentFocusRequester)
                    .testTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG),
                minLines = 10
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onDismiss()
                    },
                    modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG)
                ) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onConfirm()
                    },
                    enabled = confirmEnabled,
                    modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
                ) {
                    Text(confirmLabel, color = Color.White)
                }
            }
        }
    }
}
