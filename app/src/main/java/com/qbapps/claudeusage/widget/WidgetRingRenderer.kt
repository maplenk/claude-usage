package com.qbapps.claudeusage.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.qbapps.claudeusage.domain.model.UsageStatus
import kotlin.math.roundToInt

object WidgetRingRenderer {

    fun render(
        context: Context,
        percentage: Double,
        status: UsageStatus?,
        ringDp: Int = 110
    ): Bitmap {
        val dark = isDarkMode(context)
        val density = context.resources.displayMetrics.density
        val sizePx = (ringDp * density).toInt()

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = sizePx * 0.10f
        val gap = strokeWidth / 2 + sizePx * 0.04f
        val rect = RectF(gap, gap, sizePx - gap, sizePx - gap)

        // Track ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            color = if (dark) 0xFF343A47.toInt() else 0xFFE3E7F1.toInt()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        // Progress arc
        val sweep = ((percentage.coerceIn(0.0, 100.0) / 100.0) * 360.0).toFloat()
        if (sweep > 0f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = colorForStatus(status, dark)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, -90f, sweep, false, progressPaint)
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f

        // Percentage text
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) 0xFFE4E1E6.toInt() else 0xFF1B1B1F.toInt()
        }
        val pctText = if (status != null) "${percentage.roundToInt()}%" else "--"
        val pctY = cy + pctPaint.textSize * 0.12f
        canvas.drawText(pctText, cx, pctY, pctPaint)

        // "SESSION" label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.075f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.1f
            color = if (dark) 0xFFA8A6B1.toInt() else 0xFF6A6A74.toInt()
        }
        canvas.drawText("SESSION", cx, pctY + pctPaint.textSize * 0.7f, labelPaint)

        return bitmap
    }

    private fun colorForStatus(status: UsageStatus?, dark: Boolean): Int = when (status) {
        UsageStatus.CRITICAL -> if (dark) 0xFFEF9A9A.toInt() else 0xFFC62828.toInt()
        UsageStatus.MODERATE -> if (dark) 0xFFFFD54F.toInt() else 0xFFF9A825.toInt()
        else -> if (dark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
    }

    private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
}
