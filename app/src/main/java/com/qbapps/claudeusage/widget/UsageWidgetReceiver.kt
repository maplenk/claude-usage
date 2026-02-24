package com.qbapps.claudeusage.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.qbapps.claudeusage.worker.UsageSyncWorker

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        triggerSync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            triggerSync(context)
        }
    }

    private fun triggerSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(UsageSyncWorker.TAG_ONE_TIME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UsageSyncWorker.WORK_NAME_CHAIN,
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}
