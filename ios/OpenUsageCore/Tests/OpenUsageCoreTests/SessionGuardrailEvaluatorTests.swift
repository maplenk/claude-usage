import XCTest
@testable import OpenUsageCore

/// Behavioural coverage for the port of `SessionGuardrailEvaluator.kt`.
/// Android ships no unit tests for this object, so these encode the Kotlin
/// implementation's documented behaviour directly.
final class SessionGuardrailEvaluatorTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func point(
        minutesAgo: Int,
        utilization: Double,
        resetsAt: Date
    ) -> UsageHistoryPoint {
        UsageHistoryPoint(
            timestamp: now.addingTimeInterval(TimeInterval(-minutesAgo * 60)),
            fiveHourUtilization: utilization,
            fiveHourResetsAt: resetsAt,
            sevenDayUtilization: nil,
            sevenDayOpusUtilization: nil,
            sevenDaySonnetUtilization: nil
        )
    }

    /// Builds a history point in a *previous* session at a chosen elapsed offset.
    private func previousSessionPoint(
        sessionOffsetHours: Int,
        elapsedMinutes: Int,
        utilization: Double
    ) -> UsageHistoryPoint {
        let reset = now.addingTimeInterval(TimeInterval(-sessionOffsetHours * 3_600))
        let start = reset.addingTimeInterval(-SessionGuardrailEvaluator.sessionDurationSeconds)
        return UsageHistoryPoint(
            timestamp: start.addingTimeInterval(TimeInterval(elapsedMinutes * 60)),
            fiveHourUtilization: utilization,
            fiveHourResetsAt: reset,
            sevenDayUtilization: nil,
            sevenDayOpusUtilization: nil,
            sevenDaySonnetUtilization: nil
        )
    }

    func testNilMetricProducesEmptyInsights() async throws {
        let insights = SessionGuardrailEvaluator.evaluate(currentMetric: nil, history: [], now: now)
        XCTAssertEqual(insights.state, .safe)
        XCTAssertNil(insights.currentUtilization)
        XCTAssertEqual(insights.paceTrack, .unknown)
        XCTAssertEqual(insights.typicalBurnPhase, .unknown)
        XCTAssertFalse(insights.willHitCapBeforeReset)
    }

    func testStateBandsFollowUtilization() async throws {
        let cases: [(Double, SessionGuardrailState)] = [
            (10, .safe),
            (44.9, .safe),
            (45, .steady),
            (64.9, .steady),
            (65, .watch),
            (79.9, .watch),
            (80, .high),
            (89.9, .high),
            (90, .critical),
        ]
        for (utilization, expected) in cases {
            let metric = UsageMetric(
                utilization: utilization,
                resetsAt: now.addingTimeInterval(3 * 3_600)
            )
            let insights = SessionGuardrailEvaluator.evaluate(
                currentMetric: metric,
                history: [],
                now: now
            )
            XCTAssertEqual(insights.state, expected, "utilization \(utilization)")
        }
    }

    func testTimeToResetIsMinutesFlooredAtZero() async throws {
        let metric = UsageMetric(utilization: 20, resetsAt: now.addingTimeInterval(-600))
        let insights = SessionGuardrailEvaluator.evaluate(currentMetric: metric, history: [], now: now)
        XCTAssertEqual(insights.timeToResetMinutes, 0)
    }

    func testAboveUsualPaceUpgradesStateToWatch() async throws {
        // Six previous-session samples at the same elapsed point, all low.
        var history: [UsageHistoryPoint] = []
        for index in 0..<6 {
            history.append(
                previousSessionPoint(
                    sessionOffsetHours: 6 + index * 6,
                    elapsedMinutes: 150,
                    utilization: 10
                )
            )
        }
        // Current session is 150 minutes in (reset is 150 minutes away).
        let metric = UsageMetric(utilization: 40, resetsAt: now.addingTimeInterval(150 * 60))

        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        XCTAssertEqual(insights.baselineAtThisPoint, 10.0)
        XCTAssertEqual(insights.paceTrack, .aboveUsual)
        XCTAssertEqual(insights.state, .watch)
    }

    func testBelowUsualPaceIsDetected() async throws {
        var history: [UsageHistoryPoint] = []
        for index in 0..<6 {
            history.append(
                previousSessionPoint(
                    sessionOffsetHours: 6 + index * 6,
                    elapsedMinutes: 150,
                    utilization: 60
                )
            )
        }
        let metric = UsageMetric(utilization: 40, resetsAt: now.addingTimeInterval(150 * 60))
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        XCTAssertEqual(insights.paceTrack, .belowUsual)
    }

    func testFewerThanSixBaselineSamplesLeavesPaceUnknown() async throws {
        var history: [UsageHistoryPoint] = []
        for index in 0..<5 {
            history.append(
                previousSessionPoint(
                    sessionOffsetHours: 6 + index * 6,
                    elapsedMinutes: 150,
                    utilization: 10
                )
            )
        }
        let metric = UsageMetric(utilization: 40, resetsAt: now.addingTimeInterval(150 * 60))
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        XCTAssertNil(insights.baselineAtThisPoint)
        XCTAssertEqual(insights.paceTrack, .unknown)
        XCTAssertEqual(insights.state, .safe)
    }

    func testSteepCurrentSlopePredictsCapBeforeReset() async throws {
        let resetsAt = now.addingTimeInterval(60 * 60) // 60 minutes away, elapsed = 240
        let history = [
            point(minutesAgo: 60, utilization: 10, resetsAt: resetsAt),
            point(minutesAgo: 30, utilization: 30, resetsAt: resetsAt),
        ]
        let metric = UsageMetric(utilization: 60, resetsAt: resetsAt)

        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        // slope = (60 - 10) / 60 = 0.8333 %/min -> (100-60)/0.8333 = 48 minutes.
        XCTAssertEqual(insights.predictedTimeToCapMinutes, 48)
        XCTAssertEqual(insights.timeToResetMinutes, 60)
        XCTAssertTrue(insights.willHitCapBeforeReset)
        XCTAssertEqual(insights.state, .critical)
    }

    func testShallowSlopeDoesNotPredictACap() async throws {
        let resetsAt = now.addingTimeInterval(60 * 60)
        let history = [
            point(minutesAgo: 60, utilization: 20.0, resetsAt: resetsAt),
            point(minutesAgo: 30, utilization: 20.5, resetsAt: resetsAt),
        ]
        let metric = UsageMetric(utilization: 21.0, resetsAt: resetsAt)
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        // slope = 1.0 / 60 = 0.0167 which is below the 0.03 floor.
        XCTAssertNil(insights.predictedTimeToCapMinutes)
        XCTAssertFalse(insights.willHitCapBeforeReset)
    }

    func testAtHundredPercentCapTimeIsZero() async throws {
        let metric = UsageMetric(utilization: 100, resetsAt: now.addingTimeInterval(45 * 60))
        let insights = SessionGuardrailEvaluator.evaluate(currentMetric: metric, history: [], now: now)
        XCTAssertEqual(insights.predictedTimeToCapMinutes, 0)
        XCTAssertTrue(insights.willHitCapBeforeReset)
        XCTAssertEqual(insights.state, .critical)
    }

    func testCriticalIsDowngradedToHighWhenResetIsImminentAndNoCapRisk() async throws {
        let metric = UsageMetric(utilization: 95, resetsAt: now.addingTimeInterval(10 * 60))
        let insights = SessionGuardrailEvaluator.evaluate(currentMetric: metric, history: [], now: now)
        XCTAssertFalse(insights.willHitCapBeforeReset)
        XCTAssertEqual(insights.state, .high)
    }

    func testResetReliefSoonRequiresHighUsageAndNearReset() async throws {
        let relief = SessionGuardrailEvaluator.evaluate(
            currentMetric: UsageMetric(utilization: 80, resetsAt: now.addingTimeInterval(30 * 60)),
            history: [],
            now: now
        )
        XCTAssertTrue(relief.resetReliefSoon)

        let tooEarly = SessionGuardrailEvaluator.evaluate(
            currentMetric: UsageMetric(utilization: 80, resetsAt: now.addingTimeInterval(90 * 60)),
            history: [],
            now: now
        )
        XCTAssertFalse(tooEarly.resetReliefSoon)

        let tooLow = SessionGuardrailEvaluator.evaluate(
            currentMetric: UsageMetric(utilization: 60, resetsAt: now.addingTimeInterval(30 * 60)),
            history: [],
            now: now
        )
        XCTAssertFalse(tooLow.resetReliefSoon)
    }

    func testHistoryPointsWithoutResetTimeAreIgnored() async throws {
        let orphan = UsageHistoryPoint(
            timestamp: now.addingTimeInterval(-1_800),
            fiveHourUtilization: 50,
            fiveHourResetsAt: nil,
            sevenDayUtilization: nil,
            sevenDayOpusUtilization: nil,
            sevenDaySonnetUtilization: nil
        )
        let metric = UsageMetric(utilization: 30, resetsAt: now.addingTimeInterval(3_600))
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: [orphan],
            now: now
        )
        XCTAssertNil(insights.predictedTimeToCapMinutes)
        XCTAssertEqual(insights.typicalBurnPhase, .unknown)
    }

    func testDominantBurnPhaseFavoursTheHeaviestWindow() async throws {
        // One previous session that burns hard late and barely at all early.
        let reset = now.addingTimeInterval(-6 * 3_600)
        let start = reset.addingTimeInterval(-SessionGuardrailEvaluator.sessionDurationSeconds)
        func historic(_ elapsed: Int, _ utilization: Double) -> UsageHistoryPoint {
            UsageHistoryPoint(
                timestamp: start.addingTimeInterval(TimeInterval(elapsed * 60)),
                fiveHourUtilization: utilization,
                fiveHourResetsAt: reset,
                sevenDayUtilization: nil,
                sevenDayOpusUtilization: nil,
                sevenDaySonnetUtilization: nil
            )
        }
        let history = [
            historic(10, 1), historic(50, 2),      // early burn 1
            historic(100, 3), historic(200, 6),    // mid burn 3
            historic(250, 10), historic(290, 90),  // late burn 80
        ]
        let metric = UsageMetric(utilization: 20, resetsAt: now.addingTimeInterval(3_600))
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: metric,
            history: history,
            now: now
        )
        XCTAssertEqual(insights.typicalBurnPhase, .late)
    }

    func testUtilizationIsClampedIntoRange() async throws {
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: UsageMetric(utilization: 140, resetsAt: now.addingTimeInterval(3_600)),
            history: [],
            now: now
        )
        XCTAssertEqual(insights.currentUtilization, 100.0)
    }

    func testMetricWithoutResetTimeYieldsNoResetOrPacing() async throws {
        let insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: UsageMetric(utilization: 70, resetsAt: nil),
            history: [],
            now: now
        )
        XCTAssertNil(insights.timeToResetMinutes)
        XCTAssertEqual(insights.paceTrack, .unknown)
        XCTAssertEqual(insights.state, .watch)
    }
}
