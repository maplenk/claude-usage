package com.qbapps.claudeusage.ui.dashboard

import java.time.Duration
import java.time.Instant

sealed interface SyncState {
    val fetchedAt: Instant?
    val ageMinutes: Long?

    data class Fresh(
        override val fetchedAt: Instant?,
        override val ageMinutes: Long?,
    ) : SyncState

    data class Ageing(
        override val fetchedAt: Instant,
        override val ageMinutes: Long,
    ) : SyncState

    data class Stale(
        override val fetchedAt: Instant,
        override val ageMinutes: Long,
    ) : SyncState

    data class Offline(
        override val fetchedAt: Instant?,
        override val ageMinutes: Long?,
    ) : SyncState
}

val SyncState.isOffline: Boolean
    get() = this is SyncState.Offline

val SyncState.isStale: Boolean
    get() = this is SyncState.Stale || this is SyncState.Offline

internal fun syncStateFor(
    fetchedAt: Instant?,
    isOnline: Boolean,
    now: Instant = Instant.now(),
): SyncState {
    val ageMinutes = fetchedAt?.let {
        Duration.between(it, now).toMinutes().coerceAtLeast(0L)
    }
    if (!isOnline) return SyncState.Offline(fetchedAt, ageMinutes)
    if (fetchedAt == null) return SyncState.Fresh(fetchedAt = null, ageMinutes = null)
    return when {
        ageMinutes == null || ageMinutes < 5L -> SyncState.Fresh(fetchedAt, ageMinutes)
        ageMinutes < 10L -> SyncState.Ageing(fetchedAt, ageMinutes)
        else -> SyncState.Stale(fetchedAt, ageMinutes)
    }
}
