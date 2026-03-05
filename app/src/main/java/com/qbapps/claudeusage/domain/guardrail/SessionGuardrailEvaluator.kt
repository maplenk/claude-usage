package com.qbapps.claudeusage.domain.guardrail

import com.qbapps.claudeusage.domain.model.UsageHistoryPoint
import com.qbapps.claudeusage.domain.model.UsageMetric
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SessionGuardrailState {
    SAFE,
    STEADY,
    WATCH,
    HIGH,
    CRITICAL,
}

enum class PaceTrack {
    ABOVE_USUAL,
    ON_TRACK,
    BELOW_USUAL,
    UNKNOWN,
}

enum class BurnPhase {
    EARLY,
    MID,
    LATE,
    UNKNOWN,
}

data class SessionGuardrailInsights(
    val state: SessionGuardrailState,
    val currentUtilization: Double?,
    val timeToResetMinutes: Long?,
    val paceTrack: PaceTrack,
    val baselineAtThisPoint: Double?,
    val predictedTimeToCapMinutes: Long?,
    val willHitCapBeforeReset: Boolean,
    val typicalBurnPhase: BurnPhase,
    val earlyHeavyUsageDetected: Boolean,
    val resetReliefSoon: Boolean,
) {
    companion object {
        fun empty(): SessionGuardrailInsights = SessionGuardrailInsights(
            state = SessionGuardrailState.SAFE,
            currentUtilization = null,
            timeToResetMinutes = null,
            paceTrack = PaceTrack.UNKNOWN,
            baselineAtThisPoint = null,
            predictedTimeToCapMinutes = null,
            willHitCapBeforeReset = false,
            typicalBurnPhase = BurnPhase.UNKNOWN,
            earlyHeavyUsageDetected = false,
            resetReliefSoon = false,
        )
    }
}

object SessionGuardrailEvaluator {
    private val sessionDuration: Duration = Duration.ofHours(5)
    private const val sessionMinutes = 300L
    private const val paceBaselineWindowMinutes = 12L
    private const val minBaselineSamples = 6
    private const val minSlopeWindowMinutes = 10L

    fun evaluate(
        currentMetric: UsageMetric?,
        history: List<UsageHistoryPoint>,
        now: Instant = Instant.now(),
    ): SessionGuardrailInsights {
        if (currentMetric == null) return SessionGuardrailInsights.empty()

        val currentUtilization = currentMetric.utilization.coerceIn(0.0, 100.0)
        val resetsAt = currentMetric.resetsAt
        val timeToResetMinutes = resetsAt?.let { Duration.between(now, it).toMinutes().coerceAtLeast(0L) }
        val currentElapsedMinutes = resetsAt?.let { reset ->
            Duration.between(reset.minus(sessionDuration), now).toMinutes().coerceIn(0L, sessionMinutes)
        }

        val allSamples = history.mapNotNull { it.toSessionSample() }
        val currentSessionEpoch = resetsAt?.toEpochMilli()

        val currentSessionSamples = allSamples
            .filter { sample -> sample.sessionResetEpochMs == currentSessionEpoch }
            .plus(currentMetric.toCurrentSample(now))
            .filterNotNull()
            .sortedBy { it.timestamp }

        val previousSessionSamples = allSamples.filter { sample ->
            sample.sessionResetEpochMs != currentSessionEpoch
        }

        val baselineAtThisPoint = currentElapsedMinutes?.let { elapsed ->
            baselineForElapsed(previousSessionSamples, elapsed)
        }
        val paceTrack = when {
            baselineAtThisPoint == null -> PaceTrack.UNKNOWN
            currentUtilization - baselineAtThisPoint >= 8.0 -> PaceTrack.ABOVE_USUAL
            currentUtilization - baselineAtThisPoint <= -8.0 -> PaceTrack.BELOW_USUAL
            else -> PaceTrack.ON_TRACK
        }

        val currentSlope = slopeFromRecentWindow(
            samples = currentSessionSamples,
            referenceElapsedMinutes = currentElapsedMinutes,
        )
        val historicalSlope = averageHistoricalSessionSlope(previousSessionSamples)
        val blendedSlope = when {
            currentSlope != null && historicalSlope != null -> (currentSlope * 0.7) + (historicalSlope * 0.3)
            currentSlope != null -> currentSlope
            else -> historicalSlope
        }

        val predictedTimeToCapMinutes = when {
            currentUtilization >= 100.0 -> 0L
            blendedSlope == null || blendedSlope <= 0.03 -> null
            else -> ((100.0 - currentUtilization) / blendedSlope).roundToLong().coerceAtLeast(0L)
        }

        val willHitCapBeforeReset = predictedTimeToCapMinutes != null &&
            timeToResetMinutes != null &&
            predictedTimeToCapMinutes < timeToResetMinutes

        val typicalBurnPhase = averageBurnByPhase(previousSessionSamples).maxByOrNull { it.value }?.key
            ?: BurnPhase.UNKNOWN

        val earlyHeavyUsageDetected = detectEarlyHeavyUsage(
            currentSessionSamples = currentSessionSamples,
            previousSessionSamples = previousSessionSamples,
            currentElapsedMinutes = currentElapsedMinutes,
            typicalBurnPhase = typicalBurnPhase,
        )

        val resetReliefSoon = currentUtilization >= 75.0 &&
            (timeToResetMinutes != null && timeToResetMinutes <= 45)

        val state = deriveState(
            currentUtilization = currentUtilization,
            timeToResetMinutes = timeToResetMinutes,
            paceTrack = paceTrack,
            predictedTimeToCapMinutes = predictedTimeToCapMinutes,
            willHitCapBeforeReset = willHitCapBeforeReset,
        )

        return SessionGuardrailInsights(
            state = state,
            currentUtilization = currentUtilization,
            timeToResetMinutes = timeToResetMinutes,
            paceTrack = paceTrack,
            baselineAtThisPoint = baselineAtThisPoint,
            predictedTimeToCapMinutes = predictedTimeToCapMinutes,
            willHitCapBeforeReset = willHitCapBeforeReset,
            typicalBurnPhase = typicalBurnPhase,
            earlyHeavyUsageDetected = earlyHeavyUsageDetected,
            resetReliefSoon = resetReliefSoon,
        )
    }

