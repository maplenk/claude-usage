package com.qbapps.claudeusage.notification

/**
 * Decides whether a weekly limit should raise a threshold alert on this refresh.
 *
 * Dedup is keyed on the weekly window identity (`resetsAt`): a later reset instant
 * means a fresh window, so the notified-threshold baseline is dropped. Only the
 * highest reached threshold is ever reported, so a jump from 70% to 93% raises a
 * single 90% alert instead of backfilling the ladder.
 */
object WeeklyThresholdEvaluator {

    fun evaluate(
        utilization: Double?,
        windowResetsAtMs: Long?,
        lastNotifiedThreshold: Int?,
        lastWindowResetsAtMs: Long?
    ): WeeklyThresholdDecision {
        val isNewWindow = windowResetsAtMs != null &&
            lastWindowResetsAtMs != null &&
            windowResetsAtMs > lastWindowResetsAtMs
        val baseline = if (isNewWindow) null else lastNotifiedThreshold

        val currentHighest = UsageThresholdEvaluator.highestReachedThreshold(
            currentUtilization = utilization,
            thresholds = UsageThresholdEvaluator.WEEKLY_THRESHOLDS
        )

        // Below the lowest threshold (new window or usage reset), so clear the
        // baseline for the next climb.
        if (currentHighest == null) {
            return WeeklyThresholdDecision(
                thresholdToNotify = null,
                lastNotifiedThreshold = null,
                windowResetsAtMs = windowResetsAtMs
            )
        }

        // A null baseline covers both a fresh window and users upgrading while
        // already above a threshold: alert once for the current value, then dedupe.
        val thresholdToNotify = if (baseline == null || currentHighest > baseline) {
            currentHighest
        } else {
            null
        }

        return WeeklyThresholdDecision(
            thresholdToNotify = thresholdToNotify,
            lastNotifiedThreshold = maxOf(currentHighest, baseline ?: currentHighest),
            windowResetsAtMs = windowResetsAtMs
        )
    }
}

data class WeeklyThresholdDecision(
    val thresholdToNotify: Int?,
    val lastNotifiedThreshold: Int?,
    val windowResetsAtMs: Long?
)
