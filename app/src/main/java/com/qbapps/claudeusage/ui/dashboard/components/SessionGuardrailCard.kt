package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailEvaluator
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailState
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.theme.OpenUsageShape
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun SessionGuardrailCard(
    metric: UsageMetric?,
    history: List<UsageHistoryPoint>,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(metric?.resetsAt, history.size) {
        while (true) {
            now = Instant.now()
            delay(60_000L)
        }
    }

    val insights = remember(metric, history, now) {
        SessionGuardrailEvaluator.evaluate(
            currentMetric = metric,
            history = history,
            now = now,
        )
    }

    val shouldShowAdvisory = insights.willHitCapBeforeReset ||
        (metric?.effectiveUtilization() ?: 0.0) >= 90.0
    if (!shouldShowAdvisory) return
    val advisoryStatus = when (insights.state) {
        SessionGuardrailState.SAFE,
        SessionGuardrailState.STEADY -> metric.headroomStatus()
        SessionGuardrailState.WATCH -> HeadroomStatus.ELEVATED
        SessionGuardrailState.HIGH -> HeadroomStatus.HIGH
        SessionGuardrailState.CRITICAL -> HeadroomStatus.CRITICAL
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = advisoryStatus.foreground().copy(alpha = 0.45f),
                shape = OpenUsageShape.card,
            ),
        shape = OpenUsageShape.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pace advisory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                HeadroomStatusChip(advisoryStatus)
            }

            Text(
                text = if (insights.willHitCapBeforeReset) {
                    "At this pace you may hit the cap in ${insights.predictedTimeToCapMinutes?.let(::formatMinutes) ?: "under an hour"}."
                } else {
                    "Very little session headroom remains."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Resets in ${insights.timeToResetMinutes?.let(::formatMinutes) ?: "—"}. Weekly limits are unaffected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMinutes(totalMinutes: Long): String {
    if (totalMinutes <= 0L) return "0m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
