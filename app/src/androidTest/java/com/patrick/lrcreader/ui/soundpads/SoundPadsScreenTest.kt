package com.patrick.lrcreader.ui.soundpads

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeFiles
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Controlled phone/tablet viewports on the isolated emulator package only. */
class SoundPadsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = SoundPadsStore(context)
    private lateinit var pad: SoundPad
    private var previousIds = emptySet<String>()
    @Before fun prepare() = runBlocking {
        previousIds = store.load().map { it.padId }.toSet()
        val empty = store.add("UI-${UUID.randomUUID()}").last()
        val source = SoundPadsPrototypeFiles.defaults(context).first()
        pad = store.import(empty.padId, Uri.fromFile(File(source.audioPath))).last()
    }
    @After fun cleanup() {
        if (compose.onAllNodesWithTag("soundpads-stop").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("soundpads-stop").performClick()
        }
        runBlocking { store.load().filterNot { it.padId in previousIds }.forEach { store.delete(it.padId) } }
    }
    private fun populate(count: Int) = runBlocking {
        pad = store.update(pad.padId, "Pad 01", 0L, pad.outMs, 0.7f, SoundPadsPalette[0]).single { it.padId == pad.padId }
        repeat(count - 1) { index ->
            val next = store.add("Pad %02d".format(index + 2)).last()
            val copied = store.import(next.padId, Uri.fromFile(File(pad.audioPath))).last()
            store.update(next.padId, next.name, 0L, copied.outMs, 0.7f, SoundPadsPalette[(index + 1) % SoundPadsPalette.size])
        }
    }
    private fun render(tablet: Boolean, width: Int, height: Int) {
        compose.setContent {
            val registry = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides registry, LocalDensity provides Density(1f)) {
                var visible by remember { mutableStateOf(true) }
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    if (visible) SoundPadsScreen(onClose = { visible = false }, tabletMode = tablet)
                    else Box(Modifier.testTag("soundpads-previous"))
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("soundpad-${pad.padId}").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tileMatcher() = SemanticsMatcher("pad tile") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("soundpad-") == true
    }
    private fun shot(name: String, tag: String = "soundpads-screen") {
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        val image = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(context.cacheDir, "soundpads-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun phoneShowsSixAndStopClearsActivePad() {
        populate(6)
        render(false, 400, 820)
        compose.onAllNodes(tileMatcher()).assertCountEquals(6)
        shot("phone")
        compose.onNodeWithTag("soundpad-${pad.padId}").performClick()
        compose.waitUntil(5_000) { PlaybackCoordinator.activePadId.value == pad.padId }
        shot("phone-active")
        compose.onNodeWithTag("soundpads-stop").performClick()
        assertNull(PlaybackCoordinator.activePadId.value)
    }
    @Test fun tabletPortraitShowsTwelveAndUsesSidePanel() {
        populate(12)
        render(true, 720, 1050)
        compose.onAllNodes(tileMatcher()).assertCountEquals(12)
        shot("tablet-portrait")
        compose.onNodeWithTag("soundpad-${pad.padId}").performTouchInput { longClick() }
        compose.onNodeWithTag("soundpads-settings").assertIsDisplayed()
        assertNull(PlaybackCoordinator.activePadId.value)
        shot("tablet-portrait-settings")
    }
    @Test fun tabletLandscapeLongPressDoesNotPlayAndSettingsPersistColorTrimAndVolume() {
        populate(12)
        render(true, 1200, 800)
        compose.onAllNodes(tileMatcher()).assertCountEquals(12)
        shot("tablet-landscape")
        compose.onNodeWithTag("soundpad-${pad.padId}").performTouchInput { longClick() }
        compose.onNodeWithTag("soundpads-settings").assertIsDisplayed()
        compose.onNodeWithTag("soundpad-${pad.padId}").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.soundpads_ready)))
        assertNull(PlaybackCoordinator.activePadId.value)
        shot("tablet-landscape-settings")
        compose.onNodeWithTag("soundpads-test").performScrollTo().performClick()
        compose.waitUntil(5_000) { PlaybackCoordinator.activePadId.value == pad.padId }
        compose.onNodeWithTag("soundpads-settings-stop").performScrollTo().performClick()
        assertNull(PlaybackCoordinator.activePadId.value)
        compose.onNodeWithTag("soundpads-name").performTextReplacement("Edited pad")
        compose.onNodeWithTag("soundpads-in").performTextReplacement("100")
        compose.onNodeWithTag("soundpads-out").performTextReplacement("1500")
        compose.onNodeWithTag("soundpads-volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
        compose.onNodeWithTag("soundpads-color-5").performScrollTo().performClick()
        compose.onNodeWithTag("soundpads-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isEmpty() }
        val saved = runBlocking { SoundPadsStore(context).load().single { it.padId == pad.padId } }
        assertEquals("Edited pad", saved.name)
        assertEquals(100L, saved.inMs)
        assertEquals(1500L, saved.outMs)
        assertEquals(0.4f, saved.volume, 0.01f)
        assertEquals(SoundPadsPalette[5], saved.colorArgb)
        assertEquals(pad.audioPath, saved.audioPath)
        assertEquals(0, saved.pitchSemitones)
    }
    @Test fun selectingLaterEmptySlotReservesItsPositionWithoutChangingExistingPad() {
        render(false, 400, 820)
        compose.onNodeWithTag("soundpad-empty-5").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { store.load() }
        assertEquals(6, saved.size)
        assertEquals(pad, saved.first())
        assertTrue(saved.drop(1).all { it.audioPath.isEmpty() })
        compose.onNodeWithTag("soundpads-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("soundpad-${saved.last().padId}").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("soundpad-${saved.last().padId}").fetchSemanticsNode().boundsInRoot.top >
            compose.onNodeWithTag("soundpad-${pad.padId}").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun returningToPreviousRouteReleasesOnlyThePadVoice() {
        render(false, 400, 820)
        val mainSource = PlaybackCoordinator.activeSource.value
        compose.onNodeWithTag("soundpad-${pad.padId}").performClick()
        compose.waitUntil(5_000) { PlaybackCoordinator.activePadId.value == pad.padId }
        compose.onNodeWithTag("soundpads-back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-previous").fetchSemanticsNodes().isNotEmpty() }
        assertNull(PlaybackCoordinator.activePadId.value)
        assertEquals(mainSource, PlaybackCoordinator.activeSource.value)
    }
    @Test fun padsBeyondDefaultPhoneGridRemainReachable() {
        val last = runBlocking { var next = pad; repeat(15) { next = store.add("Extra $it").last() }; next }
        render(false, 400, 820)
        compose.onNodeWithTag("soundpads-grid").performScrollToIndex(15)
        compose.onNodeWithTag("soundpad-${last.padId}").assertIsDisplayed().performTouchInput { longClick() }
        compose.onNodeWithTag("soundpads-settings").assertIsDisplayed()
        assertNull(PlaybackCoordinator.activePadId.value)
    }
    @Test fun phoneAddOpensSheetAndDeletionRequiresConfirmation() {
        render(false, 400, 820)
        compose.onNodeWithTag("soundpads-add").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isNotEmpty() }
        shot("phone-settings", "soundpads-settings")
        val added = runBlocking { store.load().last() }
        assertTrue(added.audioPath.isEmpty())
        compose.onNodeWithTag("soundpads-delete").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.soundpads_cancel)).performClick()
        assertTrue(runBlocking { store.load().any { it.padId == added.padId } })
        compose.onNodeWithTag("soundpads-delete").performScrollTo().performClick()
        compose.onNodeWithTag("soundpads-confirm-delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isEmpty() }
        assertFalse(runBlocking { store.load().any { it.padId == added.padId } })
        assertTrue(runBlocking { store.load().any { it.padId == pad.padId } })
    }
}
