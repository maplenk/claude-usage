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
import com.qbapps.claudeusage.MainActivity
import com.qbapps.claudeusage.worker.UsageSyncWorker

internal val ProviderParameterKey = ActionParameters.Key<String>("provider")

/**
 * [ActionCallback] that opens the main application when the widget is tapped.
 */
class OpenAppActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        launchApp(context = context, openSettings = false, provider = null)
    }
}

/** Opens the Accounts section so a disconnected provider can be connected. */
class OpenSettingsActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        launchApp(context = context, openSettings = true, provider = null)
    }
}

/** Opens the dashboard and focuses the provider represented by the tapped row or ring. */
class OpenProviderActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        launchApp(
            context = context,
            openSettings = false,
            provider = parameters[ProviderParameterKey],
        )
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

private fun launchApp(
    context: Context,
    openSettings: Boolean,
    provider: String?,
) {
    val packageName = context.packageName
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        ?: Intent().apply {
            setClassName(packageName, "$packageName.MainActivity")
        }
    if (openSettings) {
        launchIntent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
    }
    provider?.let {
        launchIntent.putExtra(MainActivity.EXTRA_FOCUS_PROVIDER, it)
    }
    launchIntent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
    )
    context.startActivity(launchIntent)
}
