package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Live countdown that refreshes every second until [resetsAt] has passed.
 *
 * Displays a human-readable duration like "2h 45m 12s" or "Expired" when
 * the target instant is in the past.
 */
@Composable
fun CountdownTimer(
    resetsAt: Instant,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var remainingSeconds by remember(resetsAt) {
        mutableLongStateOf(computeRemaining(resetsAt))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(resetsAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                remainingSeconds = computeRemaining(resetsAt)
                if (remainingSeconds <= 0L) break
                delay(1_000L)
            }
        }
    }

    val text = if (remainingSeconds <= 0L) {
        "Expired"
    } else {
        formatDuration(remainingSeconds)
    }

    Text(
        text = text,
        style = textStyle,
        color = color,
        modifier = modifier,
    )
}

private fun computeRemaining(target: Instant): Long {
    val diff = target.epochSecond - Instant.now().epochSecond
    return if (diff < 0) 0L else diff
}

private fun formatDuration(totalSeconds: Long): String {
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${minutes}m ${seconds}s"
        else -> "${minutes}m ${seconds}s"
    }
}
