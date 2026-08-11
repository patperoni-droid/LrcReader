package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletTextPrompterRoutingTest {

    @Test
    fun `modern tablet routes text prompter through split layout`() {
        assertTrue(
            shouldUseTabletSplitTextPrompter(
                tabletMode = true,
                tabletExperimentalModeEnabled = true,
                selectedTabSupportsSplit = true,
                textPrompterId = "prompter-id"
            )
        )
    }

    @Test
    fun `phone keeps legacy text prompter layout`() {
        assertFalse(
            shouldUseTabletSplitTextPrompter(
                tabletMode = false,
                tabletExperimentalModeEnabled = true,
                selectedTabSupportsSplit = true,
                textPrompterId = "prompter-id"
            )
        )
    }

    @Test
    fun `tablet without modern mode keeps legacy layout`() {
        assertFalse(
            shouldUseTabletSplitTextPrompter(
                tabletMode = true,
                tabletExperimentalModeEnabled = false,
                selectedTabSupportsSplit = true,
                textPrompterId = "prompter-id"
            )
        )
    }

    @Test
    fun `tablet tab outside live split keeps legacy overlay`() {
        assertFalse(
            shouldUseTabletSplitTextPrompter(
                tabletMode = true,
                tabletExperimentalModeEnabled = true,
                selectedTabSupportsSplit = false,
                textPrompterId = "prompter-id"
            )
        )
    }
}
