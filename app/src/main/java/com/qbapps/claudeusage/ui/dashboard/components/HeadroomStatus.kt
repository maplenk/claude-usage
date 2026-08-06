package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusCriticalContainerColor
import com.qbapps.claudeusage.ui.theme.statusHighColor
import com.qbapps.claudeusage.ui.theme.statusHighContainerColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusModerateContainerColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor
import com.qbapps.claudeusage.ui.theme.statusSafeContainerColor
import com.qbapps.claudeusage.ui.theme.statusUnknownColor
import com.qbapps.claudeusage.ui.theme.statusUnknownContainerColor

internal enum class HeadroomStatus(
    val label: String,
    val glyph: String,
) {
    NORMAL("NORMAL", "●"),
    ELEVATED("ELEVATED", "◐"),
    HIGH("HIGH", "◆"),
    CRITICAL("CRITICAL", "▲"),
    STALE("STALE", "◷"),
}

internal fun UsageMetric?.headroomStatus(): HeadroomStatus = when {
    this == null || isExpired() -> HeadroomStatus.STALE
    effectiveUtilization() >= 90.0 -> HeadroomStatus.CRITICAL
    effectiveUtilization() >= 75.0 -> HeadroomStatus.HIGH
    effectiveUtilization() >= 50.0 -> HeadroomStatus.ELEVATED
    else -> HeadroomStatus.NORMAL
}

@Composable
internal fun HeadroomStatus.foreground(): Color = when (this) {
    HeadroomStatus.NORMAL -> statusSafeColor
    HeadroomStatus.ELEVATED -> statusModerateColor
    HeadroomStatus.HIGH -> statusHighColor
    HeadroomStatus.CRITICAL -> statusCriticalColor
    HeadroomStatus.STALE -> statusUnknownColor
}

@Composable
private fun HeadroomStatus.container(): Color = when (this) {
    HeadroomStatus.NORMAL -> statusSafeContainerColor
    HeadroomStatus.ELEVATED -> statusModerateContainerColor
    HeadroomStatus.HIGH -> statusHighContainerColor
    HeadroomStatus.CRITICAL -> statusCriticalContainerColor
    HeadroomStatus.STALE -> statusUnknownContainerColor
}

@Composable
internal fun HeadroomStatusChip(
    status: HeadroomStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(status.container(), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = status.glyph,
            style = MaterialTheme.typography.labelSmall,
            color = status.foreground(),
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = status.foreground(),
            fontWeight = FontWeight.Bold,
        )
    }
}
