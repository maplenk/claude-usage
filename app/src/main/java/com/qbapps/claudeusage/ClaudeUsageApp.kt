package com.qbapps.claudeusage

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.qbapps.claudeusage.data.local.SecureCredentialStore
import com.qbapps.claudeusage.notification.UsageNotificationHelper
import com.qbapps.claudeusage.worker.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ClaudeUsageApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: UsageNotificationHelper
    @Inject lateinit var credentialStore: SecureCredentialStore
    @Inject lateinit var workManagerScheduler: WorkManagerScheduler

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        ensureSyncRunning()
    }

    private fun ensureSyncRunning() {
        val hasKey = credentialStore.getSessionKey() != null
        val hasOrg = credentialStore.getOrgId() != null
        if (hasKey && hasOrg) {
            workManagerScheduler.scheduleSync(5)
            workManagerScheduler.schedulePeriodicFallback()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
