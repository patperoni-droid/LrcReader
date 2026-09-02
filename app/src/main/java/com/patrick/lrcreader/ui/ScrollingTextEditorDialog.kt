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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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

            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState())
            ) {
                if (paletteInput != null) {
                    OutlinedTextField(
                        value = paletteInput,
                        onValueChange = onPaletteInputChange,
                        label = { Text(stringResource(R.string.chords_palette_input_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SCROLLING_TEXT_EDITOR_PALETTE_FIELD_TAG),
                        singleLine = true
                    )

                    if (paletteChords.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
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
                        .heightIn(min = 260.dp)
                        .focusRequester(contentFocusRequester)
                        .testTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG),
                    minLines = 10
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG)
                ) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
                ) {
                    Text(confirmLabel, color = Color.White)
                }
            }
        }
    }
}
