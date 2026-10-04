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
import org.junit.After
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import com.patrick.lrcreader.core.PadsBusController
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.io.File

/** Run only on an isolated, initialized tablet emulator; never on a user installation. */
class SoundPadsCockpitNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var fixtureId: String? = null
    private var oldLevel = 0.5f
    @Before fun authorizeAudioInIsolatedPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        assumeTrue("Requires an isolated, initialized tablet test package",
            packageName.contains("soundpadsprototype"))
        runBlocking { PadsBusController.initialize(context) }
        oldLevel = PadsBusController.uiLevel.value
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
    @After fun restoreFixture() {
        compose.runOnIdle { PadsBusController.stopAll(); PadsBusController.setUiLevel(context, oldLevel) }
        fixtureId?.let { runBlocking { SoundPadsStore(context).delete(it) } }
    }

    @Test fun padKeepsPlayingWhileOpeningBusAndReturningToPads() {
        val pad = runBlocking {
            val store = SoundPadsStore(context)
            val id = store.add("Bus navigation test").last().padId
            fixtureId = id
            val file = SoundPadsPrototypeFiles.defaults(context).first().audioPath
            store.import(id, Uri.fromFile(File(file))).single { it.padId == id }
        }
        val padsLabel = compose.activity.getString(R.string.soundpads_title)
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(padsLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(padsLabel).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("soundpad-${pad.padId}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("soundpad-${pad.padId}").performClick()
        compose.waitUntil(10_000) { PlaybackCoordinator.activePadId.value == pad.padId }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.tablet_split_menu_main_bus)).performClick()
        compose.onNodeWithTag("pads-bus-fader").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.35f)) }
        assertEquals(pad.padId, PlaybackCoordinator.activePadId.value)
        compose.onNodeWithContentDescription(padsLabel).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("soundpad-${pad.padId}").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(pad.padId, PlaybackCoordinator.activePadId.value)
        assertEquals(0.35f, PadsBusController.uiLevel.value, 0f)
        compose.onNodeWithTag("soundpads-stop").performClick()
        assertNull(PlaybackCoordinator.activePadId.value)
    }

}
