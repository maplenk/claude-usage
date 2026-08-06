package com.qbapps.claudeusage.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.ui.dashboard.SyncState
import com.qbapps.claudeusage.ui.theme.OpenUsageText
import com.qbapps.claudeusage.ui.theme.statusModerateColor
import com.qbapps.claudeusage.ui.theme.statusSafeColor
import com.qbapps.claudeusage.ui.theme.statusUnknownColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardMetaCard(
    syncState: SyncState,
    refreshIntervalSeconds: Int,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    var showAbsoluteTime by remember { mutableStateOf(false) }
    val indicatorColor = when (syncState) {
        is SyncState.Fresh -> statusSafeColor
        is SyncState.Ageing -> statusModerateColor
        is SyncState.Stale,
        is SyncState.Offline -> statusUnknownColor
    }
    val text = when {
        isRefreshing -> "Refreshing providers…"
        showAbsoluteTime && syncState.fetchedAt != null ->
            "Last sync ${syncState.fetchedAt?.absoluteTime().orEmpty()}"
        syncState is SyncState.Offline ->
            "Offline · last seen ${syncState.fetchedAt.relativeAge()}"
        syncState is SyncState.Stale ->
            "Last sync ${syncState.fetchedAt.relativeAge()} · data is stale"
        syncState is SyncState.Ageing ->
            "Last sync ${syncState.fetchedAt.relativeAge()} · checking soon"
        else ->
            "Synced ${syncState.fetchedAt.relativeAge()} · auto every ${refreshIntervalSeconds}s"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .pointerInput(syncState.fetchedAt) {
                detectTapGestures(
                    onLongPress = { showAbsoluteTime = !showAbsoluteTime },
                )
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(indicatorColor, CircleShape),
        )
        Text(
            text = "  $text",
            style = OpenUsageText.countdownSmall,
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

private fun Instant.absoluteTime(): String =
    DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(this)
