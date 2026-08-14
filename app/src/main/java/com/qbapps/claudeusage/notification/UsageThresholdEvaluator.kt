package com.qbapps.claudeusage.notification

/**
 * Determines whether usage crossed any alert thresholds on this refresh,
 * returning only the highest crossed threshold when applicable.
 */
object UsageThresholdEvaluator {
    val SESSION_THRESHOLDS = listOf(75, 80, 85, 90, 100)
    val WEEKLY_THRESHOLDS = listOf(70, 80, 90, 100)

    fun highestReachedThreshold(
        currentUtilization: Double?,
        thresholds: List<Int> = SESSION_THRESHOLDS
    ): Int? {
        val current = currentUtilization?.coerceIn(0.0, 100.0) ?: return null
        return thresholds.lastOrNull { threshold -> current >= threshold }
    }

    fun highestCrossedThreshold(
        previousUtilization: Double?,
        currentUtilization: Double?,
        thresholds: List<Int> = SESSION_THRESHOLDS
    ): Int? {
        val current = currentUtilization?.coerceIn(0.0, 100.0) ?: return null
        val currentHighest = highestReachedThreshold(currentUtilization, thresholds) ?: return null
        if (previousUtilization == null) return currentHighest

        val previous = previousUtilization.coerceIn(0.0, 100.0)
        return thresholds.lastOrNull { threshold ->
            previous < threshold && current >= threshold
        }
    }
}
