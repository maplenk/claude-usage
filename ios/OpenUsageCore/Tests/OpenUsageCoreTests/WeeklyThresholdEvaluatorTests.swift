import XCTest
@testable import OpenUsageCore

/// The weekly ladder: 70 / 80 / 90 / 100, highest-crossed-only per refresh,
/// deduped per weekly window keyed on `resetsAt`.
final class WeeklyThresholdEvaluatorTests: XCTestCase {

    private let windowOne = Date(timeIntervalSince1970: 1_800_000_000)
    private var windowTwo: Date { windowOne.addingTimeInterval(7 * 24 * 3_600) }

    private func metric(_ utilization: Double, resetsAt: Date?) -> UsageMetric {
        UsageMetric(utilization: utilization, resetsAt: resetsAt)
    }

    func testThresholdsAreSeventyEightyNinetyHundred() async throws {
        XCTAssertEqual(WeeklyThresholdEvaluator.thresholds, [70, 80, 90, 100])
    }

    func testHighestReachedThreshold() async throws {
        XCTAssertNil(WeeklyThresholdEvaluator.highestReachedThreshold(currentUtilization: 69.9))
        XCTAssertEqual(WeeklyThresholdEvaluator.highestReachedThreshold(currentUtilization: 70.0), 70)
        XCTAssertEqual(WeeklyThresholdEvaluator.highestReachedThreshold(currentUtilization: 84.0), 80)
        XCTAssertEqual(WeeklyThresholdEvaluator.highestReachedThreshold(currentUtilization: 100.0), 100)
    }

    func testBelowFirstThresholdNeverNotifies() async throws {
        let decision = WeeklyThresholdEvaluator.evaluate(
            metric: metric(42, resetsAt: windowOne),
            previousState: .empty
        )
        XCTAssertNil(decision.thresholdToNotify)
        XCTAssertNil(decision.newState.lastNotifiedThreshold)
        XCTAssertEqual(decision.newState.windowResetsAt, windowOne)
    }

    func testFirstCrossingNotifiesHighestOnly() async throws {
        // 0% -> 85% in one refresh should fire once, at 80, not at 70 and 80.
        let decision = WeeklyThresholdEvaluator.evaluate(
            metric: metric(85, resetsAt: windowOne),
            previousState: .empty
        )
        XCTAssertEqual(decision.thresholdToNotify, 80)
        XCTAssertEqual(decision.currentPercent, 85)
        XCTAssertEqual(decision.newState.lastNotifiedThreshold, 80)
    }

    func testSecondRefreshInSameBandIsDeduped() async throws {
        var state = WeeklyThresholdEvaluator.evaluate(
            metric: metric(72, resetsAt: windowOne),
            previousState: .empty
        ).newState
        XCTAssertEqual(state.lastNotifiedThreshold, 70)

        let repeated = WeeklyThresholdEvaluator.evaluate(
            metric: metric(78, resetsAt: windowOne),
            previousState: state
        )
        XCTAssertNil(repeated.thresholdToNotify)
        state = repeated.newState
        XCTAssertEqual(state.lastNotifiedThreshold, 70)
    }

    func testClimbingToNextBandNotifiesAgain() async throws {
        let first = WeeklyThresholdEvaluator.evaluate(
            metric: metric(72, resetsAt: windowOne),
            previousState: .empty
        )
        let second = WeeklyThresholdEvaluator.evaluate(
            metric: metric(91, resetsAt: windowOne),
            previousState: first.newState
        )
        XCTAssertEqual(second.thresholdToNotify, 90)
        XCTAssertEqual(second.newState.lastNotifiedThreshold, 90)
    }

    func testHundredPercentFiresOnce() async throws {
        let ninety = WeeklyThresholdEvaluator.evaluate(
            metric: metric(93, resetsAt: windowOne),
            previousState: .empty
        )
        let hundred = WeeklyThresholdEvaluator.evaluate(
            metric: metric(100, resetsAt: windowOne),
            previousState: ninety.newState
        )
        XCTAssertEqual(hundred.thresholdToNotify, 100)

        let again = WeeklyThresholdEvaluator.evaluate(
            metric: metric(100, resetsAt: windowOne),
            previousState: hundred.newState
        )
        XCTAssertNil(again.thresholdToNotify)
    }

