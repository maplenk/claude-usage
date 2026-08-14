package com.qbapps.claudeusage.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.qbapps.claudeusage.R
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

private val FourLimitSurface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1B1D20))
private val FourLimitPrimary = ColorProvider(day = Color(0xFF1B1B1A), night = Color(0xFFE6E3E1))
private val FourLimitSecondary = ColorProvider(day = Color(0xFF4A4844), night = Color(0xFF7A7873))
private val FourLimitTrack = ColorProvider(day = Color(0xFFE5E3DE), night = Color(0xFF26282C))
private val FourLimitUnknown = ColorProvider(day = Color(0xFF7B7972), night = Color(0xFFC9C6C2))

private val FourLimitNormal = ColorProvider(day = Color(0xFF10695B), night = Color(0xFF6FDBC4))
private val FourLimitElevated = ColorProvider(day = Color(0xFF7A5900), night = Color(0xFFF4C044))
private val FourLimitHigh = ColorProvider(day = Color(0xFF99400F), night = Color(0xFFFFB59A))
private val FourLimitCritical = ColorProvider(day = Color(0xFFA8261F), night = Color(0xFFFFB4AB))

private val FourLimitClaude = ColorProvider(day = Color(0xFF8F5024), night = Color(0xFFFFB786))
private val FourLimitClaudeMuted = ColorProvider(day = Color(0x808F5024), night = Color(0x80FFB786))
private val FourLimitCodex = ColorProvider(day = Color(0xFF3A5BA0), night = Color(0xFFAFC6FF))
private val FourLimitGrok = ColorProvider(day = Color(0xFF7B3F9E), night = Color(0xFFE3B7F5))

@Composable
internal fun FourLimitWidgetContent(data: HeadroomWidgetData?) {
    val displayData = data ?: HeadroomWidgetData(claude = null, codex = null, grok = null)
    val stale = data == null || displayData.isFourLimitStale
    val offline = !hasFourLimitNetwork(LocalContext.current)
    val layout = fourLimitLayout(LocalSize.current.width, LocalSize.current.height)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(26.dp)
            .background(FourLimitSurface)
            .clickable(actionRunCallback<OpenAppActionCallback>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = layout.verticalPadding),
        ) {
            displayData.fourLimitRows.forEachIndexed { index, metric ->
                if (index > 0) Spacer(GlanceModifier.height(layout.rowGap))
                FourLimitMetricRow(metric = metric, stale = stale, layout = layout)
            }
            if (layout.showFooter) {
                Spacer(GlanceModifier.defaultWeight())
                FourLimitFooter(
                    data = displayData,
                    stale = stale,
                    offline = offline,
                    height = layout.footerHeight,
                )
            }
        }
    }
}

