import Foundation

/// Faithful port of `domain/guardrail/SessionGuardrailEvaluator.kt`.
/// Ordinal-sensitive comparisons in the Kotlin original are preserved by giving
/// the states explicit `rank` values.

public enum SessionGuardrailState: String, Sendable, CaseIterable {
    case safe
    case steady
    case watch
    case high
    case critical

    /// Equivalent to Kotlin's `enum.ordinal`.
    public var rank: Int {
        switch self {
        case .safe: return 0
        case .steady: return 1
        case .watch: return 2
        case .high: return 3
        case .critical: return 4
        }
    }
}

public enum PaceTrack: String, Sendable {
    case aboveUsual
    case onTrack
    case belowUsual
    case unknown
}

public enum BurnPhase: String, Sendable {
    case early
    case mid
    case late
    case unknown
}

public struct SessionGuardrailInsights: Equatable, Sendable {
    public let state: SessionGuardrailState
    public let currentUtilization: Double?
    public let timeToResetMinutes: Int?
    public let paceTrack: PaceTrack
    public let baselineAtThisPoint: Double?
    public let predictedTimeToCapMinutes: Int?
    public let willHitCapBeforeReset: Bool
    public let typicalBurnPhase: BurnPhase
    public let earlyHeavyUsageDetected: Bool
    public let resetReliefSoon: Bool

    public init(
        state: SessionGuardrailState,
        currentUtilization: Double?,
        timeToResetMinutes: Int?,
        paceTrack: PaceTrack,
        baselineAtThisPoint: Double?,
        predictedTimeToCapMinutes: Int?,
        willHitCapBeforeReset: Bool,
        typicalBurnPhase: BurnPhase,
        earlyHeavyUsageDetected: Bool,
        resetReliefSoon: Bool
    ) {
        self.state = state
        self.currentUtilization = currentUtilization
        self.timeToResetMinutes = timeToResetMinutes
        self.paceTrack = paceTrack
        self.baselineAtThisPoint = baselineAtThisPoint
        self.predictedTimeToCapMinutes = predictedTimeToCapMinutes
        self.willHitCapBeforeReset = willHitCapBeforeReset
        self.typicalBurnPhase = typicalBurnPhase
        self.earlyHeavyUsageDetected = earlyHeavyUsageDetected
        self.resetReliefSoon = resetReliefSoon
    }

    public static func empty() -> SessionGuardrailInsights {
        SessionGuardrailInsights(
            state: .safe,
            currentUtilization: nil,
            timeToResetMinutes: nil,
            paceTrack: .unknown,
            baselineAtThisPoint: nil,
            predictedTimeToCapMinutes: nil,
            willHitCapBeforeReset: false,
            typicalBurnPhase: .unknown,
            earlyHeavyUsageDetected: false,
            resetReliefSoon: false
        )
    }
}

public enum SessionGuardrailEvaluator {
    static let sessionDurationSeconds: TimeInterval = 5 * 60 * 60
    static let sessionMinutes = 300
    private static let paceBaselineWindowMinutes = 12
    private static let minBaselineSamples = 6
    private static let minSlopeWindowMinutes = 10

    struct SessionSample {
        let timestamp: Date
        let utilization: Double
        let elapsedMinutes: Int
        /// Kotlin keys sessions on `resetsAt.toEpochMilli()`.
        let sessionResetEpochMs: Int64
    }

