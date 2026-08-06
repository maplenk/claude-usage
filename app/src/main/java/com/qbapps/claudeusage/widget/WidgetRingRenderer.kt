package com.qbapps.claudeusage.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.qbapps.claudeusage.domain.model.UsageStatus
import java.time.Instant
import kotlin.math.roundToInt

object WidgetRingRenderer {

    fun render(
        context: Context,
        percentage: Double,
        status: UsageStatus?,
        ringDp: Int = 110,
        circularBackground: Boolean = false,
        label: String = "USED",
    ): Bitmap {
        val dark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val sizePx = (ringDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Filled circle background so the ring is readable on any wallpaper
        if (circularBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (dark) 0xFF1B1D20.toInt() else 0xFFF1EFEB.toInt()
            }
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)
        }

        val isCompactRing = ringDp <= 60
        val strokeWidth = sizePx * if (isCompactRing) 0.135f else 0.11f
        val gap = strokeWidth / 2 + sizePx * if (isCompactRing) 0.02f else 0.04f
        val rect = RectF(gap, gap, sizePx - gap, sizePx - gap)

        // Track ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = if (dark) 0xFF3A3A3D.toInt() else 0xFFCDCBC5.toInt()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        // Progress arc
        val sweep = ((percentage.coerceIn(0.0, 100.0) / 100.0) * 360.0).toFloat()
        if (sweep > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = colorForUtilization(status, percentage, dark)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        // Percentage text
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * if (isCompactRing) 0.30f else 0.24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) 0xFFE6E3E1.toInt() else 0xFF1B1B1A.toInt()
        }
        val pctText = if (status != null) "${percentage.roundToInt()}%" else "--"
        val pctY = cy + pctPaint.textSize * 0.12f
        canvas.drawText(pctText, cx, pctY, pctPaint)

        // "SESSION" label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * if (isCompactRing) 0.09f else 0.075f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.1f
            color = if (dark) 0xFFA9A6A2.toInt() else 0xFF4A4844.toInt()
        }
        canvas.drawText(label, cx, pctY + pctPaint.textSize * 0.7f, labelPaint)

        return bitmap
    }

    fun renderCountdown(
        context: Context,
        resetsAt: Instant?,
        ringDp: Int = 52,
        circularBackground: Boolean = false,
        windowMinutes: Long = 5 * 60L,
    ): Bitmap {
        val dark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val sizePx = (ringDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (circularBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (dark) 0xFF1B1D20.toInt() else 0xFFF1EFEB.toInt()
            }
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)
        }

        val isCompactRing = ringDp <= 60
        val strokeWidth = sizePx * if (isCompactRing) 0.135f else 0.11f
        val gap = strokeWidth / 2 + sizePx * if (isCompactRing) 0.02f else 0.04f
        val rect = RectF(gap, gap, sizePx - gap, sizePx - gap)

        // Track ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = if (dark) 0xFF3A3A3D.toInt() else 0xFFCDCBC5.toInt()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        // Progress arc based on remaining time (5h window)
        val remaining = resetsAt?.let {
            val dur = java.time.Duration.between(java.time.Instant.now(), it)
            if (dur.isNegative) 0L else dur.toMinutes()
        } ?: 0L
        val fraction = (remaining.toFloat() / windowMinutes.coerceAtLeast(1L)).coerceIn(0f, 1f)
        val sweep = fraction * 360f

        if (sweep > 0f) {
            val accentColor = if (dark) 0xFFFFB786.toInt() else 0xFF8F5024.toInt()
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = accentColor
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        // Countdown text
        val countdownText = if (resetsAt == null) {
            "--"
        } else {
            val dur = java.time.Duration.between(java.time.Instant.now(), resetsAt)
            if (dur.isNegative || dur.isZero) "0m" else {
                val h = dur.toHours()
                val m = dur.toMinutes() % 60
                if (h > 0) "${h}h${m}m" else "${m}m"
            }
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * if (isCompactRing) 0.26f else 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) 0xFFE6E3E1.toInt() else 0xFF1B1B1A.toInt()
        }
        val timeY = cy + timePaint.textSize * 0.12f
        canvas.drawText(countdownText, cx, timeY, timePaint)

        // "RESETS" label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * if (isCompactRing) 0.09f else 0.075f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.1f
            color = if (dark) 0xFFA9A6A2.toInt() else 0xFF4A4844.toInt()
        }
        canvas.drawText("RESETS", cx, timeY + timePaint.textSize * 0.7f, labelPaint)

        return bitmap
    }

    private fun colorForUtilization(status: UsageStatus?, percentage: Double, dark: Boolean): Int = when {
        status == null -> if (dark) 0xFFC9C6C2.toInt() else 0xFF4A4844.toInt()
        percentage >= 90.0 -> if (dark) 0xFFFFB4AB.toInt() else 0xFFA8261F.toInt()
        percentage >= 75.0 -> if (dark) 0xFFFFB59A.toInt() else 0xFF99400F.toInt()
        percentage >= 50.0 -> if (dark) 0xFFF4C044.toInt() else 0xFF7A5900.toInt()
        else -> if (dark) 0xFF6FDBC4.toInt() else 0xFF10695B.toInt()
    }

    private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
}
