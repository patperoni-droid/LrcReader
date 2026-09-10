package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.exo.R

internal data class AudioLyricsChordProToolbarVisibility(
    val showTimingControl: Boolean
)

internal data class AudioLyricsToolbarLayout(
    val compactFormatting: Boolean,
    val paletteDirectlyVisible: Boolean = true,
    val rowCount: Int = 1
)

internal const val AUDIO_LYRICS_TOOLBAR_COMPACT_WIDTH_DP = 302
internal const val AUDIO_LYRICS_TOOLBAR_DIRECT_WIDTH_DP = 350
private const val AUDIO_LYRICS_EDITOR_HORIZONTAL_PADDING_DP = 32

internal fun audioLyricsToolbarUsesCompactFormatting(screenWidthDp: Int): Boolean =
    screenWidthDp - AUDIO_LYRICS_EDITOR_HORIZONTAL_PADDING_DP <
        AUDIO_LYRICS_TOOLBAR_DIRECT_WIDTH_DP

internal fun audioLyricsToolbarLayout(screenWidthDp: Int): AudioLyricsToolbarLayout =
    AudioLyricsToolbarLayout(
        compactFormatting = audioLyricsToolbarUsesCompactFormatting(screenWidthDp)
    )

internal fun audioLyricsToolbarRemainingWidthDp(screenWidthDp: Int): Int {
    val requiredWidth = if (audioLyricsToolbarUsesCompactFormatting(screenWidthDp)) {
        AUDIO_LYRICS_TOOLBAR_COMPACT_WIDTH_DP
    } else {
        AUDIO_LYRICS_TOOLBAR_DIRECT_WIDTH_DP
    }
    return screenWidthDp - AUDIO_LYRICS_EDITOR_HORIZONTAL_PADDING_DP - requiredWidth
}

internal fun audioLyricsChordProToolbarVisibility(
    hasTimedLines: Boolean
): AudioLyricsChordProToolbarVisibility = AudioLyricsChordProToolbarVisibility(
    showTimingControl = hasTimedLines
)

internal fun shouldShowAudioLyricsChordPalette(
    showTimings: Boolean,
    paletteChords: List<String>
): Boolean = !showTimings && paletteChords.isNotEmpty()

internal fun toggleAudioLyricsTiming(current: Boolean, hasTimedLines: Boolean): Boolean =
    if (hasTimedLines) !current else current