    public static func evaluate(
        currentMetric: UsageMetric?,
        history: [UsageHistoryPoint],
        now: Date = Date()
    ) -> SessionGuardrailInsights {
        guard let currentMetric else { return .empty() }

        let currentUtilization = clamp(currentMetric.utilization, 0.0, 100.0)
        let resetsAt = currentMetric.resetsAt
        let timeToResetMinutes = resetsAt.map { max(minutesBetween(now, $0), 0) }
        let currentElapsedMinutes: Int? = resetsAt.map { reset in
            let start = reset.addingTimeInterval(-sessionDurationSeconds)
            return clampInt(minutesBetween(start, now), 0, sessionMinutes)
        }

        let allSamples = history.compactMap(sessionSample(from:))
        let currentSessionEpoch = resetsAt.map(epochMillis)

        var currentSessionSamples = allSamples.filter { $0.sessionResetEpochMs == currentSessionEpoch }
        if let sample = currentSample(from: currentMetric, now: now) {
            currentSessionSamples.append(sample)
        }
        currentSessionSamples.sort { $0.timestamp < $1.timestamp }

        let previousSessionSamples = allSamples.filter { $0.sessionResetEpochMs != currentSessionEpoch }

        let baselineAtThisPoint = currentElapsedMinutes.flatMap {
            baselineForElapsed(previousSessionSamples, elapsedMinutes: $0)
        }

        let paceTrack: PaceTrack
        if let baselineAtThisPoint {
            if currentUtilization - baselineAtThisPoint >= 8.0 {
                paceTrack = .aboveUsual
            } else if currentUtilization - baselineAtThisPoint <= -8.0 {
                paceTrack = .belowUsual
            } else {
                paceTrack = .onTrack
            }
        } else {
            paceTrack = .unknown
        }

        let currentSlope = slopeFromRecentWindow(
            samples: currentSessionSamples,
            referenceElapsedMinutes: currentElapsedMinutes
        )
        let historicalSlope = averageHistoricalSessionSlope(previousSessionSamples)
        let blendedSlope: Double?
        if let currentSlope, let historicalSlope {
            blendedSlope = (currentSlope * 0.7) + (historicalSlope * 0.3)
        } else if let currentSlope {
            blendedSlope = currentSlope
        } else {
            blendedSlope = historicalSlope
        }

        let predictedTimeToCapMinutes: Int?
        if currentUtilization >= 100.0 {
            predictedTimeToCapMinutes = 0
        } else if let blendedSlope, blendedSlope > 0.03 {
            predictedTimeToCapMinutes = max(Int(((100.0 - currentUtilization) / blendedSlope).rounded()), 0)
        } else {
            predictedTimeToCapMinutes = nil
        }

        var willHitCapBeforeReset = false
        if let predictedTimeToCapMinutes, let timeToResetMinutes {
            willHitCapBeforeReset = predictedTimeToCapMinutes < timeToResetMinutes
        }

        let typicalBurnPhase = dominantBurnPhase(previousSessionSamples)

        let earlyHeavyUsageDetected = detectEarlyHeavyUsage(
            currentSessionSamples: currentSessionSamples,
            previousSessionSamples: previousSessionSamples,
            currentElapsedMinutes: currentElapsedMinutes,
            typicalBurnPhase: typicalBurnPhase
        )

        let resetReliefSoon = currentUtilization >= 75.0 &&
            (timeToResetMinutes != nil && timeToResetMinutes! <= 45)

        let state = deriveState(
            currentUtilization: currentUtilization,
            timeToResetMinutes: timeToResetMinutes,
            paceTrack: paceTrack,
            predictedTimeToCapMinutes: predictedTimeToCapMinutes,
            willHitCapBeforeReset: willHitCapBeforeReset
        )

        return SessionGuardrailInsights(
            state: state,
            currentUtilization: currentUtilization,
            timeToResetMinutes: timeToResetMinutes,
            paceTrack: paceTrack,
            baselineAtThisPoint: baselineAtThisPoint,
            predictedTimeToCapMinutes: predictedTimeToCapMinutes,
            willHitCapBeforeReset: willHitCapBeforeReset,
            typicalBurnPhase: typicalBurnPhase,
            earlyHeavyUsageDetected: earlyHeavyUsageDetected,
            resetReliefSoon: resetReliefSoon
        )
    }

    // MARK: - Private helpers

    private static func baselineForElapsed(_ samples: [SessionSample], elapsedMinutes: Int) -> Double? {
        let matches = samples.filter { abs($0.elapsedMinutes - elapsedMinutes) <= paceBaselineWindowMinutes }
        guard matches.count >= minBaselineSamples else { return nil }
        return matches.map(\.utilization).reduce(0, +) / Double(matches.count)
    }

