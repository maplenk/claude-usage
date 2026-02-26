package com.qbapps.claudeusage.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.qbapps.claudeusage.MainActivity
import com.qbapps.claudeusage.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID_SESSION_RESET = "session_reset"
        private const val CHANNEL_ID_USAGE_MILESTONE = "usage_milestone"
        private const val NOTIFICATION_ID_SESSION_RESET = 1001
        private const val NOTIFICATION_ID_USAGE_MILESTONE_BASE = 2000
    }

    fun createChannel() {
        val sessionResetChannel = NotificationChannel(
            CHANNEL_ID_SESSION_RESET,
            context.getString(R.string.notification_channel_session_reset),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_session_reset_description)
        }

        val usageMilestoneChannel = NotificationChannel(
            CHANNEL_ID_USAGE_MILESTONE,
            context.getString(R.string.notification_channel_usage_milestones),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_usage_milestones_description)
        }

        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(sessionResetChannel)
            createNotificationChannel(usageMilestoneChannel)
        }
    }

    fun notifySessionReset() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SESSION_RESET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_session_reset_title))
            .setContentText(context.getString(R.string.notification_session_reset_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SESSION_RESET, notification)
    }

    fun notifyUsageMilestone(currentPercent: Int, crossedThreshold: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clampedPercent = currentPercent.coerceIn(0, 100)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_USAGE_MILESTONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_usage_milestone_title))
            .setContentText(
                context.getString(R.string.notification_usage_milestone_body, clampedPercent)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = NOTIFICATION_ID_USAGE_MILESTONE_BASE + crossedThreshold
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