    private fun baselineForElapsed(samples: List<SessionSample>, elapsedMinutes: Long): Double? {
        val matches = samples.filter { sample ->
            abs(sample.elapsedMinutes - elapsedMinutes) <= paceBaselineWindowMinutes
        }
        if (matches.size < minBaselineSamples) return null
        return matches.map { it.utilization }.average()
    }

    private fun slopeFromRecentWindow(
        samples: List<SessionSample>,
        referenceElapsedMinutes: Long?,
    ): Double? {
        if (samples.size < 2 || referenceElapsedMinutes == null) return null

        val recent = samples.filter { sample ->
            referenceElapsedMinutes - sample.elapsedMinutes in 0..90
        }.sortedBy { it.elapsedMinutes }

        if (recent.size < 2) return null
        val first = recent.first()
        val last = recent.last()
        val deltaMinutes = (last.elapsedMinutes - first.elapsedMinutes).coerceAtLeast(0L)
        if (deltaMinutes < minSlopeWindowMinutes) return null
        return (last.utilization - first.utilization) / deltaMinutes.toDouble()
    }

    private fun averageHistoricalSessionSlope(samples: List<SessionSample>): Double? {
        val slopes = samples.groupBy { it.sessionResetEpochMs }
            .values
            .mapNotNull { sessionSamples ->
                val ordered = sessionSamples.sortedBy { it.elapsedMinutes }
                if (ordered.size < 2) return@mapNotNull null
                val first = ordered.first()
                val last = ordered.last()
                val deltaMinutes = (last.elapsedMinutes - first.elapsedMinutes).coerceAtLeast(0L)
                if (deltaMinutes < 20L) return@mapNotNull null
                (last.utilization - first.utilization) / deltaMinutes.toDouble()
            }
        return slopes.takeIf { it.isNotEmpty() }?.average()
    }

    private fun averageBurnByPhase(samples: List<SessionSample>): Map<BurnPhase, Double> {
        val bySession = samples.groupBy { it.sessionResetEpochMs }.values
        if (bySession.isEmpty()) return emptyMap()

        val phaseBurns = mutableMapOf<BurnPhase, MutableList<Double>>(
            BurnPhase.EARLY to mutableListOf(),
            BurnPhase.MID to mutableListOf(),
            BurnPhase.LATE to mutableListOf(),
        )

        bySession.forEach { sessionSamples ->
            val sorted = sessionSamples.sortedBy { it.elapsedMinutes }
            BurnPhase.entries
                .filter { it != BurnPhase.UNKNOWN }
                .forEach { phase ->
                    val burn = burnForPhase(sorted, phase)
                    phaseBurns.getValue(phase).add(burn)
                }
        }

        return phaseBurns.mapValues { (_, values) ->
            values.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        }
    }

