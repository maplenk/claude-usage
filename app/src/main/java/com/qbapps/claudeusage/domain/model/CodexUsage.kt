package com.qbapps.claudeusage.domain.model

import java.time.Instant

data class CodexUsage(
    val weekly: UsageMetric,
    val fetchedAt: Instant = Instant.now(),
)

data class CodexDeviceCode(
    val verificationUrl: String,
    val userCode: String,
    val deviceAuthId: String,
    val intervalSeconds: Long,
)
