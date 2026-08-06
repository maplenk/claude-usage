package com.qbapps.claudeusage.ui.dashboard

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateTest {
    private val now = Instant.parse("2026-08-06T12:00:00Z")

    @Test
    fun `freshness follows five and ten minute boundaries`() {
        assertTrue(syncStateFor(now.minusSeconds(4 * 60), true, now) is SyncState.Fresh)
        assertTrue(syncStateFor(now.minusSeconds(5 * 60), true, now) is SyncState.Ageing)
        assertTrue(syncStateFor(now.minusSeconds(10 * 60), true, now) is SyncState.Stale)
    }

    @Test
    fun `offline wins while retaining last seen age`() {
        val state = syncStateFor(now.minusSeconds(12 * 60), false, now)

        assertTrue(state is SyncState.Offline)
        assertEquals(12L, state.ageMinutes)
        assertTrue(state.isOffline)
        assertTrue(state.isStale)
    }

    @Test
    fun `future timestamps are treated as just synced`() {
        val state = syncStateFor(now.plusSeconds(60), true, now)

        assertTrue(state is SyncState.Fresh)
        assertEquals(0L, state.ageMinutes)
    }
}
