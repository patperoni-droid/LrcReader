package com.patrick.lrcreader.core.soundpads

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Run only with the isolated emulator applicationId, never a user library installation. */
class SoundPadsStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(test: suspend (SoundPadsStore, File, File) -> Unit) = runBlocking {
        val root = File(context.filesDir, "soundpads-store-test-${UUID.randomUUID()}")
        val source = File(context.filesDir, "source-${UUID.randomUUID()}.mp3")
        try {
            context.assets.open("fond_sonore/FS_Lounge_Warm.mp3").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            test(SoundPadsStore(context, root), root, source)
        } finally { root.deleteRecursively(); source.delete() }
    }

    @Test fun emptyAndExtensibleBankRestoresIdsAndEmptyPads() = fixture { store, root, _ ->
        assertTrue(store.load().isEmpty())
        repeat(25) { store.add("Pad $it") }
        val before = store.load()
        val after = SoundPadsStore(context, root).load()
        assertEquals(before, after)
        assertEquals(25, after.size)
        assertEquals(25, after.map { it.padId }.toSet().size)
        assertTrue(after.all { it.audioPath.isEmpty() && it.outMs == null && it.pitchSemitones == 0 })
    }

    @Test fun importedCopyAndAllSettingsSurviveStoreRecreationAndSourceDeletion() = fixture { store, root, source ->
        val id = store.add("Original").single().padId
        val imported = store.import(id, Uri.fromFile(source)).single()
        assertEquals(SoundPadsPrototypeFiles.audioDurationMs(source), imported.outMs)
        assertEquals(0L, imported.inMs)
        val changed = store.update(id, "Edited", 100L, 1500L, 0.3f).single()
        assertTrue(source.delete())
        assertEquals(changed, SoundPadsStore(context, root).load().single())
        assertTrue(File(changed.audioPath).isFile)
        store.update(id, "Edited", 100L, null, 0.3f)
        assertNull(SoundPadsStore(context, root).load().single().outMs)
    }

    @Test fun failedImportAndFailedBankWritePreserveOldFileAndMetadata() = fixture { store, root, source ->
        val id = store.add("Original").single().padId
        val old = store.import(id, Uri.fromFile(source)).single()
        val bytes = File(old.audioPath).readBytes()
        try {
            store.import(id, Uri.fromFile(File(root, "missing.mp3")))
            fail("Missing source must fail")
        } catch (_: java.io.FileNotFoundException) { }
        assertEquals(old, store.load().single())
        assertArrayEquals(bytes, File(old.audioPath).readBytes())
        // AtomicFile cannot create its temporary bank output: import must roll back the new audio.
        val blocked = File(root, "bank.json.new").apply {
            mkdir()
            // readFully removes stale .new files: use a non-empty directory it cannot delete.
            File(this, "blocker").writeText("test")
        }
        try {
            try { store.import(id, Uri.fromFile(source)); fail("Bank write must fail") }
            catch (_: java.io.IOException) { }
        } finally { blocked.deleteRecursively() }
        assertEquals(old, SoundPadsStore(context, root).load().single())
        assertArrayEquals(bytes, File(old.audioPath).readBytes())
        assertEquals(1, File(root, "audio").listFiles()!!.size)
    }

    @Test fun replacementResetsTrimButKeepsIdentityNameVolumeAndCleansOldFile() = fixture { store, _, source ->
        val id = store.add("Keep name").single().padId
        val old = store.import(id, Uri.fromFile(source)).single()
        store.update(id, "Keep name", 100L, 1500L, 0.4f)
        val next = store.import(id, Uri.fromFile(source)).single()
        assertEquals(id, next.padId)
        assertEquals("Keep name", next.name)
        assertEquals(0.4f, next.volume, 0f)
        assertEquals(0L, next.inMs)
        assertEquals(SoundPadsPrototypeFiles.audioDurationMs(source), next.outMs)
        assertFalse(File(old.audioPath).exists())
        assertTrue(File(next.audioPath).isFile)
        try { store.update(id, "Bad", 0L, requireNotNull(next.outMs) + 1, 0.4f); fail("Invalid trim") }
        catch (_: IllegalArgumentException) { }
        assertEquals(next, store.load().single())
    }

    @Test fun sharedReferencesAndColorRestoreAndCorruptBankNeverDeletesAudio() = fixture { store, root, source ->
        val first = store.add("First").single().padId
        val pad = store.import(first, Uri.fromFile(source)).single()
        store.add("Second")
        val bankFile = File(root, "bank.json")
        val json = JSONObject(bankFile.readText())
        val entries = json.getJSONArray("pads")
        entries.getJSONObject(0).put("colorArgb", 0xFF336699L)
        entries.getJSONObject(1).put("audioFile", File(pad.audioPath).name)
        bankFile.writeText(json.toString())
        val restored = SoundPadsStore(context, root).load()
        assertEquals(0xFF336699L, restored.first().colorArgb)
        store.update(first, "Still colored", 0L, pad.outMs, 0.5f)
        assertEquals(0xFF336699L, store.load().first().colorArgb)
        store.delete(first)
        assertTrue(File(pad.audioPath).exists())
        val bankBytes = bankFile.readBytes()
        bankFile.writeText("broken")
        try { store.load(); fail("Corrupt bank must fail closed") } catch (_: org.json.JSONException) { }
        assertTrue(File(pad.audioPath).exists())
        bankFile.writeBytes(bankBytes)
        store.delete(restored[1].padId)
        assertFalse(File(pad.audioPath).exists())
        assertTrue(SoundPadsStore(context, root).load().isEmpty())
    }
}