@Composable
internal fun AudioLyricsChordProToolbar(
    hasTimedLines: Boolean,
    showTimings: Boolean,
    onShowTimingsChange: (Boolean) -> Unit,
    contentValue: TextFieldValue,
    onContentValueChange: (TextFieldValue) -> Unit,
    onRequestEditorFocus: () -> Unit,
    transposeSemitones: Int,
    onTransposeSemitonesChange: (Int) -> Unit
) {
    val visibility = audioLyricsChordProToolbarVisibility(hasTimedLines)
    val boldPlaceholder = stringResource(R.string.prompter_markup_bold)
    val italicPlaceholder = stringResource(R.string.prompter_markup_italic)
    val chordActionDescription = stringResource(R.string.lyrics_editor_chord_action)
    val colorActionDescription = stringResource(R.string.lyrics_editor_rich_color_action)
    val primaryActionColor = Color(0xFF80CBC4)
    val disabledActionColor = primaryActionColor.copy(alpha = 0.38f)
    var showChordInput by remember { mutableStateOf(false) }
    var showRichColorPalette by remember { mutableStateOf(false) }
    var showCompactFormattingMenu by remember { mutableStateOf(false) }
    var chordInput by remember { mutableStateOf("") }
    var restoreEditorFocus by remember { mutableStateOf(false) }
    val editingEnabled = !showTimings
    val colorEditingEnabled = editingEnabled && canEditPrompterColor(contentValue)

    LaunchedEffect(showChordInput, showRichColorPalette, restoreEditorFocus) {
        if (!showChordInput && !showRichColorPalette && restoreEditorFocus) {
            onRequestEditorFocus()
            restoreEditorFocus = false
        }
    }

    fun applyEditingTransform(transform: (TextFieldValue) -> TextFieldValue) {
        onContentValueChange(transform(contentValue))
        onRequestEditorFocus()
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compactFormatting = maxWidth < AUDIO_LYRICS_TOOLBAR_DIRECT_WIDTH_DP.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggleButton(
                checked = showTimings,
                onCheckedChange = {
                    onShowTimingsChange(toggleAudioLyricsTiming(showTimings, hasTimedLines))
                },
                enabled = visibility.showTimingControl,
                modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(
                        if (showTimings) R.string.lyrics_editor_hide_timings
                        else R.string.lyrics_editor_show_timings
                    ),
                    tint = if (visibility.showTimingControl) primaryActionColor
                    else disabledActionColor
                )
            }

            if (compactFormatting) {
                Box {
                    IconButton(
                        onClick = { showCompactFormattingMenu = true },
                        enabled = editingEnabled,
                        modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.lyrics_editor_formatting_action),
                            tint = if (editingEnabled) primaryActionColor else disabledActionColor
                        )
                    }
                    DropdownMenu(
                        expanded = showCompactFormattingMenu,
                        onDismissRequest = { showCompactFormattingMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prompter_markup_bold)) },
                            leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null) },
                            onClick = {
                                showCompactFormattingMenu = false
                                applyEditingTransform {
                                    insertPrompterMarkup(it, PrompterMarkupCommand.BOLD, boldPlaceholder)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prompter_markup_italic)) },
                            leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null) },
                            onClick = {
                                showCompactFormattingMenu = false
                                applyEditingTransform {
                                    insertPrompterMarkup(it, PrompterMarkupCommand.ITALIC, italicPlaceholder)
                                }
                            }
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = {
                        applyEditingTransform {
                            insertPrompterMarkup(it, PrompterMarkupCommand.BOLD, boldPlaceholder)
                        }
                    },
                    enabled = editingEnabled,
                    modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
                ) {
                    Icon(
                        Icons.Default.FormatBold,
                        contentDescription = stringResource(R.string.prompter_markup_bold),
                        tint = if (editingEnabled) primaryActionColor else disabledActionColor
                    )
                }
                IconButton(
                    onClick = {
                        applyEditingTransform {
                            insertPrompterMarkup(it, PrompterMarkupCommand.ITALIC, italicPlaceholder)
                        }
                    },
                    enabled = editingEnabled,
                    modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
                ) {
                    Icon(
                        Icons.Default.FormatItalic,
                        contentDescription = stringResource(R.string.prompter_markup_italic),
                        tint = if (editingEnabled) primaryActionColor else disabledActionColor
                    )
                }
            }

            TextButton(
                onClick = {
                    chordInput = selectedPrompterChord(contentValue).orEmpty()
                    showChordInput = true
                },
                enabled = editingEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .focusProperties { canFocus = false }
                    .semantics { contentDescription = chordActionDescription }
            ) {
                Text(
                    "[ ]",
                    color = if (editingEnabled) primaryActionColor else disabledActionColor,
                    fontSize = 13.sp
                )
            }

            IconButton(
                onClick = { showRichColorPalette = true },
                enabled = colorEditingEnabled,
                modifier = Modifier.size(48.dp).focusProperties { canFocus = false }
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = colorActionDescription,
                    tint = if (colorEditingEnabled) primaryActionColor else disabledActionColor
                )
            }

            Spacer(Modifier.weight(1f))

            PrompterTranspositionControl(
                semitones = transposeSemitones,
                onSemitonesChange = onTransposeSemitonesChange
            )
        }
    }

    if (showRichColorPalette) {
        val colors = listOf(
            PrompterTextColor.YELLOW to R.string.lyrics_editor_color_yellow,
            PrompterTextColor.ORANGE to R.string.quickplaylists_group_color_orange,
            PrompterTextColor.RED to R.string.light_color_red,
            PrompterTextColor.BLUE to R.string.light_color_blue,
            PrompterTextColor.GREEN to R.string.light_color_green,
            PrompterTextColor.WHITE to R.string.lyrics_editor_color_none
        )
        AlertDialog(
            onDismissRequest = {
                showRichColorPalette = false
                restoreEditorFocus = true
            },
            title = { Text(stringResource(R.string.lyrics_editor_color_section)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    colors.chunked(3).forEach { colorRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            colorRow.forEach { (color, labelRes) ->
                                val colorLabel = stringResource(labelRes)
                                IconButton(
                                    onClick = {
                                        val edited = if (color == PrompterTextColor.WHITE) {
                                            removePrompterColor(contentValue)
                                        } else {
                                            applyPrompterColor(contentValue, color, "")
                                        }
                                        onContentValueChange(edited)
                                        showRichColorPalette = false
                                        restoreEditorFocus = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .focusProperties { canFocus = false }
                                ) {
                                    if (color == PrompterTextColor.WHITE) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = colorLabel
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .size(24.dp)
                                                .background(
                                                    resolvePrompterTextColor(color, Color.White),
                                                    CircleShape
                                                )
                                                .border(
                                                    1.dp,
                                                    Color.White.copy(alpha = 0.6f),
                                                    CircleShape
                                                )
                                                .semantics {
                                                    contentDescription = "$colorActionDescription: $colorLabel"
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showRichColorPalette = false
                    restoreEditorFocus = true
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showChordInput) {
        AlertDialog(
            onDismissRequest = {
                showChordInput = false
                restoreEditorFocus = true
            },
            title = { Text(stringResource(R.string.lyrics_editor_chord_input_title)) },
            text = {
                OutlinedTextField(
                    value = chordInput,
                    onValueChange = { chordInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.lyrics_editor_chord_input_label)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChordInput = false
                        onContentValueChange(
                            editOrInsertPrompterChord(contentValue, chordInput)
                        )
                        restoreEditorFocus = true
                    },
                    enabled = isValidPrompterChordInput(chordInput)
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChordInput = false
                    restoreEditorFocus = true
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
