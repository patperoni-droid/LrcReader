package com.patrick.lrcreader.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            BackupManager.autoSaveToDefaultBackupFile(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}