@Composable
private fun FourLimitMetricRow(
    metric: FourLimitWidgetMetric,
    stale: Boolean,
    layout: FourLimitLayout,
) {
    val percent = metric.usedPercent
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(layout.rowHeight)
            .clickable(fourLimitAction(metric))
            .semantics { contentDescription = metric.fourLimitAccessibilityLabel() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(metric.iconRes),
            contentDescription = metric.provider,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(
                when {
                    stale -> FourLimitUnknown
                    metric.mutedMark -> FourLimitClaudeMuted
                    else -> fourLimitProviderColor(metric.provider)
                }
            ),
        )
        Spacer(GlanceModifier.width(layout.gap))
        Text(
            text = metric.label,
            modifier = GlanceModifier.width(layout.labelWidth),
            style = TextStyle(
                color = if (stale) FourLimitUnknown else if (metric.isSession) {
                    FourLimitPrimary
                } else {
                    FourLimitSecondary
                },
                fontSize = 11.sp,
                fontWeight = if (metric.isSession) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        Spacer(GlanceModifier.width(layout.gap))

        if (percent == null) {
            Box(
                modifier = GlanceModifier
                    .width(layout.barWidth + layout.valueWidth + layout.gap),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Not connected",
                    style = TextStyle(color = FourLimitSecondary, fontSize = 10.5.sp),
                )
            }
            Spacer(GlanceModifier.width(layout.gap))
            Box(
                modifier = GlanceModifier
                    .width(layout.resetWidth)
                    .height(layout.rowHeight)
                    .clickable(actionRunCallback<OpenSettingsActionCallback>()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "Add",
                    style = TextStyle(
                        color = fourLimitProviderColor(metric.provider),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        } else {
            FourLimitUsageBar(percent = percent, stale = stale, width = layout.barWidth)
            Spacer(GlanceModifier.width(layout.gap))
            Text(
                text = "$percent%",
                modifier = GlanceModifier.width(layout.valueWidth),
                style = TextStyle(
                    color = if (stale) FourLimitUnknown else FourLimitPrimary,
                    fontSize = layout.valueFontSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(layout.gap))
            Text(
                text = formatFourLimitReset(
                    resetsAt = metric.metric?.resetsAt,
                    stale = stale,
                    compact = metric.isSession,
                ),
                modifier = GlanceModifier.width(layout.resetWidth),
                style = TextStyle(
                    color = if (stale) FourLimitUnknown else FourLimitSecondary,
                    fontSize = 10.5.sp,
                ),
            )
        }
    }
}

@Composable
private fun FourLimitUsageBar(percent: Int, stale: Boolean, width: Dp) {
    val height = 7.dp
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(4.dp)
            .background(FourLimitTrack),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (stale) {
            val segmentWidth = 9.7.dp
            val filledSegments = ceil(percent.coerceIn(0, 100) / 10.0).toInt()
            Row(modifier = GlanceModifier.width(width).height(height)) {
                repeat(10) { index ->
                    Box(
                        modifier = GlanceModifier
                            .width(segmentWidth)
                            .height(height)
                            .cornerRadius(2.dp)
                            .background(if (index < filledSegments) FourLimitUnknown else FourLimitTrack),
                    ) {}
                    if (index < 9) Spacer(GlanceModifier.width(1.dp))
                }
            }
        } else {
            if (percent > 0) {
                Box(
                    modifier = GlanceModifier
                        .width(width * (percent.coerceIn(0, 100) / 100f))
                        .height(height)
                        .cornerRadius(4.dp)
                        .background(fourLimitSeverityColor(percent)),
                ) {}
            }
            FourLimitThresholdTicks(width = width, height = height)
        }
    }
}

@Composable
private fun FourLimitThresholdTicks(width: Dp, height: Dp) {
    Row(modifier = GlanceModifier.width(width).height(height)) {
        Spacer(GlanceModifier.width(width * 0.50f))
        Box(GlanceModifier.width(1.5.dp).height(height).background(FourLimitSurface)) {}
        Spacer(GlanceModifier.width(width * 0.25f - 1.5.dp))
        Box(GlanceModifier.width(1.5.dp).height(height).background(FourLimitSurface)) {}
        Spacer(GlanceModifier.width(width * 0.15f - 1.5.dp))
        Box(GlanceModifier.width(1.5.dp).height(height).background(FourLimitSurface)) {}
    }
}

@Composable
private fun FourLimitFooter(
    data: HeadroomWidgetData,
    stale: Boolean,
    offline: Boolean,
    height: Dp,
) {
    val worst = data.fourLimitRows.maxByOrNull { it.usedPercent ?: -1 }
    val worstPercent = worst?.usedPercent
    val footerText = when {
        offline -> "No connection · ${formatFourLimitAge(data.oldestFetchedAt)}"
        stale -> "Data from ${formatFourLimitAge(data.oldestFetchedAt)} · tap to retry"
        else -> "Synced ${formatFourLimitAge(data.oldestFetchedAt)}"
    }
    val baseModifier = GlanceModifier
        .fillMaxWidth()
        .height(height)
        .semantics { contentDescription = footerText }
    val modifier = if (stale || offline) {
        baseModifier.clickable(actionRunCallback<RefreshActionCallback>())
    } else {
        baseModifier
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val freshnessColor = if (stale || offline) FourLimitUnknown else FourLimitNormal
        Box(
            modifier = GlanceModifier
                .size(5.dp)
                .cornerRadius(3.dp)
                .background(freshnessColor),
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = footerText,
            style = TextStyle(color = freshnessColor, fontSize = 10.sp),
        )
        if (!stale && !offline && worst != null && worstPercent != null) {
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = fourLimitSeverityColor(worstPercent)
                Image(
                    provider = ImageProvider(fourLimitStatusIcon(worstPercent)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint(statusColor),
                )
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = "${worst.fourLimitStatusOwner()} ${fourLimitStatusLabel(worstPercent)}",
                    style = TextStyle(
                        color = statusColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

internal data class FourLimitLayout(
    val gap: Dp,
    val labelWidth: Dp,
    val barWidth: Dp,
    val valueWidth: Dp,
    val resetWidth: Dp,
    val rowHeight: Dp,
    val rowGap: Dp,
    val footerHeight: Dp,
    val verticalPadding: Dp,
    val valueFontSize: TextUnit,
    val showFooter: Boolean,
)

internal fun fourLimitLayout(width: Dp, height: Dp): FourLimitLayout {
    val compactWidth = width < 320.dp
    val gap = if (compactWidth) 6.dp else 10.dp
    val labelWidth = if (compactWidth) 54.dp else 66.dp
    val valueWidth = if (compactWidth) 32.dp else 36.dp
    val resetWidth = if (compactWidth) 38.dp else 44.dp
    val fixedWidth = 32.dp + 16.dp + (gap * 4) + labelWidth + valueWidth + resetWidth
    val barWidth = (width - fixedWidth).coerceAtLeast(36.dp)
    val compactHeight = height < 150.dp
    // Below this the footer cannot coexist with four legible rows inside the declared 70dp minimum.
    val ultraCompactHeight = height < 100.dp

    return FourLimitLayout(
        gap = gap,
        labelWidth = labelWidth,
        barWidth = barWidth,
        valueWidth = valueWidth,
        resetWidth = resetWidth,
        rowHeight = when {
            ultraCompactHeight -> 14.dp
            compactHeight -> 16.dp
            else -> 20.dp
        },
        rowGap = when {
            ultraCompactHeight -> 2.dp
            compactHeight -> 5.dp
            else -> 9.dp
        },
        footerHeight = when {
            ultraCompactHeight -> 0.dp
            compactHeight -> 12.dp
            else -> 14.dp
        },
        verticalPadding = when {
            ultraCompactHeight -> 4.dp
            compactHeight -> 8.dp
            else -> 16.dp
        },
        valueFontSize = if (ultraCompactHeight) 12.sp else 14.sp,
        showFooter = !ultraCompactHeight,
    )
}

private fun fourLimitAction(metric: FourLimitWidgetMetric): Action =
    if (metric.usedPercent == null) {
        actionRunCallback<OpenSettingsActionCallback>()
    } else {
        actionRunCallback<OpenProviderActionCallback>(
            parameters = actionParametersOf(ProviderParameterKey to metric.provider),
        )
    }

private val HeadroomWidgetData.isFourLimitStale: Boolean
    get() = oldestFetchedAt?.let {
        Duration.between(it, Instant.now()).toMinutes() >= 10L
    } ?: true

private fun FourLimitWidgetMetric.fourLimitAccessibilityLabel(): String = usedPercent?.let {
    "$provider $label, $it percent used, ${100 - it} percent left, resets ${formatFourLimitReset(metric?.resetsAt, false, isSession)}"
} ?: "$provider $label not connected. Tap to add account."

private fun FourLimitWidgetMetric.fourLimitStatusOwner(): String =
    if (isSession) "SESSION" else provider.uppercase()

private fun fourLimitProviderColor(provider: String) = when (provider) {
    "Codex" -> FourLimitCodex
    "Grok" -> FourLimitGrok
    else -> FourLimitClaude
}

private fun fourLimitSeverityColor(percent: Int) = when {
    percent >= 90 -> FourLimitCritical
    percent >= 75 -> FourLimitHigh
    percent >= 50 -> FourLimitElevated
    else -> FourLimitNormal
}

private fun fourLimitStatusLabel(percent: Int): String = when {
    percent >= 90 -> "CRITICAL"
    percent >= 75 -> "HIGH"
    percent >= 50 -> "ELEVATED"
    else -> "NORMAL"
}

private fun fourLimitStatusIcon(percent: Int): Int = when {
    percent >= 90 -> R.drawable.ic_guardrail_critical
    percent >= 75 -> R.drawable.ic_guardrail_high
    percent >= 50 -> R.drawable.ic_guardrail_elevated
    else -> R.drawable.ic_guardrail_normal
}

private fun formatFourLimitReset(resetsAt: Instant?, stale: Boolean, compact: Boolean): String {
    if (resetsAt == null) return "—"
    val remaining = Duration.between(Instant.now(), resetsAt)
    if (remaining.isNegative || remaining.isZero) return "Ready"
    val prefix = if (stale) "~" else ""
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    return when {
        days > 0 -> "$prefix${days}d ${hours}h"
        hours > 0 && compact -> "$prefix${hours}h${minutes}m"
        hours > 0 -> "$prefix${hours}h ${minutes}m"
        else -> "$prefix${minutes}m"
    }
}

private fun formatFourLimitAge(timestamp: Instant?): String {
    if (timestamp == null) return "not synced"
    val elapsed = Duration.between(timestamp, Instant.now())
    if (elapsed.isNegative || elapsed.toMinutes() < 1L) return "just now"
    if (elapsed.toMinutes() < 60L) return "${elapsed.toMinutes()}m ago"
    if (elapsed.toHours() < 24L) return "${elapsed.toHours()}h ago"
    return "${elapsed.toDays()}d ago"
}

private fun hasFourLimitNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
