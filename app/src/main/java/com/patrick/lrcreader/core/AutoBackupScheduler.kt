package com.patrick.lrcreader.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    private const val UNIQUE_NAME = "auto_backup_periodic"

    /**
     * Planifie une sauvegarde auto régulière.
     * (12h = bon compromis : pas lourd, mais ça te sauve la mise.)
     */
    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}