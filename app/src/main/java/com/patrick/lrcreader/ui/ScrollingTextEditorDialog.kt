package com.patrick.lrcreader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.core.TextPrompterAlignment
import com.patrick.lrcreader.exo.R

internal const val SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG = "scrolling_text_editor_title"
internal const val SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG = "scrolling_text_editor_content"
internal const val SCROLLING_TEXT_EDITOR_DISMISS_TAG = "scrolling_text_editor_dismiss"
internal const val SCROLLING_TEXT_EDITOR_CONFIRM_TAG = "scrolling_text_editor_confirm"
internal const val SCROLLING_TEXT_EDITOR_PALETTE_FIELD_TAG = "scrolling_text_editor_palette"
internal const val SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX = "scrolling_text_editor_chord_"
internal const val SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG = "scrolling_text_editor_format_button"
internal const val SCROLLING_TEXT_EDITOR_FORMAT_PANEL_TAG = "scrolling_text_editor_format_panel"
internal const val SCROLLING_TEXT_EDITOR_ALIGNMENT_START_TAG = "scrolling_text_editor_alignment_start"
internal const val SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG = "scrolling_text_editor_alignment_center"
internal const val SCROLLING_TEXT_EDITOR_TRANSPOSE_DECREASE_TAG = "scrolling_text_editor_transpose_decrease"
internal const val SCROLLING_TEXT_EDITOR_TRANSPOSE_INCREASE_TAG = "scrolling_text_editor_transpose_increase"
internal const val PROMPTER_TRANSPOSE_MIN = -11
internal const val PROMPTER_TRANSPOSE_MAX = 11

internal fun stepPrompterTransposition(current: Int, delta: Int): Int =
    (current.toLong() + delta)
        .coerceIn(PROMPTER_TRANSPOSE_MIN.toLong(), PROMPTER_TRANSPOSE_MAX.toLong())
        .toInt()

