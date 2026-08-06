package com.qbapps.claudeusage.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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

enum class WidgetSize { COMPACT, HORIZONTAL, WIDE, TALL }

private val Surface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1B1D20))
private val PrimaryText = ColorProvider(day = Color(0xFF1B1B1A), night = Color(0xFFE6E3E1))
private val SecondaryText = ColorProvider(day = Color(0xFF6B6864), night = Color(0xFF7A7873))
private val Track = ColorProvider(day = Color(0xFFDFDCD6), night = Color(0xFF26282C))
private val Divider = ColorProvider(day = Color(0xFFDFDCD6), night = Color(0xFF26282C))
private val RefreshButton = ColorProvider(day = Color(0xFFEDEAE4), night = Color(0xFF26282C))

private val Healthy = ColorProvider(day = Color(0xFF0E6B57), night = Color(0xFF6FDBC4))
private val Elevated = ColorProvider(day = Color(0xFF9A6B00), night = Color(0xFFF4C044))
private val High = ColorProvider(day = Color(0xFF99400F), night = Color(0xFFFFB59A))
private val Unknown = ColorProvider(day = Color(0xFF8A8783), night = Color(0xFF7A7873))

private val HealthyLive = ColorProvider(day = Color(0xBF0E6B57), night = Color(0xBF6FDBC4))
private val ElevatedLive = ColorProvider(day = Color(0xBF9A6B00), night = Color(0xBFF4C044))
private val HighLive = ColorProvider(day = Color(0xBF99400F), night = Color(0xBFFFB59A))

@Composable
internal fun UsageWidgetContent(data: HeadroomWidgetData?, widgetSize: WidgetSize) {
    val displayData = data ?: HeadroomWidgetData(claude = null, codex = null, grok = null)
    val stale = data != null && displayData.isStale
    val offline = !hasValidatedNetwork(LocalContext.current)
    val radius = when (widgetSize) {
        WidgetSize.COMPACT -> 28.dp
        WidgetSize.HORIZONTAL -> 24.dp
        WidgetSize.WIDE, WidgetSize.TALL -> 26.dp
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(radius)
            .background(Surface)
            .clickable(actionRunCallback<OpenAppActionCallback>()),
        contentAlignment = Alignment.Center,
    ) {
        when (widgetSize) {
            WidgetSize.COMPACT -> CompactLayout(displayData, stale, offline)
            WidgetSize.HORIZONTAL -> HorizontalLayout(displayData, stale)
            WidgetSize.WIDE -> WideLayout(displayData, stale, offline)
            WidgetSize.TALL -> TallLayout(displayData, stale, offline)
        }
    }
}

@Composable
private fun CompactLayout(data: HeadroomWidgetData, stale: Boolean, offline: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        data.providers.forEach { provider ->
            CompactProvider(
                provider = provider,
                stale = stale,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        if (offline) {
            OfflineFooter(compact = true)
        }
    }
}

@Composable
private fun CompactProvider(
    provider: WidgetProvider,
    stale: Boolean,
    modifier: GlanceModifier,
) {
    val value = provider.usedPercent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(providerAction(provider))
            .semantics { contentDescription = provider.accessibilityLabel() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderMark(
                provider = provider,
                size = 15.dp,
                containerSize = 24.dp,
                stale = stale,
            )
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ValueOrAdd(provider = provider, fontSize = 24, stale = stale)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        UsageBar(percent = value, width = 134.dp, height = 6.dp, stale = stale)
        provider.liveUsedPercent?.let { live ->
            Spacer(GlanceModifier.height(2.dp))
            UsageBar(
                percent = live,
                width = 134.dp,
                height = 3.dp,
                stale = stale,
                live = true,
            )
        }
    }
}

@Composable
private fun HorizontalLayout(data: HeadroomWidgetData, stale: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.providers.forEach { provider ->
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(providerAction(provider))
                    .semantics { contentDescription = provider.accessibilityLabel() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderMark(provider = provider, size = 20.dp, stale = stale)
                    Spacer(GlanceModifier.width(7.dp))
                    ValueOrAdd(provider = provider, fontSize = 26, stale = stale)
                }
                Spacer(GlanceModifier.height(6.dp))
                UsageBar(
                    percent = provider.usedPercent,
                    width = 72.dp,
                    height = 4.dp,
                    stale = stale,
                )
            }
        }
    }
}

