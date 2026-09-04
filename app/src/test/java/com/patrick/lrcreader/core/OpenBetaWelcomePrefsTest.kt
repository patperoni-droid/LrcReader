package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class OpenBetaWelcomePrefsTest {
    @Test fun firstOpeningShowsWelcomeAndNextOpeningDoesNot() {
        val state = mutableMapOf<String, Boolean>()
        val context = contextWithPreferences(state)
        assertTrue(OpenBetaWelcomePrefs.shouldShow(context))

        OpenBetaWelcomePrefs.markSeen(context)

        assertFalse(OpenBetaWelcomePrefs.shouldShow(context))
        assertTrue(state.values.single())
    }

    @Test fun closingPersistsTheVersionedIntroductionState() {
        val state = mutableMapOf<String, Boolean>()
        OpenBetaWelcomePrefs.markSeen(contextWithPreferences(state))
        val reopenedContext = contextWithPreferences(state)

        assertFalse(OpenBetaWelcomePrefs.shouldShow(reopenedContext))
    }

    @Test fun feedbackActionRequestsTheExpectedBrowserUrl() {
        val request = OpenBetaWelcomePrefs.feedbackRequest()

        assertEquals(Intent.ACTION_VIEW, request.action)
        assertEquals("https://www.musimio.com/feedback", request.url)
    }

    private fun contextWithPreferences(state: MutableMap<String, Boolean>): Context {
        val context = Mockito.mock(Context::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(preferences)
        Mockito.`when`(preferences.getBoolean(Mockito.anyString(), Mockito.anyBoolean()))
            .thenAnswer { invocation ->
                state[invocation.getArgument(0)] ?: invocation.getArgument(1)
            }
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putBoolean(Mockito.anyString(), Mockito.anyBoolean()))
            .thenAnswer { invocation ->
                state[invocation.getArgument(0)] = invocation.getArgument(1)
                editor
            }
        return context
    }
}
