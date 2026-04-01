package com.patrick.lrcreader.smp

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object SmpArchiveFinalizeScheduler {

    internal const val INPUT_SONG_ID = "song_id"
    internal const val INPUT_REQUEST_ID = "request_id"

    private const val UNIQUE_QUEUE_NAME = "smp_archive_finalize_queue"

    fun schedule(context: Context, record: SmpArchiveFinalizeRecord) {
        val songTag = buildSongTag(record.songId)
        val request = OneTimeWorkRequestBuilder<SmpArchiveFinalizeWorker>()
            .setInputData(
                workDataOf(
                    INPUT_SONG_ID to record.songId,
                    INPUT_REQUEST_ID to record.requestId
                )
            )
            .addTag(songTag)
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelAllWorkByTag(songTag)
        workManager.beginUniqueWork(
            UNIQUE_QUEUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        ).enqueue()
    }

    fun reconcilePending(context: Context) {
        SmpArchiveFinalizeStore.listPending(context)
            .forEach { record -> schedule(context, record) }
    }

    private fun buildSongTag(songId: String): String = "smp_archive_finalize::$songId"
}
