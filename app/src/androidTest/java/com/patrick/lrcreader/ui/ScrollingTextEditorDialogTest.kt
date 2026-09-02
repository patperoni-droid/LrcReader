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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ScrollingTextEditorDialogTest {

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
    fun creationDialogRequiresNonEmptyContent() {
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
            TestEditor()
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }

    @Composable
    private fun TestEditor(
        show: Boolean = true,
        dialogTitle: String = "Editor",
        contentValue: TextFieldValue = TextFieldValue("Content"),
        confirmEnabled: Boolean = true,
        onContentValueChange: (TextFieldValue) -> Unit = {},
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit = {}
    ) {
        ScrollingTextEditorDialog(
            show = show,
            dialogTitle = dialogTitle,
            title = "Title",
            contentValue = contentValue,
            confirmLabel = "Save",
            confirmEnabled = confirmEnabled,
            onTitleChange = {},
            onContentValueChange = onContentValueChange,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}
