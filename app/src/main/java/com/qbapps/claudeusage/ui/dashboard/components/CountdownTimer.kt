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
) {
    var remainingSeconds by remember { mutableLongStateOf(computeRemaining(resetsAt)) }

    LaunchedEffect(resetsAt) {
        while (true) {
            remainingSeconds = computeRemaining(resetsAt)
            if (remainingSeconds <= 0L) break
            delay(1_000L)
        }
    }

    val text = if (remainingSeconds <= 0L) {
        "Expired"
    } else {
        formatDuration(remainingSeconds)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun computeRemaining(target: Instant): Long {
    val diff = target.epochSecond - Instant.now().epochSecond
    return if (diff < 0) 0L else diff
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}
