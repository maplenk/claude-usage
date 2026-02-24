package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.domain.model.UsageStatus
import com.qbapps.claudeusage.ui.theme.statusCriticalColor
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor

/**
 * Animated linear progress indicator whose color reflects the usage status.
 *
 * @param progress Normalised progress value in the range [0.0, 1.0].
 * @param status  The current [UsageStatus] that determines bar color.
 */
@Composable
fun UsageProgressBar(
    progress: Float,
    status: UsageStatus,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "usage_progress",
    )

    val color = when (status) {
        UsageStatus.SAFE -> statusSafeColor
        UsageStatus.MODERATE -> statusModerateColor
        UsageStatus.CRITICAL -> statusCriticalColor
    }

    val trackColor = color.copy(alpha = 0.20f)

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
    )
}
