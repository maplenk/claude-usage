package com.qbapps.claudeusage.domain.model

import java.time.Instant

data class GrokUsage(
    val weekly: UsageMetric,
    val fetchedAt: Instant = Instant.now(),
)

data class GrokDeviceCode(
    val verificationUrl: String,
    val verificationUrlComplete: String?,
    val userCode: String,
    val deviceCode: String,
    val intervalSeconds: Long,
    val expiresAtMs: Long,
)
