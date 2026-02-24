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
)
