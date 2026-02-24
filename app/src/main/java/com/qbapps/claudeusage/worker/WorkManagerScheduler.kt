package com.qbapps.claudeusage.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized scheduler for background usage-sync work.
 *
 * Two strategies run in parallel:
 *
 * 1. **One-shot chain** (`scheduleSync`) – Enqueues a [UsageSyncWorker] that,
 *    after completing, re-enqueues itself with the configured delay. This
 *    supports sub-15-minute intervals.
 *
 * 2. **Periodic fallback** (`schedulePeriodicFallback`) – A 15-minute
 *    [PeriodicWorkRequest] that acts as a safety net in case the one-shot
 *    chain breaks (e.g., process death, force stop).
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedules (or reschedules) the one-shot sync chain.
     *
     * Cancels any existing chain and starts a fresh one with the given
     * [intervalSeconds] as the initial delay.
     */
    fun scheduleSync(intervalSeconds: Int) {
        val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setInitialDelay(intervalSeconds.toLong(), TimeUnit.SECONDS)
            .setConstraints(networkConstraint)
            .addTag(UsageSyncWorker.TAG_ONE_TIME)
            .build()

        workManager.enqueueUniqueWork(
            UsageSyncWorker.WORK_NAME_CHAIN,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Enqueues a periodic work request every 15 minutes as a fallback.
     *
     * This guarantees that even if the one-shot chain dies, the widget
     * data will still be refreshed at a reasonable cadence.
     */
    fun schedulePeriodicFallback() {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .addTag(UsageSyncWorker.TAG_PERIODIC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "usage_sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Cancels all scheduled sync work (both one-shot chain and periodic fallback).
     */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(UsageSyncWorker.TAG_ONE_TIME)
        workManager.cancelAllWorkByTag(UsageSyncWorker.TAG_PERIODIC)
        workManager.cancelUniqueWork(UsageSyncWorker.WORK_NAME_CHAIN)
        workManager.cancelUniqueWork(UsageSyncWorker.WORK_NAME_NEXT)
        workManager.cancelUniqueWork("usage_sync_periodic")
    }
}