    func testLaterResetsAtRollsOverTheWindowAndClearsState() async throws {
        let saturated = WeeklyThresholdEvaluator.evaluate(
            metric: metric(96, resetsAt: windowOne),
            previousState: .empty
        )
        XCTAssertEqual(saturated.newState.lastNotifiedThreshold, 90)

        let rolled = WeeklyThresholdEvaluator.evaluate(
            metric: metric(4, resetsAt: windowTwo),
            previousState: saturated.newState
        )
        XCTAssertTrue(rolled.didRollOver)
        XCTAssertNil(rolled.thresholdToNotify)
        XCTAssertNil(rolled.newState.lastNotifiedThreshold)
        XCTAssertEqual(rolled.newState.windowResetsAt, windowTwo)

        // The ladder starts again in the new window.
        let climbAgain = WeeklyThresholdEvaluator.evaluate(
            metric: metric(71, resetsAt: windowTwo),
            previousState: rolled.newState
        )
        XCTAssertEqual(climbAgain.thresholdToNotify, 70)
    }

    func testRolloverStraightIntoAHighBandNotifiesImmediately() async throws {
        let saturated = WeeklyThresholdEvaluator.evaluate(
            metric: metric(96, resetsAt: windowOne),
            previousState: .empty
        )
        let rolled = WeeklyThresholdEvaluator.evaluate(
            metric: metric(75, resetsAt: windowTwo),
            previousState: saturated.newState
        )
        XCTAssertTrue(rolled.didRollOver)
        XCTAssertEqual(rolled.thresholdToNotify, 70)
    }

    func testIdenticalResetsAtDoesNotRollOver() async throws {
        let first = WeeklyThresholdEvaluator.evaluate(
            metric: metric(82, resetsAt: windowOne),
            previousState: .empty
        )
        let second = WeeklyThresholdEvaluator.evaluate(
            metric: metric(83, resetsAt: windowOne),
            previousState: first.newState
        )
        XCTAssertFalse(second.didRollOver)
        XCTAssertNil(second.thresholdToNotify)
    }

    func testEarlierResetsAtIsIgnoredAsAWindowChange() async throws {
        let first = WeeklyThresholdEvaluator.evaluate(
            metric: metric(82, resetsAt: windowTwo),
            previousState: .empty
        )
        let stale = WeeklyThresholdEvaluator.evaluate(
            metric: metric(84, resetsAt: windowOne),
            previousState: first.newState
        )
        XCTAssertFalse(stale.didRollOver)
        XCTAssertNil(stale.thresholdToNotify)
        XCTAssertEqual(stale.newState.windowResetsAt, windowTwo)
    }

    func testDropBelowFirstThresholdClearsLadderWithoutResetTimeChange() async throws {
        let high = WeeklyThresholdEvaluator.evaluate(
            metric: metric(95, resetsAt: windowOne),
            previousState: .empty
        )
        let dropped = WeeklyThresholdEvaluator.evaluate(
            metric: metric(3, resetsAt: windowOne),
            previousState: high.newState
        )
        XCTAssertNil(dropped.newState.lastNotifiedThreshold)

        let climb = WeeklyThresholdEvaluator.evaluate(
            metric: metric(70, resetsAt: windowOne),
            previousState: dropped.newState
        )
        XCTAssertEqual(climb.thresholdToNotify, 70)
    }

    func testNilMetricLeavesStateUntouched() async throws {
        let previous = WeeklyThresholdState(windowResetsAt: windowOne, lastNotifiedThreshold: 80)
        let decision = WeeklyThresholdEvaluator.evaluate(metric: nil, previousState: previous)
        XCTAssertNil(decision.thresholdToNotify)
        XCTAssertEqual(decision.newState, previous)
    }

    func testMetricWithoutResetTimeStillLadders() async throws {
        let decision = WeeklyThresholdEvaluator.evaluate(
            metric: metric(91, resetsAt: nil),
            previousState: .empty
        )
        XCTAssertEqual(decision.thresholdToNotify, 90)
        XCTAssertNil(decision.newState.windowResetsAt)
    }
}
