package com.patrick.lrcreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.patrick.lrcreader.core.TextSongRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TextPrompterChordRenderingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        TextSongRepository.setInMemoryOnlyForTests(true)
        TextSongRepository.clearAll(composeRule.activity)
    }

    @After
    fun tearDown() {
        TextSongRepository.clearAll(composeRule.activity)
        TextSongRepository.setInMemoryOnlyForTests(false)
    }

    @Test
    fun repositoryUpdateRebuildsVisibleChordProWhileReaderStaysComposed() {
        val source = "Je [Am]pars ce [F]soir"
        val id = TextSongRepository.create(
            context = composeRule.activity,
            title = "Test",
            content = "Je pars ce soir"
        )

        composeRule.setContent {
            TextPrompterScreen(
                songId = "text:$id",
                onClose = {}
            )
        }

        composeRule.onNodeWithText("Am").assertDoesNotExist()
        composeRule.runOnIdle {
            TextSongRepository.update(
                context = composeRule.activity,
                id = id,
                title = "Test",
                content = source
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Am").assertIsDisplayed()
        composeRule.onNodeWithText("F").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(source, TextSongRepository.get(composeRule.activity, id)?.content)
        }
    }
}
