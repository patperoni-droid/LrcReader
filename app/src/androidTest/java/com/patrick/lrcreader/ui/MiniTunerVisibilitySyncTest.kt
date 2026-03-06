package com.patrick.lrcreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.patrick.lrcreader.core.MiniTunerVisibilityStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniTunerVisibilitySyncTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun waitUntilDescriptionAppears(description: String, timeoutMillis: Long = 12_000L) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule
                    .onAllNodesWithContentDescription(description, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun miniTunerVisibility_toggleInQuickPlaylists_reflectedInMixer() {
        val context = composeRule.activity
        composeRule.runOnUiThread {
            MiniTunerVisibilityStore.setVisible(context, false)
        }

        composeRule.setContent {
            QuickPlaylistsScreen(
                onPlaySong = { _, _, _ -> },
                refreshKey = 0
            )
        }

        composeRule.runOnUiThread {
            MiniTunerVisibilityStore.setVisible(context, true)
        }
        waitUntilDescriptionAppears("Masquer l'accordeur mini")

        composeRule.setContent {
            MixerHomePreviewScreen()
        }

        waitUntilDescriptionAppears("Masquer l'accordeur mini")
        composeRule.runOnUiThread {
            MiniTunerVisibilityStore.setVisible(context, false)
        }
        waitUntilDescriptionAppears("Afficher l'accordeur mini")

        composeRule.setContent {
            QuickPlaylistsScreen(
                onPlaySong = { _, _, _ -> },
                refreshKey = 0
            )
        }

        waitUntilDescriptionAppears("Afficher l'accordeur mini")
        composeRule
            .onNodeWithContentDescription("Afficher l'accordeur mini", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.runOnUiThread {
            MiniTunerVisibilityStore.setVisible(context, false)
        }
    }
}