internal fun formatPrompterTransposition(semitones: Int): String =
    if (semitones > 0) "+$semitones" else semitones.toString()

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
    alignment: TextPrompterAlignment = TextPrompterAlignment.START,
    onAlignmentChange: (TextPrompterAlignment) -> Unit = {},
    paletteInput: String? = null,
    paletteChords: List<String> = emptyList(),
    onPaletteInputChange: (String) -> Unit = {},
    transposeSemitones: Int? = null,
    onTransposeSemitonesChange: (Int) -> Unit = {}
) {
    if (!show) return
    val contentFocusRequester = remember { FocusRequester() }
    var isContentFocused by remember { mutableStateOf(false) }
    var isFormatPanelOpen by remember { mutableStateOf(false) }
    var isMarkupPaletteOpen by remember { mutableStateOf(false) }
    // Same phone/tablet breakpoint as SmpAdaptive; no keyboard-dependent layout rule.
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val contentInteractions = remember { MutableInteractionSource() }
    val visibility = scrollingTextEditorVisibility(
        isPhone, isContentFocused, isMarkupPaletteOpen, isFormatPanelOpen
    )

    fun collapsePhoneTools() {
        if (isPhone) {
            isMarkupPaletteOpen = false
            isFormatPanelOpen = false
        }
    }

    LaunchedEffect(isPhone, contentInteractions) {
        if (isPhone) {
            contentInteractions.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press && isContentFocused) collapsePhoneTools()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val focusManager = LocalFocusManager.current
        val toggleMarkup: () -> Unit = {
            isMarkupPaletteOpen = !isMarkupPaletteOpen
            isFormatPanelOpen = false
        }
        val toggleAlignment: () -> Unit = {
            isFormatPanelOpen = !isFormatPanelOpen
            isMarkupPaletteOpen = false
            contentFocusRequester.requestFocus()
        }
        val dismissEditor: () -> Unit = {
            focusManager.clearFocus(force = true)
            onDismiss()
        }
        val confirmEditor: () -> Unit = {
            focusManager.clearFocus(force = true)
            onConfirm()
        }
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
            if (visibility.showHeader) {
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

            if (paletteInput != null && visibility.showChords) {
                if (visibility.showHeader) {
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

                if (isPhone) {
                    if (paletteChords.isNotEmpty()) {
                        if (visibility.showHeader) {
                            Spacer(Modifier.height(6.dp))
                        }
                        PrompterChordPaletteRow(
                            chords = paletteChords,
                            onChordClick = { chord ->
                                onContentValueChange(
                                    editOrInsertPrompterChord(contentValue, chord)
                                )
                                contentFocusRequester.requestFocus()
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = toggleMarkup,
                    modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
                ) {
                    Icon(Icons.Default.TextFormat,
                        contentDescription = stringResource(R.string.prompter_editor_text_colors),
                        tint = if (isPhone) Color(0xFF80CBC4) else Color.White)
                }
                IconButton(
                    onClick = toggleAlignment,
                    modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
                        .testTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG)
                ) {
                    Icon(Icons.AutoMirrored.Filled.FormatAlignLeft,
                        contentDescription = stringResource(R.string.prompter_format_alignment_label),
                        tint = if (isPhone) Color(0xFF80CBC4) else Color.White)
                }
                if (isPhone) {
                    Spacer(Modifier.weight(1f))
                } else {
                    if (paletteInput != null && visibility.showChords) {
                        PrompterChordPaletteRow(
                            chords = paletteChords,
                            onChordClick = { chord ->
                                onContentValueChange(
                                    editOrInsertPrompterChord(contentValue, chord)
                                )
                                contentFocusRequester.requestFocus()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                transposeSemitones?.let { semitones ->
                    PrompterTranspositionControl(
                        semitones = semitones,
                        onSemitonesChange = onTransposeSemitonesChange
                    )
                }
                IconButton(
                    onClick = confirmEditor,
                    enabled = confirmEnabled,
                    modifier = Modifier.size(48.dp).testTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
                ) {
                    Icon(Icons.Default.Check, contentDescription = confirmLabel,
                        tint = if (confirmEnabled) Color.White else Color.White.copy(alpha = 0.38f))
                }
                IconButton(
                    onClick = dismissEditor,
                    modifier = Modifier.size(48.dp).testTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel),
                        tint = Color(0xFFB0BEC5))
                }
            }

            if (visibility.showMarkupPanel) {
                PrompterMarkupPalette { transform ->
                    onContentValueChange(transform(contentValue))
                    isMarkupPaletteOpen = false
                    collapsePhoneTools()
                    contentFocusRequester.requestFocus()
                }
            }

            if (visibility.showAlignmentPanel) {
                ScrollingTextFormatPanel(
                    alignment = alignment,
                    onAlignmentChange = { selectedAlignment ->
                        onAlignmentChange(selectedAlignment)
                        isFormatPanelOpen = false
                        collapsePhoneTools()
                        contentFocusRequester.requestFocus()
                    }
                )
                Spacer(Modifier.height(4.dp))
            }

            OutlinedTextField(
                value = contentValue,
                onValueChange = {
                    collapsePhoneTools()
                    onContentValueChange(it)
                },
                interactionSource = if (isPhone) contentInteractions else null,
                label = { Text(stringResource(R.string.quickplaylists_prompter_text_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .onFocusChanged {
                        val focused = scrollingTextEditorContentFocused(isPhone, it.isFocused, it.hasFocus)
                        if (focused && !isContentFocused) collapsePhoneTools()
                        isContentFocused = focused
                    }
                    .focusRequester(contentFocusRequester)
                    .testTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG),
                minLines = 10
            )


        }
    }
}

@Composable
internal fun PrompterTranspositionControl(
    semitones: Int,
    onSemitonesChange: (Int) -> Unit
) {
    val formattedSemitones = formatPrompterTransposition(semitones)
    val valueDescription = stringResource(
        R.string.prompter_transposition_value,
        formattedSemitones
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(
            onClick = {
                onSemitonesChange(stepPrompterTransposition(semitones, -1))
            },
            enabled = semitones > PROMPTER_TRANSPOSE_MIN,
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
                .focusProperties { canFocus = false }
                .testTag(SCROLLING_TEXT_EDITOR_TRANSPOSE_DECREASE_TAG)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.prompter_transposition_decrease),
                tint = if (semitones > PROMPTER_TRANSPOSE_MIN) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.38f)
                }
            )
        }
        Text(
            text = formattedSemitones,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(30.dp)
                .semantics {
                    contentDescription = valueDescription
                }
        )
        IconButton(
            onClick = {
                onSemitonesChange(stepPrompterTransposition(semitones, 1))
            },
            enabled = semitones < PROMPTER_TRANSPOSE_MAX,
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
                .focusProperties { canFocus = false }
                .testTag(SCROLLING_TEXT_EDITOR_TRANSPOSE_INCREASE_TAG)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.prompter_transposition_increase),
                tint = if (semitones < PROMPTER_TRANSPOSE_MAX) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.38f)
                }
            )
        }
    }
}

