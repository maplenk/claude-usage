package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.components.ProviderBrand
import com.qbapps.claudeusage.ui.components.ProviderMark
import com.qbapps.claudeusage.ui.components.UsageIndicator
import com.qbapps.claudeusage.ui.components.UsageIndicatorStyle
import com.qbapps.claudeusage.ui.theme.OpenUsageShape
import com.qbapps.claudeusage.ui.theme.OpenUsageText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SessionHeroCard(
    metric: UsageMetric?,
    weeklyMetric: UsageMetric?,
    modifier: Modifier = Modifier,
    useRelativeTime: Boolean = true,
    isStale: Boolean = false,
) {
    val status = if (isStale && metric != null) {
        HeadroomStatus.STALE
    } else {
        metric.headroomStatus()
    }
    val shape = OpenUsageShape.card
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 236.dp)
            .then(
                if (status == HeadroomStatus.CRITICAL) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        if (metric == null) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderLabel("CLAUDE · SESSION")
                    HeadroomStatusChip(status)
                }
                Text(
                    text = "No session data yet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Pull to refresh. Cached usage will stay visible if a later refresh fails.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }

        val utilization = metric.effectiveUtilization().coerceIn(0.0, 100.0)

        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderLabel("CLAUDE · SESSION")
                HeadroomStatusChip(status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = utilization.roundToInt().toString(),
                        style = OpenUsageText.metricHero,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "%",
                        fontSize = 29.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = "${(100.0 - utilization).coerceAtLeast(0.0).roundToInt()}% left",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (utilization >= 90.0) {
                        status.foreground()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            UsageIndicator(
                progress = (utilization / 100.0).toFloat(),
                color = status.foreground(),
                style = if (isStale) UsageIndicatorStyle.STALE else UsageIndicatorStyle.WAVY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (metric.isExpired()) "Last window" else "Resets in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    isStale -> Text(
                        text = "${metric.resetLabel(useRelativeTime)} est.",
                        style = OpenUsageText.countdownSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    metric.isExpired() -> Text(
                        text = "Awaiting fresh data",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = status.foreground(),
                    )
                    metric.resetsAt == null -> Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    useRelativeTime -> CountdownTimer(
                        resetsAt = metric.resetsAt,
                        textStyle = OpenUsageText.countdown,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    else -> Text(
                        text = formatAbsoluteReset(metric.resetsAt),
                        style = OpenUsageText.countdown,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            ClaudeWeeklyReading(
                metric = weeklyMetric,
                useRelativeTime = useRelativeTime,
                isStale = isStale,
            )
        }
    }
}

@Composable
internal fun ProviderLabel(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderMark(
            provider = ProviderBrand.CLAUDE,
            tint = MaterialTheme.colorScheme.primary,
            size = 20.dp,
        )
        Text(
            text = label,
            style = OpenUsageText.providerLabel,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ClaudeWeeklyReading(
    metric: UsageMetric?,
    useRelativeTime: Boolean,
    isStale: Boolean,
) {
    val utilization = metric?.effectiveUtilization()?.coerceIn(0.0, 100.0)
    val status = if (isStale && metric != null) {
        HeadroomStatus.STALE
    } else {
        metric.headroomStatus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderLabel("CLAUDE · WEEKLY")
            HeadroomStatusChip(status)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = utilization?.roundToInt()?.toString() ?: "—",
                    style = OpenUsageText.metricMedium,
                )
                if (utilization != null) {
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    )
                }
            }
            Text(
                text = metric.resetLabel(useRelativeTime) + if (isStale) " est." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        UsageIndicator(
            progress = ((utilization ?: 0.0) / 100.0).toFloat(),
            color = status.foreground(),
            style = if (isStale) UsageIndicatorStyle.STALE else UsageIndicatorStyle.FLAT,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
    }
}

private fun formatAbsoluteReset(resetsAt: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.US)
        .withZone(ZoneId.systemDefault())
    return formatter.format(resetsAt)
}
