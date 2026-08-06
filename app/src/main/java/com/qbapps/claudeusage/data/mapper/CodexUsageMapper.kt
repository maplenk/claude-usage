package com.qbapps.claudeusage.data.mapper

import com.qbapps.claudeusage.data.remote.CodexApiContract
import com.qbapps.claudeusage.data.remote.CodexRateLimitWindowDto
import com.qbapps.claudeusage.data.remote.CodexUsageResponseDto
import com.qbapps.claudeusage.domain.model.CodexUsage
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Instant
import kotlin.math.abs

fun CodexUsageResponseDto.toWeeklyDomain(now: Instant = Instant.now()): CodexUsage? {
    val windows = listOfNotNull(rateLimit?.primaryWindow, rateLimit?.secondaryWindow)
    val weekly = windows.firstOrNull { window ->
        val duration = window.limitWindowSeconds ?: return@firstOrNull false
        abs(duration - CodexApiContract.WEEKLY_WINDOW_SECONDS) <= WINDOW_TOLERANCE_SECONDS
    } ?: return null

    val utilization = weekly.usedPercent ?: return null
    val resetsAt = weekly.resetInstant(now)
    return CodexUsage(
        weekly = UsageMetric(
            utilization = utilization.coerceIn(0.0, 100.0),
            resetsAt = resetsAt,
        ),
        fetchedAt = now,
    )
}

private fun CodexRateLimitWindowDto.resetInstant(now: Instant): Instant? = when {
    resetAtEpochSeconds != null && resetAtEpochSeconds > 0L ->
        Instant.ofEpochSecond(resetAtEpochSeconds)
    resetAfterSeconds != null && resetAfterSeconds >= 0L ->
        now.plusSeconds(resetAfterSeconds)
    else -> null
}

private const val WINDOW_TOLERANCE_SECONDS = 60L * 60L
