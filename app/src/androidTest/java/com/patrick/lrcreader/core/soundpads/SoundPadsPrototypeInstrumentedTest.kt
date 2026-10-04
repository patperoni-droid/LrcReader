@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.patrick.lrcreader.core.soundpads

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.*
import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.dj.DjEngine
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run on an isolated emulator only; creates files/prefs and starts the real audio engines. */
@RunWith(AndroidJUnit4::class)
class SoundPadsPrototypeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var pads: List<SoundPad>
    private lateinit var engine: SoundPadsPrototypeEngine
    private var main: ExoPlayer? = null

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun waitFor(message: String, condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) {
            while (!condition()) delay(20)
        }
        assertTrue(message, condition())
    }
    @Before fun setup() {
        pads = runBlocking { SoundPadsPrototypeFiles.defaults(context) }
        onMain {
            PlaybackCoordinator.onPadsStop()
            engine = SoundPadsPrototypeEngine(context)
            PlaybackCoordinator.stopPlayer = { main?.pause() }
            PlaybackCoordinator.stopDj = { DjEngine.stopDj(0) }
            PlaybackCoordinator.stopFiller = { FillerSoundManager.fadeOutAndStop(0) }
            PlaybackCoordinator.requestStartPlayer()
            PlaybackCoordinator.onPlayerStop()
        }
    }
    @After fun cleanup() {
        onMain {
            engine.release()
            AudioEngine.release()
            DjEngine.release()
            FillerSoundManager.fadeOutAndStop(0)
            PlaybackCoordinator.onPlayerStop()
            PlaybackCoordinator.onDjStop()
            PlaybackCoordinator.onFillerStop()
            PlaybackCoordinator.stopPlayer = null
            PlaybackCoordinator.stopDj = null
            PlaybackCoordinator.stopFiller = null
        }
    }

    @Test fun clipsRetriggersAndImmediateStopLeaveNoGhostVoice() {
        assertTrue(pads.all { File(it.audioPath).isFile && it.audioPath.startsWith(context.filesDir.path) })
        assertTrue(pads.all { it.inMs == 0L && requireNotNull(it.outMs) > 2000L })
        // The short clip is explicit test input, never a default bank/import limit.
        onMain { engine.trigger(pads[0].copy(outMs = 2000L)) }
        waitFor("audio callback", { engine.state.value.samples.isNotEmpty() })
        waitFor("clip stops naturally", { engine.state.value.phase == SoundPadsPrototypeEngine.Phase.IDLE })
        onMain {
            repeat(20) { engine.trigger(pads[it % 2]) }
            engine.stopAll()
        }
        Thread.sleep(1000)
        assertEquals(SoundPadsPrototypeEngine.Phase.IDLE, engine.state.value.phase)
        assertNull(PlaybackCoordinator.activePadId.value)
        assertNull(engine.state.value.padId)
        // Still usable after canceling a rapid burst.
        onMain { engine.trigger(pads[1]) }
        waitFor("pad B starts", { engine.state.value.phase == SoundPadsPrototypeEngine.Phase.PLAYING })
        assertEquals(pads[1].padId, engine.state.value.padId)
    }

    @Test fun padsPreserveActualMainPlayerTimeMediaVolumeAndSource() {
        onMain {
            main = AudioEngine.getPlayer(context) {}
            main!!.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(pads[0].audioPath))))
            main!!.prepare()
            main!!.play()
            PlaybackCoordinator.requestStartPlayer()
        }
        waitFor("Player audible state", { var value = false; onMain { value = main!!.isPlaying }; value })
        var start = 0L
        var gain = 0f
        onMain { start = main!!.currentPosition; gain = main!!.volume }
        exerciseOverlay(PlaybackCoordinator.Source.Player)
        onMain {
            assertTrue(main!!.isPlaying)
            assertTrue(main!!.currentPosition > start)
            assertEquals(gain, main!!.volume, 0f)
            assertEquals(Uri.fromFile(File(pads[0].audioPath)), main!!.currentMediaItem!!.localConfiguration!!.uri)
        }
    }

    @Test fun padsPreserveActualDj() {
        onMain {
            DjEngine.init(context)
            DjEngine.setMasterVolume(0.25f)
            DjEngine.selectTrackFromList(Uri.fromFile(File(pads[0].audioPath)).toString(), "Sound Pads test")
        }
        waitFor("DJ started", { var value = false; onMain { value = DjEngine.isPlaying() }; value })
        val uri = DjEngine.state.value.playingUri
        val time = DjEngine.state.value.currentPositionMs
        exerciseOverlay(PlaybackCoordinator.Source.Dj)
        onMain { assertTrue(DjEngine.isPlaying()) }
        assertEquals(uri, DjEngine.state.value.playingUri)
        assertEquals(0.25f, DjEngine.state.value.masterLevel, 0f)
        assertTrue(DjEngine.state.value.currentPositionMs > time)
    }

    @Test fun padsPreserveActualFiller() {
        runBlocking {
            withContext(Dispatchers.IO) {
                FillerSoundPrefs.setUseCustomFolder(context, false)
                FillerSoundPrefs.saveFillerUri(context, Uri.fromFile(File(pads[0].audioPath)))
                FillerSoundPrefs.saveFillerVolume(context, 0.25f)
            }
            withContext(Dispatchers.Main) {
                // Same host entry point as FillerSoundScreen: the manager alone does not claim the source.
                PlaybackCoordinator.requestStartFiller()
                FillerSoundManager.startFromUi(context)
            }
        }
        waitFor("Filler started", { var value = false; onMain { value = FillerSoundManager.isPlaying() }; value })
        var start = 0
        onMain { start = FillerSoundManager.getCurrentPositionMs() }
        exerciseOverlay(PlaybackCoordinator.Source.Filler)
        onMain {
            assertTrue(FillerSoundManager.isPlaying())
            assertTrue(FillerSoundManager.getCurrentPositionMs() > start)
        }
        assertEquals(0.25f, FillerSoundPrefs.getFillerVolume(context), 0f)
    }

    private fun exerciseOverlay(source: PlaybackCoordinator.Source) {
        repeat(10) { index ->
            val count = engine.state.value.samples.size
            // Includes same-pad retriggers and A/B alternation.
            val pad = pads[(index / 2) % 2]
            onMain { engine.trigger(pad) }
            waitFor("sink callback $index", { engine.state.value.samples.size > count })
            assertEquals(source, PlaybackCoordinator.activeSource.value)
            if (index == 3) onMain { engine.setGlobalUiLevel(0f) }
            if (index == 4) onMain { engine.setGlobalUiLevel(0.5f) }
            Thread.sleep(80)
        }
        onMain { engine.stopAll() }
        Thread.sleep(500)
        assertNull(PlaybackCoordinator.activePadId.value)
        assertEquals(source, PlaybackCoordinator.activeSource.value)
        Log.i("SOUND_PADS_TEST", "RESULT source=$source samples=${engine.state.value.samples} underruns=${engine.state.value.underruns}")
    }
}
