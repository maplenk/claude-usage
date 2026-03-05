package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.guardrail.BurnPhase
import com.qbapps.claudeusage.domain.guardrail.PaceTrack
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailEvaluator
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailInsights
import com.qbapps.claudeusage.domain.guardrail.SessionGuardrailState
import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale

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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
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
                    text = "Session Guardrail",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GuardrailStateBadge(state = insights.state)
            }

            Text(
                text = stateSummary(insights),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            InsightRow(label = "Pace", value = paceSummary(insights))
            InsightRow(label = "Prediction", value = predictionSummary(insights))
            InsightRow(label = "Burn pattern", value = burnPatternSummary(insights))
            InsightRow(label = "Reset relief", value = resetReliefSummary(insights))
        }
    }
}

@Composable
private fun GuardrailStateBadge(state: SessionGuardrailState) {
    val color = guardrailStateColor(state)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.20f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = state.name.lowercase().replaceFirstChar { it.titlecase() },
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InsightRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun guardrailStateColor(state: SessionGuardrailState) = when (state) {
    SessionGuardrailState.SAFE -> statusSafeColor
    SessionGuardrailState.STEADY -> MaterialTheme.colorScheme.primary
    SessionGuardrailState.WATCH -> statusModerateColor
    SessionGuardrailState.HIGH -> statusModerateColor
    SessionGuardrailState.CRITICAL -> statusCriticalColor
}

private fun stateSummary(insights: SessionGuardrailInsights): String {
    val used = insights.currentUtilization?.let { String.format(Locale.US, "%.1f%% used", it) } ?: "No usage data"
    val reset = insights.timeToResetMinutes?.let { "${formatMinutes(it)} to reset" } ?: "reset time unavailable"
    return "$used · $reset"
}

private fun paceSummary(insights: SessionGuardrailInsights): String = when (insights.paceTrack) {
    PaceTrack.ABOVE_USUAL -> "Above usual pace"
    PaceTrack.BELOW_USUAL -> "Below usual pace"
    PaceTrack.ON_TRACK -> "On track"
    PaceTrack.UNKNOWN -> "Learning baseline"
}

private fun predictionSummary(insights: SessionGuardrailInsights): String {
    if (insights.willHitCapBeforeReset) {
        val eta = insights.predictedTimeToCapMinutes?.let(::formatMinutes) ?: "soon"
        return "Cap risk in $eta"
    }
    val prediction = insights.predictedTimeToCapMinutes
    val reset = insights.timeToResetMinutes
    return when {
        prediction == null -> "Likely safe"
        reset != null && prediction <= reset + 30L -> "Borderline pace"
        else -> "Likely safe"
    }
}

private fun burnPatternSummary(insights: SessionGuardrailInsights): String {
    if (insights.earlyHeavyUsageDetected) {
        return "Heavy usage earlier than normal"
    }
    return when (insights.typicalBurnPhase) {
        BurnPhase.EARLY -> "You usually spike in first hour"
        BurnPhase.MID -> "You usually burn most mid-session"
        BurnPhase.LATE -> "You usually spike in last hour"
        BurnPhase.UNKNOWN -> "Not enough session history"
    }
}

private fun resetReliefSummary(insights: SessionGuardrailInsights): String {
    if (insights.resetReliefSoon) return "Low budget, but reset soon"
    return when {
        insights.timeToResetMinutes == null -> "Reset timing unavailable"
        insights.timeToResetMinutes <= 15L -> "Reset expected very soon"
        else -> "Next reset in ${formatMinutes(insights.timeToResetMinutes)}"
    }
}

private fun formatMinutes(totalMinutes: Long): String {
    if (totalMinutes <= 0L) return "0m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
