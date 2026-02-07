package com.patrick.lrcreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.patrick.lrcreader.exo.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // ✅ Compare au vrai applicationId du variant en cours (labo, concert, etc.)
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }
}