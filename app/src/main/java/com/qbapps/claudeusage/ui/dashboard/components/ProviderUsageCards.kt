package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.components.ProviderBrand
import com.qbapps.claudeusage.ui.components.ProviderMark
import com.qbapps.claudeusage.ui.components.UsageIndicator
import com.qbapps.claudeusage.ui.components.UsageIndicatorStyle
import com.qbapps.claudeusage.ui.theme.OpenUsageShape
import com.qbapps.claudeusage.ui.theme.OpenUsageText
import com.qbapps.claudeusage.ui.theme.grokAccentColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CodexWeeklyCard(
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
    useRelativeTime: Boolean = true,
    isStale: Boolean = false,
) {
    ProviderWeeklyCard(
        provider = ProviderBrand.CODEX,
        providerLabel = "CODEX · WEEKLY",
        accent = MaterialTheme.colorScheme.secondary,
        metric = metric,
        modifier = modifier,
        useRelativeTime = useRelativeTime,
        isStale = isStale,
    )
}

@Composable
fun GrokWeeklyCard(
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
    useRelativeTime: Boolean = true,
    isStale: Boolean = false,
) {
    ProviderWeeklyCard(
        provider = ProviderBrand.GROK,
        providerLabel = "GROK · WEEKLY",
        accent = grokAccentColor,
        metric = metric,
        modifier = modifier,
        useRelativeTime = useRelativeTime,
        isStale = isStale,
    )
}

@Composable
private fun ProviderWeeklyCard(
    provider: ProviderBrand,
    providerLabel: String,
    accent: Color,
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
    useRelativeTime: Boolean,
    isStale: Boolean,
) {
    val utilization = metric?.effectiveUtilization()?.coerceIn(0.0, 100.0)
    val status = if (isStale && metric != null) {
        HeadroomStatus.STALE
    } else {
        metric.headroomStatus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = OpenUsageShape.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeeklyProviderLabel(provider, providerLabel, accent)
                HeadroomStatusChip(status)
            }

            if (metric == null) {
                Text(
                    text = "No weekly data yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = utilization?.roundToInt()?.toString() ?: "—",
                            style = OpenUsageText.metricLarge,
                        )
                        Text(
                            text = "% used",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${(100.0 - (utilization ?: 0.0)).coerceAtLeast(0.0).roundToInt()}% left",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }

                UsageIndicator(
                    progress = ((utilization ?: 0.0) / 100.0).toFloat(),
                    color = accent,
                    style = if (isStale) UsageIndicatorStyle.STALE else UsageIndicatorStyle.FLAT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (metric.isExpired()) "Last window" else "Resets in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = metric.resetLabel(useRelativeTime) + if (isStale) " est." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyProviderLabel(
    provider: ProviderBrand,
    label: String,
    accent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderMark(provider = provider, tint = accent, size = 20.dp)
        Text(
            text = label,
            style = OpenUsageText.providerLabel,
            color = accent,
        )
    }
}

internal fun UsageMetric?.resetLabel(useRelativeTime: Boolean): String {
    val resetsAt = this?.resetsAt ?: return "—"
    if (isExpired()) return "Awaiting sync"
    if (!useRelativeTime) {
        return DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(resetsAt)
    }
    val duration = Duration.between(Instant.now(), resetsAt)
    if (duration.isNegative || duration.isZero) return "Expired"
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
