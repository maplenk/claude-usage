package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardMetaCard(
    fetchedAt: Instant?,
    selectedOrgId: String?,
    refreshIntervalSeconds: Int,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetaRow("Last updated", fetchedAt.toDisplayTime())
            MetaRow("Organization", selectedOrgId?.shortOrgLabel() ?: "Not selected")
            MetaRow("Auto refresh", "Every ${refreshIntervalSeconds}s")
            MetaRow("Status", if (isRefreshing) "Refreshing…" else "Background sync active")
        }
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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

private fun Instant?.toDisplayTime(): String {
    if (this == null) return "—"
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm:ss a", Locale.US)
        .withZone(ZoneId.systemDefault())
    return formatter.format(this)
}

private fun String.shortOrgLabel(): String {
    if (length <= 10) return this
    return "${take(6)}…${takeLast(4)}"
}