    private static func slopeFromRecentWindow(
        samples: [SessionSample],
        referenceElapsedMinutes: Int?
    ) -> Double? {
        guard samples.count >= 2, let referenceElapsedMinutes else { return nil }

        let recent = samples
            .filter { (0...90).contains(referenceElapsedMinutes - $0.elapsedMinutes) }
            .sorted { $0.elapsedMinutes < $1.elapsedMinutes }

        guard recent.count >= 2, let first = recent.first, let last = recent.last else { return nil }
        let deltaMinutes = max(last.elapsedMinutes - first.elapsedMinutes, 0)
        guard deltaMinutes >= minSlopeWindowMinutes else { return nil }
        return (last.utilization - first.utilization) / Double(deltaMinutes)
    }

    private static func averageHistoricalSessionSlope(_ samples: [SessionSample]) -> Double? {
        let slopes = groupedBySession(samples).compactMap { sessionSamples -> Double? in
            let ordered = sessionSamples.sorted { $0.elapsedMinutes < $1.elapsedMinutes }
            guard ordered.count >= 2, let first = ordered.first, let last = ordered.last else { return nil }
            let deltaMinutes = max(last.elapsedMinutes - first.elapsedMinutes, 0)
            guard deltaMinutes >= 20 else { return nil }
            return (last.utilization - first.utilization) / Double(deltaMinutes)
        }
        guard !slopes.isEmpty else { return nil }
        return slopes.reduce(0, +) / Double(slopes.count)
    }

    /// Kotlin builds a `LinkedHashMap` keyed EARLY, MID, LATE and takes
    /// `maxByOrNull`, which returns the *first* maximum. An empty sample set
    /// yields an empty map and therefore `UNKNOWN`.
    private static func dominantBurnPhase(_ samples: [SessionSample]) -> BurnPhase {
        let sessions = groupedBySession(samples)
        guard !sessions.isEmpty else { return .unknown }

        let phases: [BurnPhase] = [.early, .mid, .late]
        var averages: [(BurnPhase, Double)] = []
        for phase in phases {
            let burns = sessions.map { session in
                burnForPhase(session.sorted { $0.elapsedMinutes < $1.elapsedMinutes }, phase: phase)
            }
            let average = burns.isEmpty ? 0.0 : burns.reduce(0, +) / Double(burns.count)
            averages.append((phase, average))
        }

        var best = averages[0]
        for candidate in averages.dropFirst() where candidate.1 > best.1 {
            best = candidate
        }
        return best.0
    }

    private static func detectEarlyHeavyUsage(
        currentSessionSamples: [SessionSample],
        previousSessionSamples: [SessionSample],
        currentElapsedMinutes: Int?,
        typicalBurnPhase: BurnPhase
    ) -> Bool {
        guard let currentElapsedMinutes, currentElapsedMinutes <= 120 else { return false }
        if typicalBurnPhase == .early || typicalBurnPhase == .unknown { return false }

        let currentEarlyBurn = burnForPhase(currentSessionSamples, phase: .early)
        guard currentEarlyBurn > 0.0 else { return false }

        let historicalEarlyBurns = groupedBySession(previousSessionSamples)
            .map { burnForPhase($0.sorted { $0.elapsedMinutes < $1.elapsedMinutes }, phase: .early) }
            .filter { $0 > 0.0 }

        guard !historicalEarlyBurns.isEmpty else { return false }
        let historicalAverage = historicalEarlyBurns.reduce(0, +) / Double(historicalEarlyBurns.count)
        return currentEarlyBurn > historicalAverage + 10.0
    }

