package com.qbapps.claudeusage.domain.model

import java.time.Instant

data class ClaudeUsage(
    val fiveHour: UsageMetric?,
    val sevenDay: UsageMetric?,
    val sevenDayOpus: UsageMetric?,
    val sevenDaySonnet: UsageMetric?,
    val fetchedAt: Instant = Instant.now()
)

data class UsageMetric(
    val utilization: Double,
    val resetsAt: Instant?,
    val status: UsageStatus = UsageStatus.fromUtilization(utilization)
) {
    /** Returns effective utilization: 0% if the reset window has already elapsed. */
    fun effectiveUtilization(now: Instant = Instant.now()): Double =
        if (resetsAt != null && now.isAfter(resetsAt)) 0.0 else utilization

    /** Returns effective status based on whether the window has elapsed. */
    fun effectiveStatus(now: Instant = Instant.now()): UsageStatus =
        UsageStatus.fromUtilization(effectiveUtilization(now))

    /** True when the reset window has passed and cached data is stale. */
    fun isExpired(now: Instant = Instant.now()): Boolean =
        resetsAt != null && now.isAfter(resetsAt)
}
