package com.qbapps.claudeusage.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Slider that lets the user pick a refresh interval between 5 and 300 seconds.
 * The current value is displayed as a label above the slider.
 */
@Composable
fun RefreshIntervalSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Refresh interval",
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = formatInterval(value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 5f..300f,
            steps = 58, // (300-5)/5 - 1 = 58 steps for 5-second increments
            modifier = Modifier.fillMaxWidth(),
        )

        // Min/max labels
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                text = "5s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "5m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatInterval(seconds: Int): String {
    return if (seconds >= 60) {
        val mins = seconds / 60
        val secs = seconds % 60
        if (secs == 0) "${mins}m" else "${mins}m ${secs}s"
    } else {
        "${seconds}s"
    }
}
