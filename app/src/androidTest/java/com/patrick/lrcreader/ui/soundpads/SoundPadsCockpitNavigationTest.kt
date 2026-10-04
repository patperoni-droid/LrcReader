package com.patrick.lrcreader.ui.soundpads

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.patrick.lrcreader.exo.MainActivity
import com.patrick.lrcreader.exo.R
import org.junit.Rule
import org.junit.Test

/** Run only on an isolated, initialized tablet emulator; never on a user installation. */
class SoundPadsCockpitNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun authorizeAudioInIsolatedPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        assumeTrue("Requires an isolated, initialized tablet test package",
            packageName.contains("soundpadsprototype"))
        instrumentation.uiAutomation.grantRuntimePermission(packageName,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @Test fun tabletCockpitKeepsShortcutsAndSwitchesDirectlyBetweenPadsAndSources() {
        val pads = compose.activity.getString(R.string.soundpads_title)
        val filler = compose.activity.getString(R.string.tablet_split_menu_filler)
        val dj = compose.activity.getString(R.string.tablet_split_menu_dj)
        val player = compose.activity.getString(R.string.tab_player)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription(pads).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(pads).performClick().assertIsSelected()
        compose.onNodeWithTag("soundpads-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription(filler).assertIsDisplayed().performClick()
        compose.onNodeWithTag("soundpads-screen").assertDoesNotExist()
        compose.onNodeWithContentDescription(pads).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(dj).assertIsDisplayed().performClick()
        compose.onNodeWithTag("soundpads-screen").assertDoesNotExist()
        compose.onNodeWithContentDescription(pads).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(player).assertIsDisplayed().performClick()
        compose.onNodeWithTag("soundpads-screen").assertDoesNotExist()
        compose.onNodeWithContentDescription(pads).assertIsDisplayed().assertIsNotSelected()
    }
}
