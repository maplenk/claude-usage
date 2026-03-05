package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.domain.model.UsageStatus
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CompactUsageMetricCard(
    label: String,
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                metric?.let {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(6.dp)
                            .background(color = statusColor(it.status), shape = CircleShape),
                    )
                }
            }

            if (metric == null) {
                Text(
                    text = "--",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = "${metric.utilization.toCompactPercent()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                UsageProgressBar(
                    progress = (metric.utilization / 100.0).toFloat(),
                    status = metric.status,
                    modifier = Modifier.fillMaxWidth(),
                    height = 5.dp,
                )
                Text(
                    text = metric.resetsAt.toResetLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: UsageStatus) = when (status) {
    UsageStatus.SAFE -> statusSafeColor
    UsageStatus.MODERATE -> statusModerateColor
    UsageStatus.CRITICAL -> statusCriticalColor
}

private fun Instant?.toResetLabel(): String {
    if (this == null) return "No reset time"
    val formatter = DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.US)
        .withZone(ZoneId.systemDefault())
    return "Resets ${formatter.format(this)}"
}

private fun Double.toCompactPercent(): String = String.format(Locale.US, "%.1f", this)
