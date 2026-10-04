package com.patrick.lrcreader.ui.soundpads

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.core.soundpads.SoundPadsStore
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeFiles
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SoundPadsPrototypeDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun testOverlayCanTriggerAndStopWithoutOpeningAFullScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SoundPadsStore(context)
        val name = "UI-${UUID.randomUUID()}"
        val id = runBlocking {
            val pad = store.add(name).last()
            val source = SoundPadsPrototypeFiles.defaults(context).first()
            store.import(pad.padId, Uri.fromFile(File(source.audioPath)))
            pad.padId
        }
        compose.setContent {
            // The test host Activity lives in the test APK; assets belong to the target app.
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner
            ) {
                MaterialTheme { SoundPadsPrototypeDialog(onClose = {}) }
            }
        }
        val pad = name
        compose.waitUntil(10_000) { compose.onAllNodesWithText(pad).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(pad).performClick()
        compose.onNodeWithText(context.getString(R.string.soundpads_prototype_stop)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.soundpads_prototype_idle)).fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { store.delete(id) }
    }
}
