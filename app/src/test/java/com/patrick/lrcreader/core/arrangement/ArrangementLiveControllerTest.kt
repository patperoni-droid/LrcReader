package com.patrick.lrcreader.core.arrangement

import com.patrick.lrcreader.smp.ArrangementSegmentData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementLiveControllerTest {

    @Test
    fun `define next delegates to sampler and exposes the armed occurrence`() {
        val controller = testController()
        val queued = mutableListOf<Int>()

        val result = controller.defineNext(
            nextIndex = 1,
            audioPath = "/audio/source.mp3",
            segments = segments,
            useSampler = true,
            queueSamplerOccurrence = queued::add,
            mediaQueue = FakeMediaQueue()
        )

        assertTrue(result)
        assertEquals(listOf(1), queued)
        assertEquals(1, controller.state.value.armedOccurrenceIndex)
    }

    @Test
    fun `define next appends when the player has no prepared next item`() {
        val controller = testController()
        val queue = FakeMediaQueue(
            currentMediaItemIndex = 0,
            mediaItemCount = 1
        )

        val result = controller.defineNext(
            nextIndex = 2,
            audioPath = "/audio/source.mp3",
            segments = segments,
            useSampler = false,
            queueSamplerOccurrence = {},
            mediaQueue = queue
        )

        assertTrue(result)
        assertEquals(listOf(segments[2]), queue.addedSegments)
        assertTrue(queue.replacedSegments.isEmpty())
        assertEquals(2, controller.armedOccurrenceIndex)
    }

    @Test
    fun `define next replaces the prepared next item and updates the armed occurrence`() {
        val controller = testController()
        val queue = FakeMediaQueue(
            currentMediaItemIndex = 0,
            mediaItemCount = 2
        )

        assertTrue(
            controller.defineNext(
                nextIndex = 1,
                audioPath = "/audio/source.mp3",
                segments = segments,
                useSampler = false,
                queueSamplerOccurrence = {},
                mediaQueue = queue
            )
        )
        assertTrue(
            controller.defineNext(
                nextIndex = 2,
                audioPath = "/audio/source.mp3",
                segments = segments,
                useSampler = false,
                queueSamplerOccurrence = {},
                mediaQueue = queue
            )
        )

        assertEquals(listOf(1, 1), queue.replacedSegments.map { it.first })
        assertEquals(listOf(segments[1], segments[2]), queue.replacedSegments.map { it.second })
        assertEquals(2, controller.armedOccurrenceIndex)
    }

    @Test
    fun `consume returns the armed occurrence once and clears the shared state`() {
        val controller = testController()

        controller.defineNext(
            nextIndex = 1,
            audioPath = "/audio/source.mp3",
            segments = segments,
            useSampler = true,
            queueSamplerOccurrence = {},
            mediaQueue = FakeMediaQueue()
        )

        assertEquals(1, controller.consumeArmedOccurrence())
        assertNull(controller.consumeArmedOccurrence())
        assertNull(controller.state.value.armedOccurrenceIndex)
    }

    @Test
    fun `controller owns preview and active occurrence state`() {
        val controller = testController()

        controller.startPreview(1)
        assertTrue(controller.state.value.previewActive)
        assertEquals(1, controller.state.value.activeOccurrenceIndex)

        controller.updateActiveOccurrence(2)
        assertEquals(2, controller.state.value.activeOccurrenceIndex)

        controller.stopPreview()
        assertFalse(controller.state.value.previewActive)
        assertEquals(-1, controller.state.value.activeOccurrenceIndex)
        assertNull(controller.state.value.armedOccurrenceIndex)
    }

    @Test
    fun `invalid occurrence is rejected without changing the queue or state`() {
        val controller = testController()
        val queue = FakeMediaQueue()

        val result = controller.defineNext(
            nextIndex = 8,
            audioPath = "/audio/source.mp3",
            segments = segments,
            useSampler = false,
            queueSamplerOccurrence = {},
            mediaQueue = queue
        )

        assertFalse(result)
        assertTrue(queue.addedSegments.isEmpty())
        assertTrue(queue.replacedSegments.isEmpty())
        assertNull(controller.armedOccurrenceIndex)
    }

    @Test
    fun `prepared occurrence is queued and becomes the only armed occurrence`() {
        val controller = testController()
        val queued = mutableListOf<Int>()

        assertTrue(
            controller.defineNextPreparedOccurrence(
                nextIndex = 1,
                occurrenceCount = 3,
                queuePreparedOccurrence = {
                    queued += it
                    true
                }
            )
        )
        assertTrue(
            controller.defineNextPreparedOccurrence(
                nextIndex = 2,
                occurrenceCount = 3,
                queuePreparedOccurrence = {
                    queued += it
                    true
                }
            )
        )

        assertEquals(listOf(1, 2), queued)
        assertEquals(2, controller.armedOccurrenceIndex)
    }

    @Test
    fun `prepared occurrence is not armed when its queue rejects it`() {
        val controller = testController()

        assertFalse(
            controller.defineNextPreparedOccurrence(
                nextIndex = 1,
                occurrenceCount = 3,
                queuePreparedOccurrence = { false }
            )
        )

        assertNull(controller.armedOccurrenceIndex)
    }

    private fun testController() = ArrangementLiveController(
        debugLog = { _, _ -> },
        warningLog = { _, _, _ -> },
        elapsedRealtimeMs = { 123L }
    )

    private class FakeMediaQueue(
        override val currentMediaItemIndex: Int = 0,
        mediaItemCount: Int = 1,
        override val currentPositionMs: Long = 0L
    ) : ArrangementLiveMediaQueue {
        private var itemCount = mediaItemCount

        val addedSegments = mutableListOf<ArrangementSegmentData>()
        val replacedSegments = mutableListOf<Pair<Int, ArrangementSegmentData>>()

        override val mediaItemCount: Int
            get() = itemCount

        override fun addMediaItem(
            audioPath: String,
            segment: ArrangementSegmentData
        ): Boolean {
            addedSegments += segment
            itemCount += 1
            return true
        }

        override fun replaceMediaItem(
            index: Int,
            audioPath: String,
            segment: ArrangementSegmentData
        ): Boolean {
            replacedSegments += index to segment
            return true
        }
    }

    private companion object {
        val segments = listOf(
            ArrangementSegmentData("a", "A", 0L, 1_000L),
            ArrangementSegmentData("b", "B", 1_000L, 2_000L),
            ArrangementSegmentData("c", "C", 2_000L, 3_000L)
        )
    }
}
