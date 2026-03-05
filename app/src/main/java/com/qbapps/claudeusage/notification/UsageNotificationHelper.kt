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
        private const val CHANNEL_ID_GUARDRAIL = "session_guardrail"
        private const val NOTIFICATION_ID_SESSION_RESET = 1001
        private const val NOTIFICATION_ID_USAGE_MILESTONE_BASE = 2000
        private const val NOTIFICATION_ID_CAP_RISK = 3001
        private const val NOTIFICATION_ID_RESET_SOON = 3002
        private const val NOTIFICATION_ID_BELOW_PACE = 3003
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

        val guardrailChannel = NotificationChannel(
            CHANNEL_ID_GUARDRAIL,
            "Session guardrail alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Cap risk, reset-soon, and pace alerts."
        }

        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(sessionResetChannel)
            createNotificationChannel(usageMilestoneChannel)
            createNotificationChannel(guardrailChannel)
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

    fun notifyCapRisk(predictedTimeToCapLabel: String, resetInLabel: String) {
        notifyGuardrail(
            notificationId = NOTIFICATION_ID_CAP_RISK,
            title = "Session may hit cap before reset",
            body = "Projected cap in $predictedTimeToCapLabel (reset in $resetInLabel).",
        )
    }

    fun notifyResetSoon(resetInLabel: String) {
        notifyGuardrail(
            notificationId = NOTIFICATION_ID_RESET_SOON,
            title = "Session reset expected soon",
            body = "Reset expected in about $resetInLabel.",
        )
    }

    fun notifyBelowUsualPace() {
        notifyGuardrail(
            notificationId = NOTIFICATION_ID_BELOW_PACE,
            title = "Below your usual pace",
            body = "You are tracking below your typical session usage pace today.",
        )
    }

    private fun notifyGuardrail(
        notificationId: Int,
        title: String,
        body: String,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GUARDRAIL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
