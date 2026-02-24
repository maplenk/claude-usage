package com.qbapps.claudeusage.domain.model

enum class UsageStatus {
    SAFE,
    MODERATE,
    CRITICAL;

    companion object {
        fun fromUtilization(value: Double): UsageStatus = when {
            value >= 80.0 -> CRITICAL
            value >= 50.0 -> MODERATE
            else -> SAFE
        }
    }
}