@Composable
internal fun PrompterChordPaletteRow(
    chords: List<String>,
    onChordClick: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chords.forEach { chord ->
            TextButton(
                onClick = { onChordClick(chord) },
                modifier = Modifier
                    .testTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + chord)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .focusProperties { canFocus = false },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("[$chord]", color = Color(0xFF80CBC4), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ScrollingTextFormatPanel(
    alignment: TextPrompterAlignment,
    onAlignmentChange: (TextPrompterAlignment) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SCROLLING_TEXT_EDITOR_FORMAT_PANEL_TAG),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2B3238)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.prompter_format_alignment_label),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = alignment == TextPrompterAlignment.START,
                onClick = { onAlignmentChange(TextPrompterAlignment.START) },
                label = { Text(stringResource(R.string.prompter_alignment_start)) },
                modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_START_TAG)
            )
            FilterChip(
                selected = alignment == TextPrompterAlignment.CENTER,
                onClick = { onAlignmentChange(TextPrompterAlignment.CENTER) },
                label = { Text(stringResource(R.string.prompter_alignment_center)) },
                modifier = Modifier.testTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG)
            )
        }
    }
}


@Composable
private fun PrompterMarkupPalette(onApply: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    val commands = listOf(
        PrompterMarkupCommand.TITLE to R.string.common_title_label,
        PrompterMarkupCommand.SECTION to R.string.prompter_markup_section,
        PrompterMarkupCommand.VERSE to R.string.prompter_markup_verse,
        PrompterMarkupCommand.CHORUS to R.string.prompter_markup_chorus,
        PrompterMarkupCommand.COMMENT to R.string.prompter_markup_comment,
        PrompterMarkupCommand.BOLD to R.string.prompter_markup_bold,
        PrompterMarkupCommand.ITALIC to R.string.prompter_markup_italic,
        PrompterMarkupCommand.DIVIDER to R.string.prompter_markup_divider
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        commands.forEach { (command, labelId) ->
            val label = stringResource(labelId)
            TextButton(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).focusProperties { canFocus = false },
                onClick = { onApply { insertPrompterMarkup(it, command, label) } }
            ) {
                Text(label,
                    fontWeight = if (command == PrompterMarkupCommand.BOLD) FontWeight.Bold else null,
                    fontStyle = if (command == PrompterMarkupCommand.ITALIC) FontStyle.Italic else null)

            }
        }
    }
    val colors = listOf(
        PrompterTextColor.YELLOW to R.string.lyrics_editor_color_yellow,
        PrompterTextColor.ORANGE to R.string.quickplaylists_group_color_orange,
        PrompterTextColor.RED to R.string.light_color_red,
        PrompterTextColor.BLUE to R.string.light_color_blue,
        PrompterTextColor.GREEN to R.string.light_color_green,
        PrompterTextColor.WHITE to R.string.lyrics_editor_color_none
    )
    val placeholder = stringResource(R.string.quickplaylists_prompter_text_label)
    Text(stringResource(R.string.lyrics_editor_color_section), color = Color.LightGray, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        colors.forEach { (color, labelId) ->
            TextButton(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).focusProperties { canFocus = false },
                onClick = { onApply {
                    if (color == PrompterTextColor.WHITE) removePrompterColor(it)
                    else applyPrompterColor(it, color, placeholder)
                } }
            ) {
                if (color == PrompterTextColor.WHITE) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else Box(Modifier.size(20.dp)
                    .background(resolvePrompterTextColor(color, Color.White), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(labelId), color = Color.White)

            }
        }
    }
}
