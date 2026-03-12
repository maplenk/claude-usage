package com.qbapps.claudeusage.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.qbapps.claudeusage.domain.model.UsageStatus
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

object PillWidgetRenderer {

    enum class IconType { PIE_CHART, TIMER }

    fun renderUsage(
        context: Context,
        percentage: Double,
        status: UsageStatus?,
        widthDp: Int = 160,
        heightDp: Int = 46,
    ): Bitmap {
        val dark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()

        val pct = percentage.coerceIn(0.0, 100.0)
        val fillFraction = (pct / 100.0).toFloat()
        val valueText = if (status != null) "${pct.roundToInt()}%" else "--%"
        val fillColor = fillColorForStatus(status, dark)
        val iconColor = accentColorForStatus(status, dark)

        return drawPill(w, h, dark, fillFraction, fillColor, IconType.PIE_CHART, iconColor, valueText, pct.toFloat())
    }

    fun renderTimer(
        context: Context,
        resetsAt: Instant?,
        widthDp: Int = 160,
        heightDp: Int = 46,
    ): Bitmap {
        val dark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()

        val remaining = resetsAt?.let {
            val dur = Duration.between(Instant.now(), it)
            if (dur.isNegative) Duration.ZERO else dur
        } ?: Duration.ZERO

        val maxMinutes = 5 * 60f
        val fillFraction = (remaining.toMinutes().toFloat() / maxMinutes).coerceIn(0f, 1f)

        val valueText = if (resetsAt == null) {
            "--"
        } else if (remaining.isZero) {
            "Ready"
        } else {
            val hrs = remaining.toHours()
            val mins = remaining.toMinutes() % 60
            if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        }

        val timerBlue = if (dark) 0xFF64B5F6.toInt() else 0xFF1565C0.toInt()
        val fillBlue = if (dark) 0xFF1A3A5C.toInt() else 0xFF90CAF9.toInt()

        return drawPill(w, h, dark, fillFraction, fillBlue, IconType.TIMER, timerBlue, valueText, 0f)
    }

    private fun drawPill(
        w: Int,
        h: Int,
        dark: Boolean,
        fillFraction: Float,
        fillColor: Int,
        iconType: IconType,
        iconColor: Int,
        valueText: String,
        piePercent: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = h / 2f

        // Background pill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (dark) 0xFF1C1F26.toInt() else 0xFFDDE1EC.toInt()
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bgPaint)

        // Progress fill
        val fillWidth = (w * fillFraction).coerceAtLeast(0f)
        if (fillWidth > 0f) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillColor
            }
            canvas.save()
            canvas.clipRect(0f, 0f, fillWidth, h.toFloat())
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, fillPaint)
            canvas.restore()
        }

        // Icon circle on the left
        val circleMargin = h * 0.1f
        val circleDiameter = h - circleMargin * 2
        val circleCx = circleMargin + circleDiameter / 2f
        val circleCy = h / 2f
        val circleRadius = circleDiameter / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (dark) 0xFF2A2F3A.toInt() else 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(circleCx, circleCy, circleRadius, circlePaint)

        // Draw icon inside circle
        when (iconType) {
            IconType.PIE_CHART -> drawPieChartIcon(canvas, circleCx, circleCy, circleRadius, iconColor, dark, piePercent)
            IconType.TIMER -> drawTimerIcon(canvas, circleCx, circleCy, circleRadius, iconColor, dark)
        }

        // Value text on the right
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = h * 0.38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) 0xFFE4E1E6.toInt() else 0xFF1B1B1F.toInt()
        }
        val textX = (circleCx + circleRadius + w) / 2f
        val textMetrics = textPaint.fontMetrics
        val textY = h / 2f - (textMetrics.ascent + textMetrics.descent) / 2f
        canvas.drawText(valueText, textX, textY, textPaint)

        return bitmap
    }

    private fun drawPieChartIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        dark: Boolean,
        percent: Float,
    ) {
        val pieRadius = radius * 0.55f
        val pieRect = RectF(cx - pieRadius, cy - pieRadius, cx + pieRadius, cy + pieRadius)

        // Track circle (full ring)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = pieRadius * 0.45f
            this.color = if (dark) 0xFF404859.toInt() else 0xFFCDD3E0.toInt()
        }
        canvas.drawArc(pieRect, 0f, 360f, false, trackPaint)

        // Filled arc for usage
        val sweep = (percent.coerceIn(0f, 100f) / 100f) * 360f
        if (sweep > 0f) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = pieRadius * 0.45f
                strokeCap = Paint.Cap.ROUND
                this.color = color
            }
            canvas.drawArc(pieRect, -90f, sweep, false, arcPaint)
        }
    }

    private fun drawTimerIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        @Suppress("UNUSED_PARAMETER") dark: Boolean,
    ) {
        val clockRadius = radius * 0.50f

        // Clock circle outline
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = clockRadius * 0.2f
            this.color = color
        }
        canvas.drawCircle(cx, cy, clockRadius, circlePaint)

        // Clock hands
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = clockRadius * 0.2f
            strokeCap = Paint.Cap.ROUND
            this.color = color
        }
        // Minute hand (pointing up)
        canvas.drawLine(cx, cy, cx, cy - clockRadius * 0.65f, handPaint)
        // Hour hand (pointing right)
        canvas.drawLine(cx, cy, cx + clockRadius * 0.45f, cy, handPaint)
    }

    private fun fillColorForStatus(status: UsageStatus?, dark: Boolean): Int = when (status) {
        UsageStatus.CRITICAL -> if (dark) 0xFF8B2020.toInt() else 0xFFEF9A9A.toInt()
        UsageStatus.MODERATE -> if (dark) 0xFF7A6A10.toInt() else 0xFFFFE082.toInt()
        else -> if (dark) 0xFF1A6B5A.toInt() else 0xFF80CBC4.toInt()
    }

    private fun accentColorForStatus(status: UsageStatus?, dark: Boolean): Int = when (status) {
        UsageStatus.CRITICAL -> if (dark) 0xFFEF9A9A.toInt() else 0xFFC62828.toInt()
        UsageStatus.MODERATE -> if (dark) 0xFFFFD54F.toInt() else 0xFFF9A825.toInt()
        else -> if (dark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
    }

    private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
}
