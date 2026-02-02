package com.patrick.lrcreader.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickPlaylistsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Smoke test ultra-simple :
     * vérifie que le framework de test Compose fonctionne.
     */
    @Test
    fun smoke_composeTestFramework_isOk() {
        composeRule.setContent {
            Text(
                text = "SMOKE_OK",
                modifier = Modifier.testTag("smoke_tag")
            )
        }

        composeRule.onNodeWithTag("smoke_tag").assertIsDisplayed()
    }

    /**
     * Test anti-crash :
     * - l'écran se compose
     * - le header existe
     * - cliquer dessus ne fait pas crasher l'app
     */
    @Test
    fun quickPlaylistsScreen_headerClick_doesNotCrash() {

        composeRule.setContent {
            QuickPlaylistsScreen(
                onPlaySong = { _, _, _ -> },
                refreshKey = 0,
                currentPlayingUri = null,
                selectedPlaylist = null,
                onSelectedPlaylistChange = {},
                onPlaylistColorChange = {},
                onRequestShowPlayer = {},
                indexAll = emptyList()
            )
        }

        // IMPORTANT :
        // le tag existe 2 fois -> on clique le premier node réellement cliquable
        composeRule
            .onAllNodesWithTag("quickplaylists_header", useUnmergedTree = true)
            .filter(hasClickAction())
            .onFirst()
            .performClick()
    }
}