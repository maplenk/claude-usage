package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageMetric
import com.qbapps.claudeusage.ui.theme.Guardrail
import com.qbapps.claudeusage.ui.theme.OpenUsageText
import com.qbapps.claudeusage.ui.theme.colors

internal enum class HeadroomStatus(
    val label: String,
    val guardrail: Guardrail,
) {
    NORMAL("NORMAL", Guardrail.Normal),
    ELEVATED("ELEVATED", Guardrail.Elevated),
    HIGH("HIGH", Guardrail.High),
    CRITICAL("CRITICAL", Guardrail.Critical),
    STALE("STALE", Guardrail.Unknown),
}

internal fun UsageMetric?.headroomStatus(): HeadroomStatus = when {
    this == null || isExpired() -> HeadroomStatus.STALE
    effectiveUtilization() >= Guardrail.CRITICAL_AT -> HeadroomStatus.CRITICAL
    effectiveUtilization() >= Guardrail.HIGH_AT -> HeadroomStatus.HIGH
    effectiveUtilization() >= Guardrail.ELEVATED_AT -> HeadroomStatus.ELEVATED
    else -> HeadroomStatus.NORMAL
}

@Composable
internal fun HeadroomStatus.foreground(): Color = guardrail.colors().fg

@Composable
private fun HeadroomStatus.container(): Color = guardrail.colors().container

/**
 * Status chip: 26dp tall, full radius, vector glyph + word — never colour-only.
 * Non-interactive; contributes to the parent card's merged semantics.
 */
@Composable
internal fun HeadroomStatusChip(
    status: HeadroomStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(26.dp)
            .background(status.container(), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(status.guardrail.glyphRes),
            contentDescription = null, // the word carries the meaning
            tint = status.foreground(),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = status.label,
            style = OpenUsageText.statusLabel,
            color = status.foreground(),
        )
    }
}

/** Legacy call sites that still reach for [MaterialTheme] colours by status. */
@Composable
internal fun HeadroomStatus.foregroundColor(): Color = foreground()
