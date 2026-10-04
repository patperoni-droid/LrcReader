package com.patrick.lrcreader.core.soundpads

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.core.PadsBusController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit two-process probe. Run write, force-stop the isolated package, then run read. */
class SoundPadsBusRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val marker get() = context.getSharedPreferences("pads_restart_test", 0)
    private fun phase(expected: String) {
        assumeTrue(context.packageName.contains("soundpadsprototype"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("padsRestartProbe") == expected)
    }
    @Test fun writeBeforeRestart() = runBlocking {
        phase("write")
        assertFalse("Finish or clean the previous probe first", marker.contains("padId"))
        PadsBusController.initialize(context)
        val oldLevel = PadsBusController.uiLevel.value
        val source = File(context.cacheDir, "pads-restart-source.mp3")
        context.assets.open("fond_sonore/FS_Lounge_Warm.mp3").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val store = SoundPadsStore(context)
        val id = store.add("Restart probe").last().padId
        store.import(id, Uri.fromFile(source))
        val pad = store.update(id, "Restart probe", 100L, 1500L, 0.37f).single { it.padId == id }
        assertTrue(source.delete())
        assertTrue(marker.edit().putString("padId", id).putString("audioPath", pad.audioPath)
            .putFloat("previousLevel", oldLevel).commit())
        instrumentation.runOnMainSync { PadsBusController.setUiLevel(context, 0.62f) }
        assertEquals(0.62f, PadsBusController.uiLevel.value, 0f)
        // apply() follows the other bus preferences. Do not force-kill its pending disk write.
        withContext(Dispatchers.IO) {
            val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/pads_volume_prefs.xml")
            withTimeout(5_000) {
                while (!prefsFile.isFile || !prefsFile.readText().contains("value=\"0.62\"")) delay(25)
            }
        }
    }
    @Test fun readAfterRestart() = runBlocking {
        phase("read")
        val id = requireNotNull(marker.getString("padId", null))
        val oldLevel = marker.getFloat("previousLevel", 0.5f)
        val store = SoundPadsStore(context)
        try {
            PadsBusController.initialize(context)
            assertEquals(0.62f, PadsBusController.uiLevel.value, 0f)
            val pad = store.load().single { it.padId == id }
            assertEquals(marker.getString("audioPath", null), pad.audioPath)
            assertEquals(0.37f, pad.volume, 0f)
            assertEquals(100L, pad.inMs)
            assertEquals(1500L, pad.outMs)
            assertTrue(File(pad.audioPath).isFile)
            instrumentation.runOnMainSync {
                val engine = SoundPadsPrototypeEngine(context)
                try {
                    engine.trigger(pad)
                    assertEquals(0.37f * 0.62f * 0.62f * 0.62f, engine.outputVolume, 0.00001f)
                } finally { engine.release() }
            }
        } finally {
            store.delete(id)
            instrumentation.runOnMainSync { PadsBusController.setUiLevel(context, oldLevel) }
            marker.edit().clear().commit()
        }
    }
}
