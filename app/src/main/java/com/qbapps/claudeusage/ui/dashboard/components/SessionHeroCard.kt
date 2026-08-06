package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.components.ProviderBrand
import com.qbapps.claudeusage.ui.components.ProviderMark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SessionHeroCard(
    metric: UsageMetric?,
    weeklyMetric: UsageMetric?,
    modifier: Modifier = Modifier,
    useRelativeTime: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        if (metric == null) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderLabel("CLAUDE · SESSION")
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
        val status = metric.headroomStatus()

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
                        style = MaterialTheme.typography.displayLarge,
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
                    text = if (utilization >= 90.0) {
                        "${(100.0 - utilization).coerceAtLeast(0.0).roundToInt()}% left"
                    } else {
                        "used"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (utilization >= 90.0) status.foreground()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            WavyUsageIndicator(
                progress = (utilization / 100.0).toFloat(),
                color = status.foreground(),
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
                        textStyle = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    else -> Text(
                        text = formatAbsoluteReset(metric.resetsAt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
            ClaudeWeeklyReading(
                metric = weeklyMetric,
                useRelativeTime = useRelativeTime,
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ClaudeWeeklyReading(
    metric: UsageMetric?,
    useRelativeTime: Boolean,
) {
    val utilization = metric?.effectiveUtilization()?.coerceIn(0.0, 100.0)
    val status = metric.headroomStatus()
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
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
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
                text = metric.resetLabel(useRelativeTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        FlatUsageBar(
            progress = ((utilization ?: 0.0) / 100.0).toFloat(),
            color = status.foreground(),
            showThresholds = true,
        )
    }
}

@Composable
private fun WavyUsageIndicator(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(650),
        label = "headroom_wavy_progress",
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val thresholdSeparator = MaterialTheme.colorScheme.surfaceContainerLowest

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val amplitude = 3.dp.toPx()
        val wavelength = 18.dp.toPx()
        val stroke = 5.dp.toPx()

        fun wavePath(endX: Float): Path = Path().apply {
            var x = 0f
            moveTo(0f, centerY)
            while (x <= endX) {
                val y = centerY + sin((x / wavelength) * 2.0 * PI).toFloat() * amplitude
                lineTo(x, y)
                x += 2.dp.toPx()
            }
        }

        drawPath(
            path = wavePath(size.width),
            color = track,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (animatedProgress > 0f) {
            drawPath(
                path = wavePath(size.width * animatedProgress),
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        listOf(0.50f, 0.75f, 0.90f).forEach { threshold ->
            val x = size.width * threshold
            drawLine(
                color = thresholdSeparator,
                start = Offset(x, centerY - 7.dp.toPx()),
                end = Offset(x, centerY + 7.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

private fun formatAbsoluteReset(resetsAt: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.US)
        .withZone(ZoneId.systemDefault())
    return formatter.format(resetsAt)
}
