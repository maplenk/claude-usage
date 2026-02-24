package com.qbapps.claudeusage.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qbapps.claudeusage.data.local.UsageDataStore
import com.qbapps.claudeusage.data.local.UserPreferencesStore
import com.qbapps.claudeusage.domain.repository.UsageRepository
import com.qbapps.claudeusage.notification.UsageNotificationHelper
import com.qbapps.claudeusage.widget.pushDataToWidgets
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker that fetches the latest usage data from the Claude API,
 * persists it via [UsageRepository], pushes it to the widget Glance state,
 * and then enqueues the next sync.
 *
 * After a successful sync the worker enqueues the **next** one-shot work
 * request with the user's configured refresh interval. This creates a
 * self-perpetuating chain that supports sub-15-minute intervals (unlike
 * [PeriodicWorkRequest] which has a 15-minute minimum).
 */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val usageRepository: UsageRepository,
    private val userPreferencesStore: UserPreferencesStore,
    private val usageDataStore: UsageDataStore,
    private val notificationHelper: UsageNotificationHelper,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        SyncLog.d(appContext, "doWork() started  attempt=$runAttemptCount  stopped=$isStopped")

        // Snapshot the current cached utilization before the fetch overwrites it
        val previousFiveHour = usageDataStore.cachedUsage.first()?.fiveHour?.utilization

        val result = usageRepository.fetchUsage()

        if (result.isSuccess) {
            val newUsage = result.getOrNull()
            val pct = newUsage?.fiveHour?.utilization
            SyncLog.d(appContext, "fetch OK  session=${pct?.let { "%.1f%%".format(it) } ?: "null"}")

            // Push the freshly fetched data into every widget instance's
            // Glance state and trigger a UI refresh.
            newUsage?.let { pushDataToWidgets(appContext, it) }

            // Notify if session usage reset: was >0, now 0
            val newFiveHour = newUsage?.fiveHour?.utilization
            if (previousFiveHour != null && previousFiveHour > 0.0
                && newFiveHour != null && newFiveHour == 0.0
            ) {
                val shouldNotify = userPreferencesStore.notifyOnSessionReset.first()
                if (shouldNotify) {
                    SyncLog.d(appContext, "session reset detected, sending notification")
                    notificationHelper.notifySessionReset()
                }
            }
        } else {
            val error = result.exceptionOrNull()
            SyncLog.d(appContext, "fetch FAILED  error=${error?.message ?: "unknown"}")
        }

        // Always schedule the next sync so the chain never breaks.
        // We return success() in all cases — rescheduling is handled by us,
        // not by WorkManager's retry/backoff mechanism.
        scheduleNextSync()
        SyncLog.d(appContext, "doWork() finished  stopped=$isStopped")
        return Result.success()
    }

    /**
     * Enqueues the next one-shot sync using the user's preferred refresh
     * interval. This keeps the sync chain alive even for intervals shorter
     * than WorkManager's 15-minute periodic minimum.
     */
    private suspend fun scheduleNextSync() {
        val intervalSeconds = userPreferencesStore.refreshIntervalSeconds.first()
        SyncLog.d(appContext, "next sync in ${intervalSeconds}s")

        val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setInitialDelay(intervalSeconds.toLong(), TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_ONE_TIME)
            .build()

        // Use WORK_NAME_NEXT (not WORK_NAME_CHAIN) so we don't REPLACE
        // the currently-running worker, which would cancel it mid-execution.
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(
                WORK_NAME_NEXT,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    companion object {
        const val TAG_ONE_TIME = "usage_sync"
        const val TAG_PERIODIC = "usage_sync_periodic"
        const val WORK_NAME_CHAIN = "usage_sync_chain"
        const val WORK_NAME_NEXT = "usage_sync_next"
    }
}
