package com.patrick.lrcreader.core.arrangement

import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.smp.ArrangementSegmentData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ArrangementLiveState(
    val armedOccurrenceIndex: Int? = null,
    val previewActive: Boolean = false,
    val activeOccurrenceIndex: Int = -1
)

interface ArrangementLiveMediaQueue {
    val currentMediaItemIndex: Int
    val mediaItemCount: Int
    val currentPositionMs: Long

    fun addMediaItem(audioPath: String, segment: ArrangementSegmentData): Boolean

    fun replaceMediaItem(
        index: Int,
        audioPath: String,
        segment: ArrangementSegmentData
    ): Boolean
}

class ArrangementLiveController(
    private val debugLog: (String, String) -> Unit = { tag, message -> Log.d(tag, message) },
    private val warningLog: (String, String, Throwable) -> Unit =
        { tag, message, error -> Log.w(tag, message, error) },
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) {
    private val mutableState = MutableStateFlow(ArrangementLiveState())

    val state: StateFlow<ArrangementLiveState> = mutableState.asStateFlow()

    val armedOccurrenceIndex: Int?
        get() = mutableState.value.armedOccurrenceIndex

    val previewActive: Boolean
        get() = mutableState.value.previewActive

    val activeOccurrenceIndex: Int
        get() = mutableState.value.activeOccurrenceIndex

    fun clearArmedOccurrence() {
        if (mutableState.value.armedOccurrenceIndex != null) {
            mutableState.value = mutableState.value.copy(armedOccurrenceIndex = null)
        }
    }

    fun startPreview(occurrenceIndex: Int) {
        mutableState.value = mutableState.value.copy(
            previewActive = true,
            activeOccurrenceIndex = occurrenceIndex
        )
    }

    fun stopPreview() {
        mutableState.value = ArrangementLiveState()
    }

    fun updateActiveOccurrence(occurrenceIndex: Int) {
        mutableState.value = mutableState.value.copy(activeOccurrenceIndex = occurrenceIndex)
    }

    fun consumeArmedOccurrence(): Int? {
        val armedIndex = mutableState.value.armedOccurrenceIndex
        clearArmedOccurrence()
        return armedIndex
    }

    fun defineNext(
        nextIndex: Int,
        audioPath: String,
        segments: List<ArrangementSegmentData>,
        useSampler: Boolean,
        queueSamplerOccurrence: (Int) -> Unit,
        mediaQueue: ArrangementLiveMediaQueue
    ): Boolean {
        if (nextIndex !in segments.indices) return false

        if (useSampler) {
            return runCatching {
                queueSamplerOccurrence(nextIndex)
                armOccurrence(nextIndex)
                debugLog(ARR_STRUCTURE_SAMPLER_TAG, "QUEUE_NEXT index=$nextIndex")
            }.onFailure { error ->
                warningLog(
                    ARR_STRUCTURE_SAMPLER_TAG,
                    "FALLBACK_EXOPLAYER reason=sampler_queue_failed error=${error.message}",
                    error
                )
            }.isSuccess
        }

        val queuedSegment = segments[nextIndex]
        val queuedStartMs = minOf(queuedSegment.startMs, queuedSegment.endMs).coerceAtLeast(0L)
        val queuedEndMs = maxOf(queuedSegment.startMs, queuedSegment.endMs)
            .coerceAtLeast(queuedStartMs + 1L)
        val sourceUri = "file://${File(audioPath).absolutePath}"
        val insertionIndex = mediaQueue.currentMediaItemIndex.coerceAtLeast(0) + 1
        val mediaItemCount = mediaQueue.mediaItemCount
        val previousArmedIndex = armedOccurrenceIndex
        val queued = if (mediaItemCount <= insertionIndex) {
            mediaQueue.addMediaItem(audioPath, queuedSegment).also { added ->
                if (added) {
                    debugLog(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "NEXT_ITEM_SET queuedIndex=$nextIndex insertionIndex=$insertionIndex name=${queuedSegment.name}"
                    )
                    debugLog(
                        ARR_STRUCTURE_FLOW_TAG,
                        "NEXT_ITEM_SET queuedIndex=$nextIndex name=${queuedSegment.name} " +
                            "startMs=$queuedStartMs endMs=$queuedEndMs sourceUri=$sourceUri " +
                            "previousQueuedIndex=$previousArmedIndex mediaItemCountBefore=$mediaItemCount " +
                            "mediaItemCountAfter=${mediaQueue.mediaItemCount}"
                    )
                }
            }
        } else {
            mediaQueue.replaceMediaItem(insertionIndex, audioPath, queuedSegment).also { replaced ->
                if (replaced) {
                    debugLog(
                        ARR_STRUCTURE_QUEUE_TAG,
                        "NEXT_ITEM_REPLACED old=$previousArmedIndex new=$nextIndex " +
                            "insertionIndex=$insertionIndex name=${queuedSegment.name}"
                    )
                    debugLog(
                        ARR_STRUCTURE_FLOW_TAG,
                        "NEXT_ITEM_REPLACED queuedIndex=$nextIndex name=${queuedSegment.name} " +
                            "startMs=$queuedStartMs endMs=$queuedEndMs sourceUri=$sourceUri " +
                            "previousQueuedIndex=$previousArmedIndex mediaItemCountBefore=$mediaItemCount " +
                            "mediaItemCountAfter=${mediaQueue.mediaItemCount}"
                    )
                }
            }
        }
        if (!queued) return false

        armOccurrence(nextIndex)
        debugLog(
            ARR_TIMING_DIAG_TAG,
            "STRUCTURE_QUEUE segmentId=${queuedSegment.id} segmentStartMs=$queuedStartMs " +
                "segmentEndMs=$queuedEndMs mediaItemSourceUri=$sourceUri " +
                "transitionTimestampMs=${elapsedRealtimeMs()} expectedStartMs=$queuedStartMs " +
                "currentPosition=${mediaQueue.currentPositionMs}"
        )
        return true
    }

    private fun armOccurrence(index: Int) {
        mutableState.value = mutableState.value.copy(armedOccurrenceIndex = index)
    }

    private companion object {
        const val ARR_STRUCTURE_QUEUE_TAG = "ARR_STRUCTURE_QUEUE"
        const val ARR_STRUCTURE_FLOW_TAG = "ARR_STRUCTURE_FLOW"
        const val ARR_STRUCTURE_SAMPLER_TAG = "ARR_STRUCTURE_SAMPLER"
        const val ARR_TIMING_DIAG_TAG = "ARR_TIMING_DIAG"
    }
}
