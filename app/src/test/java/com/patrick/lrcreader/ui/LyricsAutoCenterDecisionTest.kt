package com.patrick.lrcreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAutoCenterDecisionTest {

    @Test
    fun repeatedActiveIndex_requestsOnlyOneCentering() {
        assertTrue(decide(targetIndex = 4, lastRequestedIndex = 3))
        assertFalse(decide(targetIndex = 4, lastRequestedIndex = 4))
    }

    @Test
    fun realIndexChange_requestsNewCentering() {
        assertTrue(decide(targetIndex = 5, lastRequestedIndex = 4))
    }

    @Test
    fun recreatedListWithSameIndex_doesNotStartLoop() {
        assertFalse(decide(targetIndex = 8, lastRequestedIndex = 8, listReady = true))
    }

    @Test
    fun playbackStart_doesNotDuplicateEquivalentRequestInProgress() {
        assertFalse(
            decide(
                targetIndex = 2,
                lastRequestedIndex = -1,
                equivalentRequestInProgress = true,
                force = true
            )
        )
    }

    @Test
    fun manualScroll_blocksCenteringThenAllowsSingleExplicitResume() {
        assertFalse(decide(targetIndex = 3, lastRequestedIndex = 2, userScrolling = true))
        assertTrue(decide(targetIndex = 3, lastRequestedIndex = 3, force = true))
    }

    private fun decide(
        targetIndex: Int,
        lastRequestedIndex: Int,
        listReady: Boolean = true,
        userScrolling: Boolean = false,
        equivalentRequestInProgress: Boolean = false,
        force: Boolean = false
    ): Boolean = shouldRequestLyricsAutoCenter(
        targetIndex = targetIndex,
        lastRequestedIndex = lastRequestedIndex,
        listReady = listReady,
        isPlaying = true,
        userScrolling = userScrolling,
        isDragging = false,
        equivalentRequestInProgress = equivalentRequestInProgress,
        force = force
    )
}
