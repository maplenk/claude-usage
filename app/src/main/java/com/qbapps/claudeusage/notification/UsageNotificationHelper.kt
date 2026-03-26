package com.qbapps.claudeusage.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.qbapps.claudeusage.MainActivity
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.UsageStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
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
        private const val CHANNEL_ID_PERSISTENT = "persistent_usage"
        private const val NOTIFICATION_ID_SESSION_RESET = 1001
        private const val NOTIFICATION_ID_USAGE_MILESTONE_BASE = 2000
        private const val NOTIFICATION_ID_CAP_RISK = 3001
        private const val NOTIFICATION_ID_RESET_SOON = 3002
        private const val NOTIFICATION_ID_BELOW_PACE = 3003
        const val NOTIFICATION_ID_PERSISTENT = 4001
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

        val persistentChannel = NotificationChannel(
            CHANNEL_ID_PERSISTENT,
            "Ongoing Usage Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification showing current session usage."
            setShowBadge(false)
        }

        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(sessionResetChannel)
            createNotificationChannel(usageMilestoneChannel)
            createNotificationChannel(guardrailChannel)
            createNotificationChannel(persistentChannel)
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

    fun updatePersistentNotification(usage: ClaudeUsage) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val metric = usage.fiveHour
        val now = Instant.now()
        val utilization = metric?.effectiveUtilization(now) ?: 0.0
        val status = metric?.effectiveStatus(now) ?: UsageStatus.SAFE
        val utilPercent = utilization.coerceIn(0.0, 100.0).toInt()

        val title = "Session: $utilPercent%"
        val text = when {
            metric == null -> "No session data"
            metric.isExpired(now) -> "Session expired"
            metric.resetsAt != null -> "Resets in ${formatCountdown(metric.resetsAt, now)}"
            else -> "No reset scheduled"
        }

        val colorInt = when (status) {
            UsageStatus.SAFE -> 0xFF2E7D32.toInt()
            UsageStatus.MODERATE -> 0xFFF9A825.toInt()
            UsageStatus.CRITICAL -> 0xFFC62828.toInt()
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID_PERSISTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(createSmallBatteryIcon(utilPercent))
            .setLargeIcon(createColoredBatteryBitmap(utilPercent, colorInt))
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, utilPercent, false)
            .setColor(colorInt)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    fun cancelPersistentNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PERSISTENT)
    }

    /** Monochrome battery cell for the status bar (ALPHA_8, horizontal). */
    private fun createSmallBatteryIcon(percent: Int): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        drawBatteryCell(canvas, size, size, percent, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 90, boldText = true)
        return IconCompat.createWithBitmap(bitmap)
    }

    /** Full-color battery cell for the notification shade large icon. */
    private fun createColoredBatteryBitmap(percent: Int, statusColor: Int): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBatteryCell(canvas, size, size, percent, statusColor, 0xFFFFFFFF.toInt(), 255, boldText = false)
        return bitmap
    }

    /**
     * Draws a vertical battery cell (like Samsung's battery icon).
     * Tall body filling most of the canvas, terminal cap on top,
     * fill rises from bottom proportional to percentage.
     */
    private fun drawBatteryCell(
        canvas: Canvas,
        w: Int,
        h: Int,
        percent: Int,
        fillColor: Int,
        outlineColor: Int,
        fillAlpha: Int,
        boldText: Boolean,
    ) {
        val stroke = w * 0.05f

        // Vertical body — tall and wide, cap on top
        val bodyLeft = w * 0.12f
        val bodyTop = h * 0.12f
        val bodyRight = w * 0.88f
        val bodyBottom = h * 0.96f
        val bodyWidth = bodyRight - bodyLeft
        val bodyHeight = bodyBottom - bodyTop
        val bodyRadius = bodyWidth * 0.20f
        val bodyRect = RectF(bodyLeft, bodyTop, bodyRight, bodyBottom)

        // Terminal cap on top, centered
        val capWidth = bodyWidth * 0.36f
        val capHeight = h * 0.07f
        val capLeft = (bodyLeft + bodyRight - capWidth) / 2f
        val capBottom = bodyTop + stroke * 0.4f
        val capRect = RectF(capLeft, capBottom - capHeight, capLeft + capWidth, capBottom)

        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = outlineColor
        }
        canvas.drawRoundRect(capRect, capWidth * 0.3f, capWidth * 0.3f, capPaint)

        // Battery outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = outlineColor
        }
        canvas.drawRoundRect(bodyRect, bodyRadius, bodyRadius, outlinePaint)

        // Fill level — rises from bottom
        val fillPct = percent.coerceIn(0, 100) / 100f
        val inset = stroke + stroke * 0.5f
        val fillLeft = bodyLeft + inset
        val fillRight = bodyRight - inset
        val fillBottom = bodyBottom - inset
        val fillTop = bodyTop + inset
        val fillMaxHeight = fillBottom - fillTop
        val fillHeight = fillMaxHeight * fillPct
        if (fillHeight > 0f) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillColor
                alpha = fillAlpha
            }
            val fillRadius = bodyRadius * 0.4f
            canvas.drawRoundRect(
                RectF(fillLeft, fillBottom - fillHeight, fillRight, fillBottom),
                fillRadius, fillRadius, fillPaint,
            )
        }

        // Percentage text centered in body
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = bodyWidth * (if (percent >= 100) 0.48f else 0.56f)
            if (boldText) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = w * 0.02f
            }
        }
        val textX = (bodyLeft + bodyRight) / 2f
        val textY = (bodyTop + bodyBottom) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("$percent", textX, textY, textPaint)
    }

    private fun formatCountdown(resetsAt: Instant, now: Instant): String {
        val totalMinutes = Duration.between(now, resetsAt).toMinutes().coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val remaining = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${remaining}m" else "${remaining}m"
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
