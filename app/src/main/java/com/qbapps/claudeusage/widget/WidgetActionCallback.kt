package com.qbapps.claudeusage.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import com.qbapps.claudeusage.worker.UsageSyncWorker

/**
 * [ActionCallback] that opens the main application when the widget is tapped.
 */
class OpenAppActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply {
                setClassName(packageName, "$packageName.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}

/**
 * [ActionCallback] that enqueues a one-shot [UsageSyncWorker] to refresh
 * usage data immediately. The widget's refresh button triggers this.
 */
class RefreshActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(UsageSyncWorker.TAG_ONE_TIME)
            .build()

        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "usage_sync_manual_refresh",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
