package com.patrick.lrcreader.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.TextPrompterChordPaletteStore
import com.patrick.lrcreader.core.TextPrompterDisplaySettings
import com.patrick.lrcreader.core.TextPrompterDisplaySettingsStore
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.exo.R

/** Shared Library/Prompter edit session; the existing text identity is retained on save. */
@Composable
internal fun EditScrollingTextDialog(
    textSongId: String,
    transposeSemitones: Int? = null,
    onTransposeSemitonesChange: (Int) -> Unit = {},
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val song = remember(textSongId) {
        TextSongRepository.get(context, textSongId)
    } ?: return
    val paletteKey = TextPrompterChordPaletteStore.textSongKey(textSongId)
    val settingsKey = TextPrompterDisplaySettingsStore.textSongKey(textSongId)
    var title by remember(textSongId) { mutableStateOf(song.title) }
    var contentValue by remember(textSongId) {
        mutableStateOf(TextFieldValue(song.content, TextRange(song.content.length)))
    }
    var paletteInput by remember(textSongId) {
        mutableStateOf(
            paletteKey?.let { TextPrompterChordPaletteStore.get(context, it) }
                .orEmpty().joinToString(" ")
        )
    }
    var alignment by remember(textSongId) {
        mutableStateOf(
            (settingsKey?.let { TextPrompterDisplaySettingsStore.get(context, it) }
                ?: TextPrompterDisplaySettings()).alignment
        )
    }

    ScrollingTextEditorDialog(
        show = true,
        dialogTitle = stringResource(R.string.quickplaylists_edit_prompter_title),
        title = title,
        contentValue = contentValue,
        confirmLabel = stringResource(R.string.common_save),
        confirmEnabled = title.isNotBlank(),
        onTitleChange = { title = it },
        onContentValueChange = { contentValue = it },
        alignment = alignment,
        onAlignmentChange = { alignment = it },
        transposeSemitones = transposeSemitones,
        onTransposeSemitonesChange = onTransposeSemitonesChange,
        onDismiss = onDismiss,
        onConfirm = {
            if (title.isNotBlank()) {
                TextSongRepository.update(
                    context = context,
                    id = textSongId,
                    title = title.trim(),
                    content = contentValue.text.trim()
                )
                paletteKey?.let {
                    TextPrompterChordPaletteStore.save(
                        context, it, parseTextPrompterChordPaletteInput(paletteInput)
                    )
                }
                settingsKey?.let {
                    TextPrompterDisplaySettingsStore.saveAlignment(
                        context, it, alignment
                    )
                }
                onSaved()
            }
        },
        paletteInput = paletteInput,
        paletteChords = parseTextPrompterChordPaletteInput(paletteInput),
        onPaletteInputChange = { paletteInput = it }
    )
}
