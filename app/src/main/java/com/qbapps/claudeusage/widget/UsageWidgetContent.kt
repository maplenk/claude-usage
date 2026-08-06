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
import java.time.Duration
import java.time.Instant

enum class WidgetSize { QUAD, RAIL, HERO }

private val Surface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1B1D20))
private val PrimaryText = ColorProvider(day = Color(0xFF1B1B1A), night = Color(0xFFE6E3E1))
private val SecondaryText = ColorProvider(day = Color(0xFF4A4844), night = Color(0xFFA9A6A2))
private val TertiaryText = ColorProvider(day = Color(0xFF7B7972), night = Color(0xFF7A7873))
private val Track = ColorProvider(day = Color(0xFFE5E3DE), night = Color(0xFF313337))
private val RefreshButton = ColorProvider(day = Color(0xFFEBE9E4), night = Color(0xFF26282C))
private val Normal = ColorProvider(day = Color(0xFF10695B), night = Color(0xFF6FDBC4))
private val Elevated = ColorProvider(day = Color(0xFF7A5900), night = Color(0xFFF4C044))
private val High = ColorProvider(day = Color(0xFF99400F), night = Color(0xFFFFB59A))
private val Critical = ColorProvider(day = Color(0xFFA8261F), night = Color(0xFFFFB4AB))
private val Unknown = ColorProvider(day = Color(0xFF4A4844), night = Color(0xFFC9C6C2))

private val ClaudeAccent = ColorProvider(day = Color(0xFF8F5024), night = Color(0xFFFFB786))
private val CodexAccent = ColorProvider(day = Color(0xFF3A5BA0), night = Color(0xFFAFC6FF))
private val GrokAccent = ColorProvider(day = Color(0xFF7B3F9E), night = Color(0xFFE3B7F5))

