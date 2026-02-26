package com.qbapps.claudeusage.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageThresholdEvaluatorTest {

    @Test
    fun `highest reached threshold for 84 is 80`() {
        val reached = UsageThresholdEvaluator.highestReachedThreshold(84.0)
        assertEquals(80, reached)
    }

    @Test
    fun `highest reached threshold for 74 is null`() {
        val reached = UsageThresholdEvaluator.highestReachedThreshold(74.0)
        assertNull(reached)
    }

    @Test
    fun `75 to 88 returns highest crossed 85`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 75.0,
            currentUtilization = 88.0
        )

        assertEquals(85, crossed)
    }

    @Test
    fun `74 to 91 returns highest crossed 90`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 74.0,
            currentUtilization = 91.0
        )

        assertEquals(90, crossed)
    }

    @Test
    fun `88 to 89 returns null`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 88.0,
            currentUtilization = 89.0
        )

        assertNull(crossed)
    }

    @Test
    fun `89 to 90 returns 90`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 89.0,
            currentUtilization = 90.0
        )

        assertEquals(90, crossed)
    }

    @Test
    fun `null to 88 returns highest reached 85`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = null,
            currentUtilization = 88.0
        )

        assertEquals(85, crossed)
    }

    @Test
    fun `null to 50 returns null`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = null,
            currentUtilization = 50.0
        )

        assertNull(crossed)
    }

    @Test
    fun `99 to 100 returns 100`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 99.0,
            currentUtilization = 100.0
        )

        assertEquals(100, crossed)
    }

    @Test
    fun `100 to 100 returns null`() {
        val crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization = 100.0,
            currentUtilization = 100.0
        )

        assertNull(crossed)
    }
}
