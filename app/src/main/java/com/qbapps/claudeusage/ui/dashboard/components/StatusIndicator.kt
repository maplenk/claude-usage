package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageStatus
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor

/**
 * Small colored dot that visually signals the current usage status.
 */
@Composable
fun StatusIndicator(
    status: UsageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val color = when (status) {
        UsageStatus.SAFE -> statusSafeColor
        UsageStatus.MODERATE -> statusModerateColor
        UsageStatus.CRITICAL -> statusCriticalColor
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
