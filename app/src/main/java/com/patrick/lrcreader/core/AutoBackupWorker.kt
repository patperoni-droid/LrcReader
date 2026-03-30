package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val autoResult = BackupManager.autoSaveToDefaultBackupFile(applicationContext)
            val output = workDataOf(
                "auto_backup_code" to autoResult.code.name,
                "auto_backup_workspace_status" to (autoResult.workspaceStatus?.name ?: "null"),
                "auto_backup_target_file" to (autoResult.targetFileUri?.toString() ?: ""),
                "auto_backup_detail" to (autoResult.detail ?: "")
            )

            when (BackupManager.workerActionForAutoBackup(autoResult)) {
                BackupManager.AutoBackupWorkerAction.SUCCESS -> Result.success(output)
                BackupManager.AutoBackupWorkerAction.FAILURE -> Result.failure(output)
                BackupManager.AutoBackupWorkerAction.RETRY -> Result.retry()
            }
        } catch (error: Exception) {
            Log.e("AUTO_BACKUP", "worker exception", error)
            Result.retry()
        }
    }
}
