package com.patrick.lrcreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import com.patrick.lrcreader.exo.R

internal data class AudioLyricsChordProToolbarVisibility(
    val showTimingControl: Boolean,
    val showFullChordPalette: Boolean
)

internal const val AUDIO_LYRICS_TOOLBAR_ESSENTIAL_WIDTH_DP = 302
private const val AUDIO_LYRICS_EDITOR_HORIZONTAL_PADDING_DP = 32

internal fun audioLyricsToolbarRemainingWidthDp(screenWidthDp: Int): Int =
    screenWidthDp -
        AUDIO_LYRICS_EDITOR_HORIZONTAL_PADDING_DP -
        AUDIO_LYRICS_TOOLBAR_ESSENTIAL_WIDTH_DP

internal fun audioLyricsChordProToolbarVisibility(
    hasTimedLines: Boolean,
    tabletMode: Boolean
): AudioLyricsChordProToolbarVisibility = AudioLyricsChordProToolbarVisibility(
    showTimingControl = hasTimedLines,
    showFullChordPalette = tabletMode
)

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
    onTransposeSemitonesChange: (Int) -> Unit,
    tabletMode: Boolean,
    paletteChords: List<String>
) {
    val visibility = audioLyricsChordProToolbarVisibility(hasTimedLines, tabletMode)
    val boldPlaceholder = stringResource(R.string.prompter_markup_bold)
    val italicPlaceholder = stringResource(R.string.prompter_markup_italic)
    val chordActionDescription = stringResource(R.string.lyrics_editor_chord_action)
    val primaryActionColor = Color(0xFF80CBC4)
    val disabledActionColor = primaryActionColor.copy(alpha = 0.38f)
    var showChordInput by remember { mutableStateOf(false) }
    var chordInput by remember { mutableStateOf("") }
    var restoreEditorFocus by remember { mutableStateOf(false) }
    val editingEnabled = !showTimings

    LaunchedEffect(showChordInput, restoreEditorFocus) {
        if (!showChordInput && restoreEditorFocus) {
            onRequestEditorFocus()
            restoreEditorFocus = false
        }
    }

    fun applyEditingTransform(transform: (TextFieldValue) -> TextFieldValue) {
        onContentValueChange(transform(contentValue))
        onRequestEditorFocus()
    }

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
                tint = if (visibility.showTimingControl) {
                    primaryActionColor
                } else {
                    disabledActionColor
                }
            )
        }
        IconButton(
            onClick = {
                applyEditingTransform {
                    insertPrompterMarkup(
                        it,
                        PrompterMarkupCommand.BOLD,
                        boldPlaceholder
                    )
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
                    insertPrompterMarkup(
                        it,
                        PrompterMarkupCommand.ITALIC,
                        italicPlaceholder
                    )
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

        if (visibility.showFullChordPalette) {
            PrompterChordPaletteRow(
                chords = paletteChords,
                onChordClick = { chord ->
                    applyEditingTransform { editOrInsertPrompterChord(it, chord) }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        PrompterTranspositionControl(
            semitones = transposeSemitones,
            onSemitonesChange = onTransposeSemitonesChange
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
