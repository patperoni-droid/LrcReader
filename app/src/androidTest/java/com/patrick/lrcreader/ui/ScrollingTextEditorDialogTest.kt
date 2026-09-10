package com.patrick.lrcreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.TextPrompterAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ScrollingTextEditorDialogTest {

    @Test
    fun formatEntryRemainsVisibleInNormalAndFocusedModes() {
        composeRule.setContent {
            TestEditor()
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }

    @Test
    fun formatPanelChangesAlignmentWithoutChangingContentSelectionOrFocus() {
        var alignment by mutableStateOf(TextPrompterAlignment.START)
        var contentValue by mutableStateOf(
            TextFieldValue("Je voulais te dire", selection = TextRange(3, 10))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it },
                alignment = alignment,
                onAlignmentChange = { alignment = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_EMPTY_CHORD_BUTTON_TAG)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_START_TAG).assertIsSelected()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(TextPrompterAlignment.CENTER, alignment)
            assertEquals("Je voulais te dire", contentValue.text)
            assertEquals(TextRange(3, 10), contentValue.selection)
        }
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).assertIsFocused()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_EMPTY_CHORD_BUTTON_TAG)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG).assertIsSelected()
    }

    @Test
    fun startCanBeSelectedFromExistingCenterDraft() {
        var alignment by mutableStateOf(TextPrompterAlignment.CENTER)

        composeRule.setContent {
            TestEditor(
                alignment = alignment,
                onAlignmentChange = { alignment = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG).assertIsSelected()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_START_TAG).performClick()

        composeRule.runOnIdle { assertEquals(TextPrompterAlignment.START, alignment) }
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).assertIsFocused()
    }

    @Test
    fun dismissAfterAlignmentChangeDoesNotConfirmDraft() {
        var alignment by mutableStateOf(TextPrompterAlignment.START)
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            TestEditor(
                alignment = alignment,
                onAlignmentChange = { alignment = it },
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(TextPrompterAlignment.CENTER, alignment)
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Test
    fun confirmReadsCurrentAlignmentDraft() {
        var alignment by mutableStateOf(TextPrompterAlignment.START)
        var confirmedAlignment: TextPrompterAlignment? = null

        composeRule.setContent {
            TestEditor(
                alignment = alignment,
                onAlignmentChange = { alignment = it },
                onConfirm = { confirmedAlignment = alignment }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_FORMAT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_ALIGNMENT_CENTER_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(TextPrompterAlignment.CENTER, confirmedAlignment)
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialTextIsDisplayed() {
        val contentValue = TextFieldValue(
            text = "Initial content",
            selection = TextRange(7)
        )

        composeRule.setContent {
            TestEditor(contentValue = contentValue)
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsDisplayed()
            .assertTextContains("Initial content")
    }

    @Test
    fun typingUpdatesContentValueText() {
        var contentValue by mutableStateOf(
            TextFieldValue("Initial", selection = TextRange(7))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInput(" text")

        composeRule.runOnIdle {
            assertEquals("Initial text", contentValue.text)
            assertEquals(TextRange(12), contentValue.selection)
        }
    }

    @Test
    fun cursorPositionSurvivesRecomposition() {
        var contentValue by mutableStateOf(TextFieldValue("Cursor text"))
        var recompositionToken by mutableIntStateOf(0)

        composeRule.setContent {
            TestEditor(
                dialogTitle = "Editor $recompositionToken",
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInputSelection(TextRange(3))
        composeRule.runOnIdle { recompositionToken++ }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(TextRange(3), contentValue.selection)
        }
    }

    @Test
    fun selectionRangeRemainsCoherent() {
        var contentValue by mutableStateOf(TextFieldValue("Select this text"))

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInputSelection(TextRange(0, 6))

        composeRule.runOnIdle {
            assertEquals(TextRange(0, 6), contentValue.selection)
        }
    }

    @Test
    fun typingReplacesCurrentSelection() {
        var contentValue by mutableStateOf(TextFieldValue("Je voulais te dire"))

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        val contentField = composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
        contentField.performTextInputSelection(TextRange(3, 10))
        contentField.performTextInput("X")

        composeRule.runOnIdle {
            assertEquals("Je X te dire", contentValue.text)
            assertEquals(TextRange(4), contentValue.selection)
        }
    }

    @Test
    fun openingAnotherTextReplacesPreviousTextAndSelection() {
        var show by mutableStateOf(true)
        var contentValue by mutableStateOf(
            TextFieldValue("First text", selection = TextRange(2))
        )

        composeRule.setContent {
            TestEditor(
                show = show,
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.runOnIdle {
            show = false
            contentValue = TextFieldValue("Second text", selection = TextRange(11))
            show = true
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertTextContains("Second text")
        composeRule.runOnIdle {
            assertEquals(TextRange(11), contentValue.selection)
        }
    }

    @Test
    fun confirmUsesContentValueText() {
        var contentValue by mutableStateOf(TextFieldValue("Saved text"))
        var confirmedText: String? = null

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it },
                onConfirm = { confirmedText = contentValue.text }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals("Saved text", confirmedText)
        }
    }

    @Test
    fun libraryEditorAllowsEmptyContentAndConfirms() {
        var confirmed = false

        composeRule.setContent {
            TestEditor(
                dialogTitle = "Library editor",
                contentValue = TextFieldValue(),
                confirmEnabled = true,
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithText("Library editor").assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun playlistEditorRequiresNonEmptyContent() {
        var confirmed = false

        composeRule.setContent {
            TestEditor(
                dialogTitle = "Playlist editor",
                contentValue = TextFieldValue(),
                confirmEnabled = false,
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithText("Playlist editor").assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsNotEnabled()
        composeRule.runOnIdle { assertFalse(confirmed) }
    }

    @Test
    fun creationDialogShowsAutomaticPaletteEntryAndRequiresNonEmptyContent() {
        composeRule.setContent {
            CreateScrollingTextDialog(
                show = true,
                title = "Title",
                contentValue = TextFieldValue(),
                onTitleChange = {},
                onContentValueChange = {},
                onDismiss = {},
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_EMPTY_CHORD_BUTTON_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun emptyChordButtonCreatesFirstChordAndUpdatesAutomaticPalette() {
        var contentValue by mutableStateOf(
            TextFieldValue("Je pars ce soir", selection = TextRange(8))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_EMPTY_CHORD_BUTTON_TAG)
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals("Je pars []ce soir", contentValue.text)
            assertEquals(TextRange(9), contentValue.selection)
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInput("Am")
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()
    }

    @Test
    fun creationDialogInsertsAutomaticPaletteChordAtCursor() {
        var contentValue by mutableStateOf(
            TextFieldValue("Je voulais [C/E]te dire", selection = TextRange(3))
        )

        composeRule.setContent {
            CreateScrollingTextDialog(
                show = true,
                title = "Title",
                contentValue = contentValue,
                onTitleChange = {},
                onContentValueChange = { contentValue = it },
                onDismiss = {},
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "C/E")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Je [C/E]voulais [C/E]te dire", contentValue.text)
            assertEquals(TextRange(8), contentValue.selection)
        }
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsFocused()
    }

    @Test
    fun contentFocusHidesHeaderFieldsAndKeepsChordButtonsAndActionsVisible() {
        composeRule.setContent {
            TestEditor(
                dialogTitle = "Edit scrolling text",
                contentValue = TextFieldValue("[Am] [F] [C] [G]")
            )
        }

        composeRule.onNodeWithText("Edit scrolling text").assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit scrolling text").assertDoesNotExist()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsDisplayed()
            .assertIsFocused()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }

    @Test
    fun losingContentFocusRestoresHeaderFieldsWithoutLosingValues() {
        var title by mutableStateOf("Ma chanson")
        var contentValue by mutableStateOf(TextFieldValue("Je [Am]voulais te dire"))
        var confirmed = false

        composeRule.setContent {
            TestEditor(
                dialogTitle = "Edit scrolling text",
                title = title,
                contentValue = contentValue,
                onTitleChange = { title = it },
                onContentValueChange = { contentValue = it },
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit scrolling text").assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG)
            .assertIsDisplayed()
            .assertTextContains("Ma chanson")
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertTextContains("Je [Am]voulais te dire")
        composeRule.runOnIdle {
            assertEquals("Ma chanson", title)
            assertEquals("Je [Am]voulais te dire", contentValue.text)
            assertTrue(confirmed)
        }
    }

    @Test
    fun longContentRemainsAvailableInEditor() {
        val longContent = List(80) { index -> "Line $index with scrolling text" }
            .joinToString("\n")

        composeRule.setContent {
            TestEditor(contentValue = TextFieldValue(longContent))
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsDisplayed()
            .assertTextContains("Line 79 with scrolling text", substring = true)
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }

    @Test
    fun dismissButtonInvokesCallback() {
        var dismissed = false

        composeRule.setContent {
            TestEditor(onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun keyboardFocusKeepsConfirmationVisible() {
        composeRule.setContent {
            TestEditor(dialogTitle = "Historical editor")
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Historical editor").assertDoesNotExist()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_EMPTY_CHORD_BUTTON_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun textChangeUpdatesDisplayedAutomaticChordButtons() {
        var contentValue by mutableStateOf(TextFieldValue("[Am] [F]"))

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextReplacement("[C/E] [G7sus4]")

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "C/E")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "G7sus4")
            .assertIsDisplayed()
    }

    @Test
    fun chordClickInsertsChordProAtCursorAndRestoresContentFocus() {
        var contentValue by mutableStateOf(
            TextFieldValue("Je voulais [Am]", selection = TextRange(3))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInputSelection(TextRange(3))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .performClick()

        composeRule.runOnIdle {
            assertEquals("Je [Am]voulais [Am]", contentValue.text)
            assertEquals(TextRange(7), contentValue.selection)
        }
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsFocused()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun paletteAndActionsRemainVisibleAfterMovingToEndOfLongContent() {
        val longContent = List(99) { index -> "Long line $index" }
            .plus("Long line 99 [Am] [F]")
            .joinToString("\n")
        var contentValue by mutableStateOf(
            TextFieldValue(longContent, selection = TextRange(0))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextInputSelection(TextRange(longContent.length))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(contentValue.text.endsWith("Long line 99 [Am] [F][Am]"))
            assertEquals(TextRange(contentValue.text.length), contentValue.selection)
        }
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsFocused()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun keyboardFocusKeepsPaletteAndActionsVisible() {
        composeRule.setContent {
            TestEditor(
                contentValue = TextFieldValue("[Am] [F] [C] [G]")
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun successiveChordClicksInsertAtUpdatedCursor() {
        var contentValue by mutableStateOf(
            TextFieldValue("[Am] [F] [G]\nBonjour", selection = TextRange(13))
        )

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "Am").performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "F").performClick()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CHORD_TAG_PREFIX + "G").performClick()

        composeRule.runOnIdle {
            assertEquals("[Am] [F] [G]\n[Am][F][G]Bonjour", contentValue.text)
            assertEquals(TextRange(23), contentValue.selection)
        }
    }

    @Test
    fun confirmReadsCurrentTextWithoutManualPaletteState() {
        var contentValue by mutableStateOf(TextFieldValue("Content"))
        var confirmedDraft: String? = null

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it },
                onConfirm = { confirmedDraft = contentValue.text }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextReplacement("[Dm] [C/E]")
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals("[Dm] [C/E]", confirmedDraft)
        }
    }

    @Test
    fun dismissAfterTextChangeDoesNotConfirmDraft() {
        var contentValue by mutableStateOf(TextFieldValue("[Am] [F]"))
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            TestEditor(
                contentValue = contentValue,
                onContentValueChange = { contentValue = it },
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .performTextReplacement("[Dm] [G]")
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Composable
    private fun TestEditor(
        show: Boolean = true,
        dialogTitle: String = "Editor",
        title: String = "Title",
        contentValue: TextFieldValue = TextFieldValue("Content"),
        confirmEnabled: Boolean = true,
        onTitleChange: (String) -> Unit = {},
        onContentValueChange: (TextFieldValue) -> Unit = {},
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit = {},
        alignment: TextPrompterAlignment = TextPrompterAlignment.START,
        onAlignmentChange: (TextPrompterAlignment) -> Unit = {}
    ) {
        ScrollingTextEditorDialog(
            show = show,
            dialogTitle = dialogTitle,
            title = title,
            contentValue = contentValue,
            confirmLabel = "Save",
            confirmEnabled = confirmEnabled,
            onTitleChange = onTitleChange,
            onContentValueChange = onContentValueChange,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            alignment = alignment,
            onAlignmentChange = onAlignmentChange
        )
    }
}
