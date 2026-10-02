package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.service.OverlayStateManager
import com.example.util.AppManagerHelper
import java.util.concurrent.TimeUnit

class AutomatedCacheCleanerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val state = OverlayStateManager.uiState.value
        if (!state.backgroundDataCaching) {
            // In battery savings mode, perform lightweight self cleanup
            AppManagerHelper.clearSelfCache(applicationContext)
            return Result.success()
        }

        // Low-power background cache maintenance
        AppManagerHelper.clearSelfCache(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "AutomatedCacheCleaningWork"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutomatedCacheCleanerWorker>(
                1, TimeUnit.DAYS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
