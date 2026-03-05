package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.domain.model.UsageStatus
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusCriticalContainerColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusModerateContainerColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor
import com.qbapps.claudeusage.ui.theme.statusSafeContainerColor

@Composable
fun SessionHeroCard(
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.33f),
        ),
    ) {
        if (metric == null) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Session (5h)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "No session data yet. Pull to refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Session (5h)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusBadge(status = metric.status)
            }

            SessionUsageRing(metric = metric)

            metric.resetsAt?.let { resetsAt ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Resets in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CountdownTimer(
                        resetsAt = resetsAt,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionUsageRing(
    metric: UsageMetric,
) {
    val progress = (metric.utilization / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 650),
        label = "session_hero_ring_progress",
    )

    val progressColor = when (metric.status) {
        UsageStatus.SAFE -> statusSafeColor
        UsageStatus.MODERATE -> statusModerateColor
        UsageStatus.CRITICAL -> statusCriticalColor
    }

    Box(
        modifier = Modifier.size(198.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 16.dp.toPx()
            val padding = strokeWidth / 2f + 8.dp.toPx()
            val arcSize = size.minDimension - (padding * 2f)
            val topLeft = Offset(
                x = (size.width - arcSize) / 2f,
                y = (size.height - arcSize) / 2f,
            )

            drawArc(
                color = progressColor.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${String.format("%.1f", metric.utilization)}%",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                ),
                color = progressColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = statusHeadline(metric.status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: UsageStatus) {
    val containerColor = when (status) {
        UsageStatus.SAFE -> statusSafeContainerColor
        UsageStatus.MODERATE -> statusModerateContainerColor
        UsageStatus.CRITICAL -> statusCriticalContainerColor
    }
    val textColor = when (status) {
        UsageStatus.SAFE -> statusSafeColor
        UsageStatus.MODERATE -> statusModerateColor
        UsageStatus.CRITICAL -> statusCriticalColor
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = status.name.lowercase().replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusHeadline(status: UsageStatus): String = when (status) {
    UsageStatus.SAFE -> "Comfortable"
    UsageStatus.MODERATE -> "Approaching limit"
    UsageStatus.CRITICAL -> "Risk zone"
}
