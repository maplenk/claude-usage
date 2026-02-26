package com.qbapps.claudeusage.notification

/**
 * Determines whether session usage crossed any alert thresholds on this refresh,
 * returning only the highest crossed threshold when applicable.
 */
object UsageThresholdEvaluator {
    private val thresholds = listOf(75, 80, 85, 90, 100)

    fun highestReachedThreshold(currentUtilization: Double?): Int? {
        val current = currentUtilization?.coerceIn(0.0, 100.0) ?: return null
        return thresholds.lastOrNull { threshold -> current >= threshold }
    }

    fun highestCrossedThreshold(
        previousUtilization: Double?,
        currentUtilization: Double?
    ): Int? {
        val current = currentUtilization?.coerceIn(0.0, 100.0) ?: return null
        val currentHighest = highestReachedThreshold(currentUtilization) ?: return null
        if (previousUtilization == null) return currentHighest

        val previous = previousUtilization.coerceIn(0.0, 100.0)
        return thresholds.lastOrNull { threshold ->
            previous < threshold && current >= threshold
        }
    }
}
