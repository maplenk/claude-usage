package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

@Composable
fun DashboardMetaCard(
    fetchedAt: Instant?,
    refreshIntervalSeconds: Int,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        )
        Text(
            text = if (isRefreshing) {
                "  Refreshing providers…"
            } else {
                "  Synced ${fetchedAt.relativeAge()} · auto every ${refreshIntervalSeconds}s"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Instant?.relativeAge(now: Instant = Instant.now()): String {
    if (this == null) return "not yet"
    val elapsed = Duration.between(this, now)
    if (elapsed.isNegative || elapsed.seconds < 60L) return "just now"
    if (elapsed.toMinutes() < 60L) return "${elapsed.toMinutes()}m ago"
    if (elapsed.toHours() < 24L) return "${elapsed.toHours()}h ago"
    return "${elapsed.toDays()}d ago"
}
