package com.qbapps.claudeusage.data.mapper

import com.qbapps.claudeusage.data.remote.CodexRateLimitDto
import com.qbapps.claudeusage.data.remote.CodexRateLimitWindowDto
import com.qbapps.claudeusage.data.remote.CodexUsageResponseDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexUsageMapperTest {
    @Test
    fun `maps seven day window regardless of primary or secondary position`() {
        val now = Instant.parse("2026-08-06T00:00:00Z")
        val response = CodexUsageResponseDto(
            rateLimit = CodexRateLimitDto(
                primaryWindow = window(seconds = 18_000, used = 12.0, resetAt = 100L),
                secondaryWindow = window(seconds = 604_800, used = 42.0, resetAt = 1_786_150_800L),
            )
        )

        val usage = response.toWeeklyDomain(now)

        assertEquals(42.0, usage?.weekly?.utilization ?: -1.0, 0.0)
        assertEquals(Instant.ofEpochSecond(1_786_150_800L), usage?.weekly?.resetsAt)
        assertEquals(now, usage?.fetchedAt)
    }

    @Test
    fun `does not mislabel five hour window as weekly`() {
        val response = CodexUsageResponseDto(
            rateLimit = CodexRateLimitDto(
                primaryWindow = window(seconds = 18_000, used = 12.0, resetAt = 100L),
                secondaryWindow = null,
            )
        )

        assertNull(response.toWeeklyDomain())
    }

    private fun window(seconds: Long, used: Double, resetAt: Long) =
        CodexRateLimitWindowDto(
            usedPercent = used,
            limitWindowSeconds = seconds,
            resetAtEpochSeconds = resetAt,
            resetAfterSeconds = null,
        )
}
