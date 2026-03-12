package com.qbapps.claudeusage.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.qbapps.claudeusage.R
import com.qbapps.claudeusage.domain.model.ClaudeUsage
import com.qbapps.claudeusage.domain.model.UsageStatus
import java.time.Duration
import java.time.Instant

enum class WidgetSize {
    SMALL,
    MEDIUM,
    LARGE,
}

private val Surface = ColorProvider(day = Color(0xFFF6F7FB), night = Color(0xFF111217))
private val SurfaceElevated = ColorProvider(day = Color(0xFFECEFF8), night = Color(0xFF1E222B))
private val PrimaryText = ColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFF4EEE4))
private val SecondaryText = ColorProvider(day = Color(0xFF454957), night = Color(0xFFD7D0C7))
private val TertiaryText = ColorProvider(day = Color(0xFF6C6F7E), night = Color(0xFFAFA89E))
private val RefreshBtnBg = ColorProvider(day = Color(0xFFE0E5F2), night = Color(0xFF2B313B))

@Composable
fun UsageWidgetContent(usage: ClaudeUsage?, widgetSize: WidgetSize) {
    val baseModifier = GlanceModifier
        .fillMaxSize()
        .clickable(actionRunCallback<OpenAppActionCallback>())

    val modifier = when (widgetSize) {
        WidgetSize.SMALL -> baseModifier
        WidgetSize.MEDIUM -> baseModifier.background(
            ImageProvider(R.drawable.widget_background_semi)
        )
        WidgetSize.LARGE -> baseModifier.background(Surface)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (usage == null) {
            EmptyState(widgetSize)
        } else {
            when (widgetSize) {
                WidgetSize.SMALL -> SmallLayout(usage)
                WidgetSize.MEDIUM -> MediumLayout(usage)
                WidgetSize.LARGE -> LargeLayout(usage)
            }
        }
    }
}

@Composable
private fun EmptyState(widgetSize: WidgetSize) {
    if (widgetSize == WidgetSize.SMALL) {
        Text(
            "C",
            style = TextStyle(color = SecondaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Claude",
                style = TextStyle(color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text("Tap to set up", style = TextStyle(color = SecondaryText, fontSize = 10.sp))
        }
    }
}

@Composable
private fun SmallLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

    val bitmap = WidgetRingRenderer.render(
        context = context,
        percentage = pct,
        status = metric?.status,
        ringDp = 59,
        circularBackground = true,
    )

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "Session ${pct.toInt()}%",
        modifier = GlanceModifier.size(59.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun MediumLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0

    val ringSize = 56
    val pctBitmap = WidgetRingRenderer.render(context, pct, metric?.status, ringDp = ringSize)
    val resetBitmap = WidgetRingRenderer.renderCountdown(
        context = context,
        resetsAt = metric?.resetsAt,
        ringDp = ringSize,
    )

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(pctBitmap),
            contentDescription = "Session ${pct.toInt()}%",
            modifier = GlanceModifier.size(ringSize.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(GlanceModifier.width(12.dp))
        Image(
            provider = ImageProvider(resetBitmap),
            contentDescription = "Reset countdown",
            modifier = GlanceModifier.size(ringSize.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun LargeLayout(usage: ClaudeUsage) {
    val context = LocalContext.current
    val metric = usage.fiveHour
    val pct = metric?.utilization?.coerceIn(0.0, 100.0) ?: 0.0
    val modelMetric = usage.currentModelMetric()

    val bitmap = WidgetRingRenderer.render(context, pct, metric?.status, ringDp = 80)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Session ${pct.toInt()}%",
                modifier = GlanceModifier.size(80.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Session",
                        style = TextStyle(color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    StatusDot(metric?.status, size = 7.dp)
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "${pct.toInt()}% used",
                    style = TextStyle(color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    metric?.resetsAt?.let { formatCountdown(it) } ?: "Ready",
                    style = TextStyle(color = SecondaryText, fontSize = 10.sp),
                )
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricPill("Weekly", usage.sevenDay?.utilization)
            Spacer(GlanceModifier.width(6.dp))
            MetricPill(modelMetric.first, modelMetric.second)
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(26.dp)
                        .cornerRadius(13.dp)
                        .background(RefreshBtnBg)
                        .clickable(actionRunCallback<RefreshActionCallback>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u21BB", style = TextStyle(color = SecondaryText, fontSize = 13.sp))
                }
            }
        }

        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "Updated ${formatRelativeTime(usage.fetchedAt)}",
            style = TextStyle(color = TertiaryText, fontSize = 10.sp),
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: Double?,
) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(10.dp)
            .background(SurfaceElevated)
            .padding(4.dp),
    ) {
        Text(
            text = "$label ${value.toWholePercent()}%",
            style = TextStyle(color = PrimaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun StatusDot(
    status: UsageStatus?,
    size: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2f)
            .background(statusColor(status)),
    ) {}
}

private fun statusColor(status: UsageStatus?) = when (status) {
    UsageStatus.CRITICAL -> ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF9A9A))
    UsageStatus.MODERATE -> ColorProvider(day = Color(0xFFF9A825), night = Color(0xFFFFD54F))
    else -> ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
}

private fun formatCountdown(resetsAt: Instant): String {
    val remaining = Duration.between(Instant.now(), resetsAt)
    if (remaining.isNegative || remaining.isZero) return "Ready"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun Double?.toWholePercent(): Int = this?.coerceIn(0.0, 100.0)?.toInt() ?: 0

private fun ClaudeUsage.currentModelMetric(): Pair<String, Double?> {
    val sonnet = sevenDaySonnet?.utilization
    val opus = sevenDayOpus?.utilization
    if (sonnet == null && opus == null) return "Model" to null
    return if ((sonnet ?: Double.MIN_VALUE) >= (opus ?: Double.MIN_VALUE)) {
        "Sonnet" to sonnet
    } else {
        "Opus" to opus
    }
}

private fun formatRelativeTime(timestamp: Instant): String {
    val elapsed = Duration.between(timestamp, Instant.now())
    if (elapsed.isNegative) return "now"
    val minutes = elapsed.toMinutes()
    val hours = elapsed.toHours()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${elapsed.toDays()}d ago"
    }
}
