package com.patrick.lrcreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.exo.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsEditorBlankLinesInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneEditor_keepsBlankLinesAfterSyncTabRoundTrip() {
        verifyBlankLinesAfterSyncTabRoundTrip(
            compactEditorTabs = false,
            tabletFocusEditingMode = false
        )
    }

    @Test
    fun tabletEditor_keepsBlankLinesAfterSyncTabRoundTrip() {
        verifyBlankLinesAfterSyncTabRoundTrip(
            compactEditorTabs = true,
            tabletFocusEditingMode = true
        )
    }

    private fun verifyBlankLinesAfterSyncTabRoundTrip(
        compactEditorTabs: Boolean,
        tabletFocusEditingMode: Boolean
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exactDraft = "Couplet 1\n\nRefrain\n\n\nPont\n"
        val lyricsTabLabel = context.getString(
            if (compactEditorTabs) {
                R.string.lyrics_editor_tab_lyrics_short
            } else {
                R.string.lyrics_editor_tab_lyrics
            }
        )
        val syncTabLabel = context.getString(
            if (compactEditorTabs) {
                R.string.lyrics_editor_tab_sync_short
            } else {
                R.string.lyrics_editor_tab_sync
            }
        )

        composeRule.setContent {
            var rawDraft by remember { mutableStateOf(exactDraft) }
            var editingLines by remember { mutableStateOf(emptyList<LrcLine>()) }
            var currentTab by remember { mutableIntStateOf(0) }

            MaterialTheme {
                LyricsEditorSection(
                    highlightColor = MaterialTheme.colorScheme.primary,
                    currentTrackUri = null,
                    isEditingLyrics = true,
                    onCloseEditor = {},
                    rawLyricsText = rawDraft,
                    onRawLyricsTextChange = { rawDraft = it },
                    editingLines = editingLines,
                    onEditingLinesChange = { editingLines = it },
                    currentEditTab = currentTab,
                    onCurrentEditTabChange = { currentTab = it },
                    isPlaying = false,
                    positionMs = 0,
                    durationMs = 0,
                    onIsPlayingChange = {},
                    seekToMs = {},
                    onSaveSortedLines = {},
                    onPersistSucceeded = {},
                    onPersistLines = { true },
                    onDeletePersisted = { true },
                    compactEditorTabs = compactEditorTabs,
                    tabletFocusEditingMode = tabletFocusEditingMode
                )
            }
        }

        assertEditableTextEquals(exactDraft)
        composeRule.onNodeWithText(syncTabLabel).performClick()
        composeRule.onNodeWithText(lyricsTabLabel).performClick()
        assertEditableTextEquals(exactDraft)
    }

    private fun assertEditableTextEquals(expected: String) {
        composeRule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString(expected)
            )
        )
    }
}
