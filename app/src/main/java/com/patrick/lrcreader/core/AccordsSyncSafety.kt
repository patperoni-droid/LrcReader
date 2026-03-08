package com.patrick.lrcreader.core

import android.content.Context
import com.patrick.lrcreader.exo.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccordsIoResult(
    val success: Boolean,
    val stage: String
)

data class AccordsUiTruth(
    val lines: List<LrcLine>,
    val hasSource: Boolean
)

/**
 * Returns the locked editing track only when it still matches the current track.
 * Any mismatch means the edit action must be blocked.
 */
fun resolveAccordsEditTargetTrack(
    lockedTrackUri: String?,
    currentTrackUri: String?
): String? {
    val locked = lockedTrackUri?.trim().orEmpty()
    val current = currentTrackUri?.trim().orEmpty()
    if (locked.isBlank() || current.isBlank()) return null
    return if (locked == current) locked else null
}

fun runAccordsSaveIo(
    writeAccords: () -> String?,
    ensureLyricsTwin: (writtenName: String) -> Boolean
): AccordsIoResult {
    val writtenName = writeAccords()
    if (writtenName.isNullOrBlank()) {
        return AccordsIoResult(success = false, stage = "writeAccords")
    }
    val ensured = ensureLyricsTwin(writtenName)
    if (!ensured) {
        return AccordsIoResult(success = false, stage = "ensureLyricsTwin")
    }
    return AccordsIoResult(success = true, stage = "ok")
}

fun runAccordsDeleteIo(
    deleteAccords: () -> Boolean
): AccordsIoResult {
    return if (deleteAccords()) {
        AccordsIoResult(success = true, stage = "ok")
    } else {
        AccordsIoResult(success = false, stage = "deleteAccords")
    }
}

fun resolveAccordsUiTruthAfterSave(
    previous: AccordsUiTruth,
    requestedLines: List<LrcLine>,
    io: AccordsIoResult
): AccordsUiTruth {
    if (!io.success) return previous
    return AccordsUiTruth(
        lines = requestedLines,
        hasSource = true
    )
}

fun resolveAccordsUiTruthAfterDelete(
    previous: AccordsUiTruth,
    io: AccordsIoResult
): AccordsUiTruth {
    if (!io.success) return previous
    return AccordsUiTruth(
        lines = emptyList(),
        hasSource = false
    )
}

fun buildAccordsIoFailureFeedback(
    context: Context,
    actionLabel: String,
    io: AccordsIoResult
): String? {
    if (io.success) return null
    return context.getString(R.string.accords_error_action_with_stage, actionLabel, io.stage)
}

fun buildAccordsIoFailureFeedback(action: String, io: AccordsIoResult): String? {
    if (io.success) return null
    return "Accords: echec $action (${io.stage})."
}

fun buildAccordsIoFailureLog(action: String, trackUri: String, io: AccordsIoResult): String {
    return "ACCORDS_IO_FAILURE action=$action trackUri=$trackUri stage=${io.stage}"
}

/**
 * Single writer queue for accords persistence.
 * Conflation keeps only the most recent pending request while a write is in-flight.
 */
class LatestAccordsWriteQueue<T>(
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writer: suspend (T) -> Unit
) {
    private val channel = Channel<T>(capacity = Channel.CONFLATED)

    init {
        scope.launch {
            for (request in channel) {
                withContext(dispatcher) {
                    writer(request)
                }
            }
        }
    }

    fun submit(request: T): Boolean {
        return channel.trySend(request).isSuccess
    }

    fun close() {
        channel.close()
    }
}
