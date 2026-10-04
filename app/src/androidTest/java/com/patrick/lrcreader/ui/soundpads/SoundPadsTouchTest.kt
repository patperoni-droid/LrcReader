package com.patrick.lrcreader.ui.soundpads

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.soundpads.SoundPad
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeFiles
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

/** Raw down/move/up events, not semantic clicks. Slight movement stays below scroll slop. */
class SoundPadsTouchTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = SoundPadsStore(context)
    private lateinit var pad: SoundPad
    private var oldIds = emptySet<String>()
    private var behindClicks = 0
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() } }
    private fun triggers(): Int = shell("logcat -d -v brief -s SOUND_PADS:I '*:S'")
        .lineSequence().count { it.contains("TRIGGER request=${pad.padId}:") }
    @Before fun prepare() = runBlocking {
        assumeTrue("Isolated emulator package required", context.packageName.contains("soundpadsprototype"))
        oldIds = store.load().map { it.padId }.toSet()
        val id = store.add("Touch regression").last().padId
        pad = store.import(id, Uri.fromFile(File(SoundPadsPrototypeFiles.defaults(context).first().audioPath))).last()
    }
    @After fun cleanup() {
        if (!::pad.isInitialized) return
        if (compose.onAllNodesWithTag("soundpads-stop").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("soundpads-stop").performClick()
        runBlocking { store.load().filterNot { it.padId in oldIds }.forEach { store.delete(it.padId) } }
    }
    private fun render(tablet: Boolean, width: Int, height: Int) {
        compose.setContent {
            val registry = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides context, LocalActivityResultRegistryOwner provides registry,
                LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    Box(Modifier.fillMaxSize().clickable { behindClicks++ })
                    SoundPadsScreen(onClose = {}, tabletMode = tablet)
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("soundpad-${pad.padId}").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun twentyMovingTaps(tablet: Boolean, width: Int, height: Int) {
        render(tablet, width, height)
        val start = triggers()
        val tile = compose.onNodeWithTag("soundpad-${pad.padId}")
        repeat(20) { index ->
            tile.performTouchInput {
                val x = when (index % 5) { 0 -> 12f; 1 -> this.width - 12f; else -> center.x }
                val y = when (index % 5) { 0 -> 12f; 1 -> this.height - 12f; 2 -> this.height * 0.7f; else -> center.y }
                down(Offset(x, y))
                moveBy(Offset(1f, 1f), delayMillis = 30)
                advanceEventTime(30)
                up()
            }
            assertEquals("Touch ${index + 1} must call the actual engine once", start + index + 1, triggers())
        }
        assertEquals(0, behindClicks)
    }
    private fun movingLongPress(tablet: Boolean, width: Int, height: Int) {
        render(tablet, width, height)
        val start = triggers()
        repeat(3) {
            compose.onNodeWithTag("soundpad-${pad.padId}").performTouchInput {
                down(center); moveBy(Offset(1f, 1f), delayMillis = 40)
                advanceEventTime(700); up()
            }
            compose.onNodeWithTag("soundpads-settings").assertIsDisplayed()
            assertEquals("Long press must never play", start, triggers())
            compose.onNodeWithContentDescription(context.getString(R.string.soundpads_prototype_close)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("soundpads-settings").fetchSemanticsNodes().isEmpty() }
        }
        assertEquals(0, behindClicks)
    }
    @Test fun phoneTwentyTapsWithNaturalMovement() = twentyMovingTaps(false, 360, 700)
    @Test fun tabletLandscapeTwentyTapsWithNaturalMovement() = twentyMovingTaps(true, 1200, 800)
    @Test fun tabletPortraitTwentyTapsWithNaturalMovement() = twentyMovingTaps(true, 720, 1050)
    @Test fun phoneLongPressWithNaturalMovement() = movingLongPress(false, 360, 700)
    @Test fun tabletLongPressWithNaturalMovement() = movingLongPress(true, 720, 1050)
    @Test fun scrollingAndBlankAreasNeverTriggerPadsOrTheUnderlyingRoute() {
        runBlocking { repeat(15) { store.add("Scroll $it") } }
        render(false, 360, 700)
        val start = triggers()
        compose.onNodeWithTag("soundpads-screen").performTouchInput { click(Offset(center.x, 32f)) }
        compose.onNodeWithTag("soundpads-grid").performTouchInput { swipeUp() }
        compose.onNodeWithTag("soundpad-${pad.padId}").assertIsNotDisplayed()
        assertEquals(start, triggers())
        assertEquals(0, behindClicks)
    }
}