    private static func burnForPhase(_ sessionSamples: [SessionSample], phase: BurnPhase) -> Double {
        let range: ClosedRange<Int>
        switch phase {
        case .early: range = 0...60
        case .mid: range = 61...240
        case .late: range = 241...300
        case .unknown: return 0.0
        }
        let phasePoints = sessionSamples.filter { range.contains($0.elapsedMinutes) }
        guard phasePoints.count >= 2 else { return 0.0 }
        let minimum = phasePoints.map(\.utilization).min() ?? 0.0
        let maximum = phasePoints.map(\.utilization).max() ?? 0.0
        return max(maximum - minimum, 0.0)
    }

    private static func deriveState(
        currentUtilization: Double,
        timeToResetMinutes: Int?,
        paceTrack: PaceTrack,
        predictedTimeToCapMinutes: Int?,
        willHitCapBeforeReset: Bool
    ) -> SessionGuardrailState {
        var state: SessionGuardrailState
        if currentUtilization >= 90.0 {
            state = .critical
        } else if currentUtilization >= 80.0 {
            state = .high
        } else if currentUtilization >= 65.0 {
            state = .watch
        } else if currentUtilization >= 45.0 {
            state = .steady
        } else {
            state = .safe
        }

        if paceTrack == .aboveUsual, state.rank < SessionGuardrailState.watch.rank {
            state = .watch
        }

        if let predictedTimeToCapMinutes, let timeToResetMinutes {
            if willHitCapBeforeReset {
                state = maxState(state, .critical)
            } else if predictedTimeToCapMinutes <= timeToResetMinutes + 30 {
                state = maxState(state, .watch)
            }
        }

        if !willHitCapBeforeReset,
           let timeToResetMinutes,
           timeToResetMinutes <= 20,
           state.rank > SessionGuardrailState.high.rank {
            state = .high
        }

        return state
    }

    private static func maxState(_ a: SessionGuardrailState, _ b: SessionGuardrailState) -> SessionGuardrailState {
        a.rank >= b.rank ? a : b
    }

    private static func groupedBySession(_ samples: [SessionSample]) -> [[SessionSample]] {
        var order: [Int64] = []
        var buckets: [Int64: [SessionSample]] = [:]
        for sample in samples {
            if buckets[sample.sessionResetEpochMs] == nil {
                order.append(sample.sessionResetEpochMs)
                buckets[sample.sessionResetEpochMs] = []
            }
            buckets[sample.sessionResetEpochMs]?.append(sample)
        }
        return order.compactMap { buckets[$0] }
    }

    static func sessionSample(from point: UsageHistoryPoint) -> SessionSample? {
        guard let utilizationValue = point.fiveHourUtilization,
              let resetAt = point.fiveHourResetsAt else { return nil }
        let start = resetAt.addingTimeInterval(-sessionDurationSeconds)
        let elapsedMinutes = clampInt(minutesBetween(start, point.timestamp), 0, sessionMinutes)
        return SessionSample(
            timestamp: point.timestamp,
            utilization: clamp(utilizationValue, 0.0, 100.0),
            elapsedMinutes: elapsedMinutes,
            sessionResetEpochMs: epochMillis(resetAt)
        )
    }

    private static func currentSample(from metric: UsageMetric, now: Date) -> SessionSample? {
        guard let resetAt = metric.resetsAt else { return nil }
        let start = resetAt.addingTimeInterval(-sessionDurationSeconds)
        let elapsedMinutes = clampInt(minutesBetween(start, now), 0, sessionMinutes)
        return SessionSample(
            timestamp: now,
            utilization: clamp(metric.utilization, 0.0, 100.0),
            elapsedMinutes: elapsedMinutes,
            sessionResetEpochMs: epochMillis(resetAt)
        )
    }
}

// MARK: - Shared numeric helpers

/// `java.time.Duration.between(a, b).toMinutes()` truncates toward zero.
func minutesBetween(_ from: Date, _ to: Date) -> Int {
    Int(to.timeIntervalSince(from) / 60.0)
}

func epochMillis(_ date: Date) -> Int64 {
    Int64((date.timeIntervalSince1970 * 1000.0).rounded())
}

func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
    min(max(value, low), high)
}

func clampInt(_ value: Int, _ low: Int, _ high: Int) -> Int {
    min(max(value, low), high)
}
