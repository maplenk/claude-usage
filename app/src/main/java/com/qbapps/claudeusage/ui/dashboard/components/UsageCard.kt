package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * A card that displays a single usage metric with label, percentage,
 * progress bar, and optional countdown timer.
 */
@Composable
fun UsageCard(
    label: String,
    metric: UsageMetric?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (metric == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header row: label + status dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusIndicator(status = metric.status)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Large utilization percentage
                val percentColor = when (metric.status) {
                    UsageStatus.SAFE -> statusSafeColor
                    UsageStatus.MODERATE -> statusModerateColor
                    UsageStatus.CRITICAL -> statusCriticalColor
                }

                Text(
                    text = "${String.format("%.1f", metric.utilization)}%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = percentColor,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress bar
                UsageProgressBar(
                    progress = (metric.utilization / 100.0).toFloat(),
                    status = metric.status,
                )

                // Countdown timer (if applicable)
                metric.resetsAt?.let { resetsAt ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Resets in:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CountdownTimer(resetsAt = resetsAt)
                    }
                }
            }
        }
    }
}