    private fun detectEarlyHeavyUsage(
        currentSessionSamples: List<SessionSample>,
        previousSessionSamples: List<SessionSample>,
        currentElapsedMinutes: Long?,
        typicalBurnPhase: BurnPhase,
    ): Boolean {
        if (currentElapsedMinutes == null || currentElapsedMinutes > 120L) return false
        if (typicalBurnPhase == BurnPhase.EARLY || typicalBurnPhase == BurnPhase.UNKNOWN) return false

        val currentEarlyBurn = burnForPhase(currentSessionSamples, BurnPhase.EARLY)
        if (currentEarlyBurn <= 0.0) return false

        val historicalEarlyBurns = previousSessionSamples
            .groupBy { it.sessionResetEpochMs }
            .values
            .map { session -> burnForPhase(session.sortedBy { it.elapsedMinutes }, BurnPhase.EARLY) }
            .filter { burn -> burn > 0.0 }

        val historicalAverage = historicalEarlyBurns.takeIf { it.isNotEmpty() }?.average() ?: return false
        return currentEarlyBurn > historicalAverage + 10.0
    }

    private fun burnForPhase(
        sessionSamples: List<SessionSample>,
        phase: BurnPhase,
    ): Double {
        val range = when (phase) {
            BurnPhase.EARLY -> 0L..60L
            BurnPhase.MID -> 61L..240L
            BurnPhase.LATE -> 241L..300L
            BurnPhase.UNKNOWN -> return 0.0
        }
        val phasePoints = sessionSamples.filter { it.elapsedMinutes in range }
        if (phasePoints.size < 2) return 0.0
        val min = phasePoints.minOf { it.utilization }
        val max = phasePoints.maxOf { it.utilization }
        return (max - min).coerceAtLeast(0.0)
    }

    private fun deriveState(
        currentUtilization: Double,
        timeToResetMinutes: Long?,
        paceTrack: PaceTrack,
        predictedTimeToCapMinutes: Long?,
        willHitCapBeforeReset: Boolean,
    ): SessionGuardrailState {
        val fromUtil = when {
            currentUtilization >= 90.0 -> SessionGuardrailState.CRITICAL
            currentUtilization >= 80.0 -> SessionGuardrailState.HIGH
            currentUtilization >= 65.0 -> SessionGuardrailState.WATCH
            currentUtilization >= 45.0 -> SessionGuardrailState.STEADY
            else -> SessionGuardrailState.SAFE
        }

        var state = fromUtil

        if (paceTrack == PaceTrack.ABOVE_USUAL && state.ordinal < SessionGuardrailState.WATCH.ordinal) {
            state = SessionGuardrailState.WATCH
        }

        if (predictedTimeToCapMinutes != null && timeToResetMinutes != null) {
            when {
                willHitCapBeforeReset -> state = maxState(state, SessionGuardrailState.CRITICAL)
                predictedTimeToCapMinutes <= timeToResetMinutes + 30L -> {
                    state = maxState(state, SessionGuardrailState.WATCH)
                }
            }
        }

        if (!willHitCapBeforeReset &&
            timeToResetMinutes != null &&
            timeToResetMinutes <= 20L &&
            state.ordinal > SessionGuardrailState.HIGH.ordinal
        ) {
            state = SessionGuardrailState.HIGH
        }

        return state
    }

    private fun maxState(a: SessionGuardrailState, b: SessionGuardrailState): SessionGuardrailState {
        return if (a.ordinal >= b.ordinal) a else b
    }

    private fun UsageHistoryPoint.toSessionSample(): SessionSample? {
        val utilizationValue = fiveHourUtilization ?: return null
        val resetAt = fiveHourResetsAt ?: return null
        val elapsedMinutes = Duration.between(resetAt.minus(sessionDuration), timestamp)
            .toMinutes()
            .coerceIn(0L, sessionMinutes)
        return SessionSample(
            timestamp = timestamp,
            utilization = utilizationValue.coerceIn(0.0, 100.0),
            elapsedMinutes = elapsedMinutes,
            sessionResetEpochMs = resetAt.toEpochMilli(),
        )
    }

    private fun UsageMetric.toCurrentSample(now: Instant): SessionSample? {
        val resetAt = resetsAt ?: return null
        val elapsedMinutes = Duration.between(resetAt.minus(sessionDuration), now)
            .toMinutes()
            .coerceIn(0L, sessionMinutes)
        return SessionSample(
            timestamp = now,
            utilization = utilization.coerceIn(0.0, 100.0),
            elapsedMinutes = elapsedMinutes,
            sessionResetEpochMs = resetAt.toEpochMilli(),
        )
    }

    private data class SessionSample(
        val timestamp: Instant,
        val utilization: Double,
        val elapsedMinutes: Long,
        val sessionResetEpochMs: Long,
    )
}