@Composable
internal fun UsageWidgetContent(data: HeadroomWidgetData?, widgetSize: WidgetSize) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(26.dp)
            .background(Surface)
            .clickable(actionRunCallback<OpenAppActionCallback>()),
        contentAlignment = Alignment.Center,
    ) {
        if (data == null) {
            EmptyState()
        } else {
            when (widgetSize) {
                WidgetSize.QUAD -> QuadLayout(data.metrics)
                WidgetSize.RAIL -> RailLayout(data)
                WidgetSize.HERO -> HeroRailLayout(data)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("OpenUsage", style = TextStyle(color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Text("Tap to connect", style = TextStyle(color = SecondaryText, fontSize = 9.sp))
    }
}

@Composable
private fun QuadLayout(metrics: List<WidgetMetric>) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuadRow(metrics.take(2))
        Spacer(GlanceModifier.height(2.dp))
        QuadRow(metrics.drop(2).take(2))
    }
}

@Composable
private fun QuadRow(metrics: List<WidgetMetric>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(53.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEachIndexed { index, metric ->
            QuadMetric(metric)
            if (index == 0) Spacer(GlanceModifier.width(3.dp))
        }
    }
}

@Composable
private fun QuadMetric(metric: WidgetMetric) {
    val context = LocalContext.current
    val used = metric.usedPercent
    val bitmap = WidgetRingRenderer.render(
        context = context,
        percentage = used?.toDouble() ?: 0.0,
        status = metric.metric?.effectiveStatus(),
        ringDp = 48,
        label = metric.quadLabel(),
    )
    Box(
        modifier = GlanceModifier.width(51.dp).height(51.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = metric.accessibilityLabel(),
            modifier = GlanceModifier.size(48.dp),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(end = 1.dp, bottom = 1.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Image(
                provider = ImageProvider(metric.iconRes),
                contentDescription = metric.provider,
                modifier = GlanceModifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun RailLayout(data: HeadroomWidgetData) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 6.dp)) {
        data.metrics.forEach { metric ->
            RailMetricRow(metric = metric, stale = data.isStale, compact = false)
        }
        WidgetFooter(data = data, showRefresh = false)
    }
}

@Composable
private fun HeroRailLayout(data: HeadroomWidgetData) {
    val session = data.metrics.first()
    val context = LocalContext.current
    val used = session.usedPercent
    val ring = WidgetRingRenderer.render(
        context = context,
        percentage = used?.toDouble() ?: 0.0,
        status = session.metric?.effectiveStatus(),
        ringDp = 76,
        label = "SESSION",
    )

    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(ring),
                contentDescription = session.accessibilityLabel(),
                modifier = GlanceModifier.size(76.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(session.iconRes), "Claude", GlanceModifier.size(15.dp))
                    Spacer(GlanceModifier.width(5.dp))
                    Text("CLAUDE", style = TextStyle(color = ClaudeAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(5.dp))
                StatusLabel(metric = session, stale = data.isStale)
                Spacer(GlanceModifier.height(7.dp))
                Text("Resets in", style = TextStyle(color = TertiaryText, fontSize = 8.sp))
                Text(
                    text = formatReset(session.metric?.resetsAt, data.isStale),
                    style = TextStyle(color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        data.metrics.drop(1).forEach { metric ->
            RailMetricRow(metric = metric, stale = data.isStale, compact = true)
        }
        WidgetFooter(data = data, showRefresh = true)
    }
}

@Composable
private fun RailMetricRow(metric: WidgetMetric, stale: Boolean, compact: Boolean) {
    val used = metric.usedPercent
    val rowHeight = if (compact) 19.dp else 22.dp
    val barWidth = if (compact) 76 else 72
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(metric.iconRes),
            contentDescription = metric.provider,
            modifier = GlanceModifier.size(if (compact) 13.dp else 15.dp),
        )
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = metric.railLabel(),
            style = TextStyle(
                color = if (metric.label == "Session") PrimaryText else SecondaryText,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = if (metric.label == "Session") FontWeight.Bold else FontWeight.Normal,
            ),
            modifier = GlanceModifier.width(if (compact) 54.dp else 58.dp),
        )
        Spacer(GlanceModifier.width(5.dp))
        if (used == null) {
            Text("Not connected", style = TextStyle(color = TertiaryText, fontSize = 8.sp))
            Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("Add", style = TextStyle(color = providerAccent(metric.provider), fontSize = 8.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            UsageBar(
                percent = used,
                stale = stale,
                widthDp = barWidth,
            )
            Spacer(GlanceModifier.width(7.dp))
            Text(
                text = "$used%",
                style = TextStyle(color = PrimaryText, fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.width(31.dp),
            )
            Text(
                text = formatReset(metric.metric?.resetsAt, stale),
                style = TextStyle(color = TertiaryText, fontSize = if (compact) 7.sp else 8.sp),
            )
        }
    }
}

@Composable
private fun UsageBar(percent: Int, stale: Boolean, widthDp: Int) {
    val color = severityColor(percent, stale)
    Box(
        modifier = GlanceModifier
            .width(widthDp.dp)
            .height(6.dp)
            .cornerRadius(3.dp)
            .background(Track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = GlanceModifier
                .width((widthDp * percent.coerceIn(0, 100) / 100f).dp)
                .height(6.dp)
                .cornerRadius(3.dp)
                .background(color),
        ) {}
    }
}

@Composable
private fun StatusLabel(metric: WidgetMetric, stale: Boolean) {
    val used = metric.usedPercent
    Text(
        text = when {
            stale -> "◷  STALE"
            used == null -> "◷  NOT CONNECTED"
            used >= 90 -> "▲  CRITICAL"
            used >= 75 -> "◆  HIGH"
            used >= 50 -> "◐  ELEVATED"
            else -> "●  NORMAL"
        },
        style = TextStyle(
            color = if (used == null) Unknown else severityColor(used, stale),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun WidgetFooter(data: HeadroomWidgetData, showRefresh: Boolean) {
    val worst = data.metrics.mapNotNull { it.usedPercent }.maxOrNull()
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (data.isStale) "◷ Data from ${formatAge(data.oldestFetchedAt)}" else "● Synced ${formatAge(data.oldestFetchedAt)}",
            style = TextStyle(
                color = if (data.isStale) Unknown else Normal,
                fontSize = 7.sp,
            ),
        )
        Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (showRefresh) {
                Box(
                    modifier = GlanceModifier
                        .size(17.dp)
                        .cornerRadius(9.dp)
                        .background(RefreshButton)
                        .clickable(actionRunCallback<RefreshActionCallback>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↻", style = TextStyle(color = SecondaryText, fontSize = 9.sp))
                }
            } else if (worst != null) {
                Text(
                    text = worstStatusLabel(data.metrics),
                    style = TextStyle(color = severityColor(worst, data.isStale), fontSize = 7.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

private val HeadroomWidgetData.isStale: Boolean
    get() = oldestFetchedAt?.let { Duration.between(it, Instant.now()).toMinutes() >= 10L } ?: true

private fun WidgetMetric.quadLabel(): String = when {
    label == "Session" -> "SESSION"
    provider == "Claude" -> "CLAUDE WK"
    provider == "Codex" -> "CODEX WK"
    else -> "GROK WK"
}

private fun WidgetMetric.railLabel(): String = when {
    label == "Session" -> "Session"
    else -> "$provider wk"
}

private fun WidgetMetric.accessibilityLabel(): String = usedPercent?.let {
    "$provider $label, $it percent used, ${remainingPercent ?: 0} percent left"
} ?: "$provider $label not connected"

private fun providerAccent(provider: String) = when (provider) {
    "Codex" -> CodexAccent
    "Grok" -> GrokAccent
    else -> ClaudeAccent
}

private fun severityColor(percent: Int, stale: Boolean) = when {
    stale -> Unknown
    percent >= 90 -> Critical
    percent >= 75 -> High
    percent >= 50 -> Elevated
    else -> Normal
}

private fun worstStatusLabel(metrics: List<WidgetMetric>): String {
    val worst = metrics.maxByOrNull { it.usedPercent ?: -1 } ?: return ""
    val used = worst.usedPercent ?: return ""
    val state = when {
        used >= 90 -> "CRITICAL"
        used >= 75 -> "HIGH"
        used >= 50 -> "ELEVATED"
        else -> "NORMAL"
    }
    return "◆ ${worst.provider.uppercase()} $state"
}

private fun formatReset(resetsAt: Instant?, stale: Boolean): String {
    if (resetsAt == null) return "—"
    val remaining = Duration.between(Instant.now(), resetsAt)
    if (remaining.isNegative || remaining.isZero) return "Ready"
    val prefix = if (stale) "~" else ""
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    return when {
        days > 0 -> "$prefix${days}d ${hours}h"
        hours > 0 -> "$prefix${hours}h ${minutes}m"
        else -> "$prefix${minutes}m"
    }
}

private fun formatAge(timestamp: Instant?): String {
    if (timestamp == null) return "not yet"
    val elapsed = Duration.between(timestamp, Instant.now())
    if (elapsed.isNegative || elapsed.toMinutes() < 1L) return "just now"
    if (elapsed.toMinutes() < 60L) return "${elapsed.toMinutes()}m ago"
    if (elapsed.toHours() < 24L) return "${elapsed.toHours()}h ago"
    return "${elapsed.toDays()}d ago"
}