@Composable
private fun WideLayout(data: HeadroomWidgetData, stale: Boolean, offline: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(18.dp)) {
        data.providers.forEach { provider ->
            WideProvider(
                provider = provider,
                stale = stale,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        SyncFooter(data = data, stale = stale, offline = offline, showRefresh = false)
    }
}

@Composable
private fun WideProvider(
    provider: WidgetProvider,
    stale: Boolean,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(providerAction(provider))
            .semantics { contentDescription = provider.accessibilityLabel() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderMark(provider = provider, size = 17.dp, stale = stale)
        Spacer(GlanceModifier.width(12.dp))
        Column(
            modifier = GlanceModifier.width(171.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UsageBar(
                percent = provider.usedPercent,
                width = 171.dp,
                height = 7.dp,
                stale = stale,
            )
            provider.liveUsedPercent?.let { live ->
                Spacer(GlanceModifier.height(3.dp))
                UsageBar(
                    percent = live,
                    width = 171.dp,
                    height = 3.dp,
                    stale = stale,
                    live = true,
                )
            }
        }
        Spacer(GlanceModifier.width(12.dp))
        Box(GlanceModifier.width(34.dp), contentAlignment = Alignment.CenterEnd) {
            ValueOrAdd(provider = provider, fontSize = 20, stale = stale)
        }
        Spacer(GlanceModifier.width(12.dp))
        Text(
            text = formatReset(provider.longWindow?.resetsAt, stale),
            modifier = GlanceModifier.width(46.dp),
            style = TextStyle(
                color = if (stale) Unknown else SecondaryText,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun TallLayout(data: HeadroomWidgetData, stale: Boolean, offline: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(20.dp)) {
        data.providers.forEach { provider ->
            TallProvider(
                provider = provider,
                stale = stale,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        SyncFooter(data = data, stale = stale, offline = offline, showRefresh = true)
    }
}

@Composable
private fun TallProvider(
    provider: WidgetProvider,
    stale: Boolean,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(providerAction(provider))
            .semantics { contentDescription = provider.accessibilityLabel() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderMark(provider = provider, size = 19.dp, stale = stale)
            Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatReset(provider.longWindow?.resetsAt, stale),
                        style = TextStyle(
                            color = if (stale) Unknown else SecondaryText,
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    ValueOrAdd(provider = provider, fontSize = 28, stale = stale)
                }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        UsageBar(
            percent = provider.usedPercent,
            width = 300.dp,
            height = 8.dp,
            stale = stale,
        )
        provider.liveUsedPercent?.let { live ->
            Spacer(GlanceModifier.height(3.dp))
            UsageBar(
                percent = live,
                width = 300.dp,
                height = 3.dp,
                stale = stale,
                live = true,
            )
        }
    }
}

@Composable
private fun ProviderMark(
    provider: WidgetProvider,
    size: Dp,
    stale: Boolean,
    containerSize: Dp = size,
) {
    Box(modifier = GlanceModifier.size(containerSize), contentAlignment = Alignment.Center) {
        if (stale) {
            Image(
                provider = ImageProvider(provider.iconRes),
                contentDescription = provider.provider,
                modifier = GlanceModifier.size(size),
                colorFilter = ColorFilter.tint(Unknown),
            )
        } else {
            Image(
                provider = ImageProvider(provider.iconRes),
                contentDescription = provider.provider,
                modifier = GlanceModifier.size(size),
            )
        }
    }
}

@Composable
private fun ValueOrAdd(provider: WidgetProvider, fontSize: Int, stale: Boolean) {
    val value = provider.usedPercent
    Text(
        text = value?.toString() ?: "Add",
        style = TextStyle(
            color = when {
                stale -> Unknown
                value == null -> SecondaryText
                else -> PrimaryText
            },
            fontSize = (if (value == null) 12 else fontSize).sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun UsageBar(
    percent: Int?,
    width: Dp,
    height: Dp,
    stale: Boolean,
    live: Boolean = false,
) {
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(height / 2)
            .background(Track),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (percent != null && percent > 0) {
            Box(
                modifier = GlanceModifier
                    .width(width * (percent.coerceIn(0, 100) / 100f))
                    .height(height)
                    .cornerRadius(height / 2)
                    .background(severityColor(percent, stale, live)),
            ) {}
        }
    }
}

@Composable
private fun SyncFooter(
    data: HeadroomWidgetData,
    stale: Boolean,
    offline: Boolean,
    showRefresh: Boolean,
) {
    val footerHeight = if (showRefresh) 48.dp else 17.dp
    val text = when {
        offline -> "No connection"
        stale -> "Data from ${formatAge(data.oldestFetchedAt)}"
        else -> "Synced ${formatAge(data.oldestFetchedAt)}"
    }
    val color = if (stale || offline) Unknown else Healthy

    Column(modifier = GlanceModifier.fillMaxWidth().height(footerHeight)) {
        if (showRefresh) {
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
            Spacer(GlanceModifier.height(3.dp))
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .semantics { contentDescription = text },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(color)) {}
            Spacer(GlanceModifier.width(7.dp))
            Text(text, style = TextStyle(color = color, fontSize = 10.5.sp))
            if (showRefresh) {
                Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = GlanceModifier
                            .size(48.dp)
                            .clickable(actionRunCallback<RefreshActionCallback>())
                            .semantics { contentDescription = "Refresh usage" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(24.dp)
                                .cornerRadius(12.dp)
                                .background(RefreshButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↻", style = TextStyle(color = SecondaryText, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineFooter(compact: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (compact) 17.dp else 20.dp)
            .semantics { contentDescription = "No connection" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(Unknown)) {}
        Spacer(GlanceModifier.width(7.dp))
        Text("No connection", style = TextStyle(color = Unknown, fontSize = 12.sp))
    }
}

private fun providerAction(provider: WidgetProvider): Action = if (provider.usedPercent == null) {
    actionRunCallback<OpenSettingsActionCallback>()
} else {
    actionRunCallback<OpenProviderActionCallback>(
        parameters = actionParametersOf(ProviderParameterKey to provider.provider),
    )
}

private val HeadroomWidgetData.isStale: Boolean
    get() = oldestFetchedAt?.let {
        Duration.between(it, Instant.now()).toMinutes() >= 10L
    } ?: true

private fun WidgetProvider.accessibilityLabel(): String {
    val weekly = usedPercent ?: return "$provider not connected. Tap to add account."
    val session = liveUsedPercent?.let { ", short window $it percent used" }.orEmpty()
    return "$provider weekly, $weekly percent used, ${remainingPercent ?: 0} percent left$session"
}

private fun severityColor(
    percent: Int,
    stale: Boolean,
    live: Boolean,
): androidx.glance.unit.ColorProvider {
    if (stale) return Unknown
    return when {
        percent >= 90 -> if (live) HighLive else High
        percent >= 50 -> if (live) ElevatedLive else Elevated
        else -> if (live) HealthyLive else Healthy
    }
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

private fun hasValidatedNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
