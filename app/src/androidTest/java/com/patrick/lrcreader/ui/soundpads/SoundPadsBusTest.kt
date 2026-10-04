package com.patrick.lrcreader.ui.soundpads

import android.graphics.Bitmap
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.PadsBusController
import com.patrick.lrcreader.core.PadsVolumePrefs
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.soundpads.*
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.GlobalMixScreen
import com.patrick.lrcreader.ui.MixerHomePreviewScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Media3 output volume driven through both actual bus controls, on an isolated emulator. */
class SoundPadsBusTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var engine: SoundPadsPrototypeEngine
    private lateinit var pad: SoundPad
    private var oldLevel = 0.5f
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    @Before fun prepare() {
        pad = runBlocking {
            PadsBusController.initialize(context)
            SoundPadsPrototypeFiles.defaults(context).first().copy(volume = 0.4f)
        }
        oldLevel = PadsBusController.uiLevel.value
        onMain { PadsBusController.setUiLevel(context, 0.5f); engine = SoundPadsPrototypeEngine(context) }
    }
    @After fun cleanup() = onMain {
        engine.release()
        PadsBusController.setUiLevel(context, oldLevel)
    }
    private fun snapshot(name: String) {
        val bitmap = compose.onNodeWithTag("pads-bus-test-root").captureToImage().asAndroidBitmap()
        File(context.cacheDir, "pads-bus-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun twoBusSurfacesShareOnePersistedFaderAndUpdateThePlayingPadImmediately() {
        var analog by mutableStateOf(true)
        compose.setContent {
            val registryOwner = LocalActivityResultRegistryOwner.current
                ?: (LocalContext.current as ActivityResultRegistryOwner)
            CompositionLocalProvider(LocalContext provides context, LocalDensity provides Density(1f),
                LocalActivityResultRegistryOwner provides registryOwner) {
                Box(Modifier.requiredSize(360.dp, 700.dp).testTag("pads-bus-test-root")) {
                    if (analog) MixerHomePreviewScreen(showBackButton = false)
                    else GlobalMixScreen(playerLevel = 0.7f, onPlayerLevelChange = {},
                        djLevel = 0.8f, onDjLevelChange = {}, fillerLevel = 0.6f,
                        onFillerLevelChange = {}, onBack = {})
                }
            }
        }
        onMain { engine.trigger(pad) }
        compose.waitUntil(10_000) { engine.state.value.phase == SoundPadsPrototypeEngine.Phase.PLAYING }
        val analogFader = compose.onNodeWithTag("pads-bus-fader").assertIsDisplayed()
        compose.onNodeWithTag("pads-bus-status").assertTextEquals(context.getString(R.string.soundpads_playing))
        snapshot("phone-active")
        onMain { assertEquals(0.05f, engine.outputVolume, 0.00001f) }
        analogFader.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.7f)) }
        analogFader.performTouchInput {
            swipe(center, center + Offset(0f, height * 0.25f), 250)
        }
        assertTrue(PadsBusController.uiLevel.value < 0.7f)
        analogFader.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.25f)) }
        onMain {
            assertEquals(0.00625f, engine.outputVolume, 0.00001f)
            assertEquals(pad.padId, engine.state.value.padId)
            assertEquals(SoundPadsPrototypeEngine.Phase.PLAYING, engine.state.value.phase)
        }
        assertEquals(0.25f, PadsVolumePrefs.load(context), 0f)
        assertEquals(0.4f, pad.volume, 0f)
        analog = false
        val globalFader = compose.onNodeWithTag("pads-globalmix-fader").performScrollTo().assertIsDisplayed()
        assertEquals(0.25f, PadsBusController.uiLevel.value, 0f)
        globalFader.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.8f)) }
        onMain { assertEquals(0.2048f, engine.outputVolume, 0.00001f) }
        snapshot("globalmix")
        analog = true
        compose.onNodeWithTag("pads-bus-fader").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(0.8f, 0f..1f)))
        compose.onNodeWithContentDescription(context.getString(R.string.soundpads_bus_stop)).performClick()
        assertEquals(SoundPadsPrototypeEngine.Phase.IDLE, engine.state.value.phase)
        assertNull(PlaybackCoordinator.activePadId.value)
        compose.onNodeWithTag("pads-bus-status").assertTextEquals(context.getString(R.string.soundpads_ready))
    }
    @Test fun zeroAndFullBusGainPreserveIndividualVolumeAndInvalidInputDoesNotChangeIt() {
        onMain {
            engine.trigger(pad)
            PadsBusController.setUiLevel(context, 1f)
            assertEquals(0.4f, engine.outputVolume, 0f)
            PadsBusController.setUiLevel(context, 0f)
            assertEquals(0f, engine.outputVolume, 0f)
            PadsBusController.setUiLevel(context, Float.NaN)
            assertEquals(0f, PadsBusController.uiLevel.value, 0f)
            assertEquals(0f, engine.outputVolume, 0f)
            engine.setGlobalUiLevel(0.5f)
            assertEquals(0.5f, PadsBusController.uiLevel.value, 0f)
            assertEquals(0.05f, engine.outputVolume, 0.00001f)
            PadsBusController.stopAll()
            assertEquals(SoundPadsPrototypeEngine.Phase.IDLE, engine.state.value.phase)
        }
        assertEquals(0.4f, pad.volume, 0f)
        assertEquals(0.5f, PadsVolumePrefs.load(context), 0f)
    }
}
