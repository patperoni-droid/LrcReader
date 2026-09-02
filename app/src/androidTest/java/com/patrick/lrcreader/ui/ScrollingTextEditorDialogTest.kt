package com.patrick.lrcreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScrollingTextEditorDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun libraryEditor_allowsEmptyContentAndConfirms() {
        var confirmed = false

        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Library editor",
                title = "Title",
                content = "",
                confirmLabel = "Save",
                confirmEnabled = true,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = {},
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
    fun playlistEditor_requiresNonEmptyContent() {
        var confirmed = false

        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Playlist editor",
                title = "Title",
                content = "",
                confirmLabel = "Save",
                confirmEnabled = false,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = {},
                onConfirm = { confirmed = true }
            )
        }

        composeRule.onNodeWithText("Playlist editor").assertIsDisplayed()
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsNotEnabled()
        composeRule.runOnIdle { assertFalse(confirmed) }
    }

    @Test
    fun emptyTitleDisablesConfirmation() {
        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Editor",
                title = "",
                content = "Content",
                confirmLabel = "Save",
                confirmEnabled = false,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = {},
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun dismissButtonInvokesCallback() {
        var dismissed = false

        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Editor",
                title = "Title",
                content = "Content",
                confirmLabel = "Save",
                confirmEnabled = true,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = { dismissed = true },
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_DISMISS_TAG).performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun longContentRemainsAvailableInEditor() {
        val longContent = List(80) { index -> "Line $index with scrolling text" }
            .joinToString("\n")

        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Editor",
                title = "Title",
                content = longContent,
                confirmLabel = "Save",
                confirmEnabled = true,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = {},
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONTENT_FIELD_TAG)
            .assertIsDisplayed()
            .assertTextContains("Line 79 with scrolling text", substring = true)
        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }

    @Test
    fun keyboardFocusKeepsConfirmationVisible() {
        composeRule.setContent {
            ScrollingTextEditorDialog(
                show = true,
                dialogTitle = "Editor",
                title = "Title",
                content = "Content",
                confirmLabel = "Save",
                confirmEnabled = true,
                onTitleChange = {},
                onContentChange = {},
                onDismiss = {},
                onConfirm = {}
            )
        }

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_TITLE_FIELD_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SCROLLING_TEXT_EDITOR_CONFIRM_TAG).assertIsDisplayed()
    }
}
