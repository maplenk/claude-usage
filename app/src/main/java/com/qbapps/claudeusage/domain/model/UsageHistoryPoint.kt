package com.qbapps.claudeusage.domain.model

import java.time.Instant

/**
 * Snapshot of usage values captured at a specific time.
 */
data class UsageHistoryPoint(
    val timestamp: Instant,
    val fiveHourUtilization: Double?,
    val fiveHourResetsAt: Instant?,
    val sevenDayUtilization: Double?,
    val sevenDayOpusUtilization: Double?,
    val sevenDaySonnetUtilization: Double?,
)
