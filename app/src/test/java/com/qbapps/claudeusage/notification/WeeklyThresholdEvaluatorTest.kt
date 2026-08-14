package com.qbapps.claudeusage.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyThresholdEvaluatorTest {

    private val windowMs = 1_700_000_000_000L
    private val nextWindowMs = windowMs + 7L * 24L * 60L * 60L * 1_000L

    @Test
    fun `weekly ladder starts at 70`() {
        assertEquals(
            70,
            UsageThresholdEvaluator.highestReachedThreshold(
                currentUtilization = 72.0,
                thresholds = UsageThresholdEvaluator.WEEKLY_THRESHOLDS
            )
        )
        assertNull(
            UsageThresholdEvaluator.highestReachedThreshold(
                currentUtilization = 69.0,
                thresholds = UsageThresholdEvaluator.WEEKLY_THRESHOLDS
            )
        )
    }

    @Test
    fun `session ladder is unchanged by the weekly ladder`() {
        assertNull(UsageThresholdEvaluator.highestReachedThreshold(72.0))
        assertEquals(75, UsageThresholdEvaluator.highestReachedThreshold(75.0))
    }

    @Test
    fun `crossing 70 raises the 70 alert`() {
        val decision = evaluate(utilization = 71.0, lastNotifiedThreshold = null)

        assertEquals(70, decision.thresholdToNotify)
        assertEquals(70, decision.lastNotifiedThreshold)
        assertEquals(windowMs, decision.windowResetsAtMs)
    }

    @Test
    fun `jump from 70 to 93 raises only the highest threshold`() {
        val decision = evaluate(utilization = 93.0, lastNotifiedThreshold = 70)

        assertEquals(90, decision.thresholdToNotify)
        assertEquals(90, decision.lastNotifiedThreshold)
    }

    @Test
    fun `already notified threshold does not repeat`() {
        val decision = evaluate(utilization = 84.0, lastNotifiedThreshold = 80)

        assertNull(decision.thresholdToNotify)
        assertEquals(80, decision.lastNotifiedThreshold)
    }

    @Test
    fun `staying above a notified threshold at 100 does not repeat`() {
        val decision = evaluate(utilization = 100.0, lastNotifiedThreshold = 100)

        assertNull(decision.thresholdToNotify)
        assertEquals(100, decision.lastNotifiedThreshold)
    }

    @Test
    fun `upgrade while already above a threshold alerts once`() {
        val first = WeeklyThresholdEvaluator.evaluate(
            utilization = 88.0,
            windowResetsAtMs = windowMs,
            lastNotifiedThreshold = null,
            lastWindowResetsAtMs = null
        )
        assertEquals(80, first.thresholdToNotify)

        val second = WeeklyThresholdEvaluator.evaluate(
            utilization = 88.0,
            windowResetsAtMs = windowMs,
            lastNotifiedThreshold = first.lastNotifiedThreshold,
            lastWindowResetsAtMs = first.windowResetsAtMs
        )
        assertNull(second.thresholdToNotify)
    }

    @Test
    fun `new weekly window clears dedup state`() {
        val decision = WeeklyThresholdEvaluator.evaluate(
            utilization = 74.0,
            windowResetsAtMs = nextWindowMs,
            lastNotifiedThreshold = 90,
            lastWindowResetsAtMs = windowMs
        )

        assertEquals(70, decision.thresholdToNotify)
        assertEquals(70, decision.lastNotifiedThreshold)
        assertEquals(nextWindowMs, decision.windowResetsAtMs)
    }

    @Test
    fun `same window does not clear dedup state`() {
        val decision = evaluate(utilization = 91.0, lastNotifiedThreshold = 90)

        assertNull(decision.thresholdToNotify)
        assertEquals(90, decision.lastNotifiedThreshold)
    }

    @Test
    fun `earlier reset instant is not treated as a new window`() {
        val decision = WeeklyThresholdEvaluator.evaluate(
            utilization = 91.0,
            windowResetsAtMs = windowMs - 60_000L,
            lastNotifiedThreshold = 90,
            lastWindowResetsAtMs = windowMs
        )

        assertNull(decision.thresholdToNotify)
    }

    @Test
    fun `dropping below the lowest threshold clears dedup state`() {
        val decision = evaluate(utilization = 12.0, lastNotifiedThreshold = 90)

        assertNull(decision.thresholdToNotify)
        assertNull(decision.lastNotifiedThreshold)
        assertEquals(windowMs, decision.windowResetsAtMs)
    }

    @Test
    fun `climbing again after a drop alerts from the lowest threshold`() {
        val dropped = evaluate(utilization = 0.0, lastNotifiedThreshold = 100)
        val climbed = evaluate(
            utilization = 71.0,
            lastNotifiedThreshold = dropped.lastNotifiedThreshold
        )

        assertEquals(70, climbed.thresholdToNotify)
    }

    @Test
    fun `null utilization clears dedup state without alerting`() {
        val decision = evaluate(utilization = null, lastNotifiedThreshold = 80)

        assertNull(decision.thresholdToNotify)
        assertNull(decision.lastNotifiedThreshold)
    }

    private fun evaluate(
        utilization: Double?,
        lastNotifiedThreshold: Int?
    ): WeeklyThresholdDecision = WeeklyThresholdEvaluator.evaluate(
        utilization = utilization,
        windowResetsAtMs = windowMs,
        lastNotifiedThreshold = lastNotifiedThreshold,
        lastWindowResetsAtMs = windowMs
    )
}
