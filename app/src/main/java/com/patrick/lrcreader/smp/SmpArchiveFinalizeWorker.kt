package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SmpArchiveFinalizeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SMP_ARCHIVE_FINALIZE"
    }

    override suspend fun doWork(): Result {
        val songId = inputData.getString(SmpArchiveFinalizeScheduler.INPUT_SONG_ID)?.trim().orEmpty()
        val requestId = inputData.getString(SmpArchiveFinalizeScheduler.INPUT_REQUEST_ID)?.trim().orEmpty()
        if (songId.isEmpty() || requestId.isEmpty()) {
            Log.e(TAG, "step=worker_invalid_input songId=$songId requestId=$requestId")
            return Result.failure()
        }

        val record = SmpArchiveFinalizeStore.get(applicationContext, songId)
        if (record == null) {
            Log.i(TAG, "step=worker_skip_no_record songId=$songId requestId=$requestId")
            return Result.success()
        }
        if (record.requestId != requestId) {
            Log.i(
                TAG,
                "step=worker_skip_stale_request songId=$songId requestId=$requestId currentRequestId=${record.requestId}"
            )
            return Result.success()
        }
        if (record.state != SmpArchivePersistState.PENDING) {
            Log.i(
                TAG,
                "step=worker_skip_not_pending songId=$songId requestId=$requestId state=${record.state}"
            )
            return Result.success()
        }

        val snapshot = record.toSnapshot()
        if (snapshot == null || !snapshot.isUsable || snapshot.workspaceRootUri == null) {
            SmpArchiveFinalizeStore.markFailed(
                context = applicationContext,
                songId = songId,
                requestId = requestId,
                reason = "snapshot_workspace_unavailable"
            )
            Log.e(TAG, "step=worker_failed_bad_snapshot songId=$songId requestId=$requestId")
            return Result.success()
        }

        val song = SmpLibraryScanner(applicationContext).findSongById(songId)
        if (song == null) {
            SmpArchiveFinalizeStore.markFailed(
                context = applicationContext,
                songId = songId,
                requestId = requestId,
                reason = "runtime_song_missing"
            )
            Log.e(TAG, "step=worker_failed_runtime_missing songId=$songId requestId=$requestId")
            return Result.success()
        }

        val persistResult = SmpWorkspaceArchiveStore.persistNormalizedArchive(
            context = applicationContext,
            songUnit = song,
            snapshotOverride = snapshot
        )
        val archiveUri = persistResult.archiveUri?.toString()
        if (archiveUri != null) {
            SmpArchiveFinalizeStore.markSuccess(
                context = applicationContext,
                songId = songId,
                requestId = requestId,
                archiveUri = archiveUri
            )
            Log.i(
                TAG,
                "step=worker_success songId=$songId requestId=$requestId archiveUri=$archiveUri"
            )
            return Result.success()
        }

        val failureReason = persistResult.failureReason ?: "archive_persist_failed"
        SmpArchiveFinalizeStore.markFailed(
            context = applicationContext,
            songId = songId,
            requestId = requestId,
            reason = failureReason
        )
        Log.e(
            TAG,
            "step=worker_failed songId=$songId requestId=$requestId reason=$failureReason"
        )
        return Result.success()
    }
}
