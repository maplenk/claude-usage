import XCTest
@testable import OpenUsageCore

final class NotificationPlannerTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func claude(
        session: Double?,
        sessionResetsAt: Date? = nil,
        weekly: Double? = nil,
        opus: Double? = nil,
        sonnet: Double? = nil,
        weeklyResetsAt: Date? = nil
    ) -> ClaudeUsage {
        ClaudeUsage(
            fiveHour: session.map { UsageMetric(utilization: $0, resetsAt: sessionResetsAt) },
            sevenDay: weekly.map { UsageMetric(utilization: $0, resetsAt: weeklyResetsAt) },
            sevenDayOpus: opus.map { UsageMetric(utilization: $0, resetsAt: weeklyResetsAt) },
            sevenDaySonnet: sonnet.map { UsageMetric(utilization: $0, resetsAt: weeklyResetsAt) },
            fetchedAt: now
        )
    }

    private func kinds(_ plan: NotificationPlan) -> [UsageNotificationRequest.Kind] {
        plan.requests.map(\.kind)
    }

    // MARK: - Session reset

    func testSessionResetFiresWhenUsageDropsToZero() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: 64,
                usage: claude(session: 0),
                now: now
            )
        )
        XCTAssertTrue(kinds(plan).contains(.sessionReset))
    }

    func testSessionResetIsSuppressedWhenToggleOff() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: 64,
                usage: claude(session: 0),
                settings: NotificationSettings(notifyOnSessionReset: false, notifyOnUsageThresholds: true),
                now: now
            )
        )
        XCTAssertFalse(kinds(plan).contains(.sessionReset))
    }

    func testSessionResetDoesNotFireWhenPreviousWasAlreadyZero() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(previousSessionUtilization: 0, usage: claude(session: 0), now: now)
        )
        XCTAssertFalse(kinds(plan).contains(.sessionReset))
    }

    // MARK: - Session milestones

    func testSessionMilestoneUsesCurrentPercentInBody() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: 70,
                usage: claude(session: 88),
                now: now
            )
        )
        let milestone = plan.requests.first { request in
            if case .sessionMilestone = request.kind { return true }
            return false
        }
        XCTAssertNotNil(milestone)
        XCTAssertEqual(milestone?.body, "Limit used 88%")
        XCTAssertEqual(milestone?.title, "Session usage alert")
        XCTAssertEqual(plan.state.lastNotifiedSessionThreshold, 85)
    }

    func testSessionMilestoneOnlyFiresOncePerBand() async throws {
        let first = NotificationPlanner.plan(
            NotificationPlanInput(previousSessionUtilization: 70, usage: claude(session: 88), now: now)
        )
        let second = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: 88,
                usage: claude(session: 89),
                state: first.state,
                now: now
            )
        )
        XCTAssertTrue(kinds(second).allSatisfy { kind in
            if case .sessionMilestone = kind { return false }
            return true
        })
    }

    func testSessionLadderResetsBelowFirstThreshold() async throws {
        var state = NotificationState.empty
        state.lastNotifiedSessionThreshold = 90
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(previousSessionUtilization: 90, usage: claude(session: 5), state: state, now: now)
        )
        XCTAssertNil(plan.state.lastNotifiedSessionThreshold)
    }

    func testBaselineAdvancesEvenWhenNotificationsDisabled() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: 10,
                usage: claude(session: 92),
                settings: NotificationSettings(notifyOnSessionReset: true, notifyOnUsageThresholds: false),
                now: now
            )
        )
        XCTAssertTrue(plan.requests.isEmpty)
        XCTAssertEqual(plan.state.lastNotifiedSessionThreshold, 90)
    }

    // MARK: - Weekly milestones

    func testWeeklyMilestonesCoverFourLimitsButNotSonnet() async throws {
        let reset = now.addingTimeInterval(3 * 86_400)
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(
                    session: 10,
                    weekly: 82,
                    opus: 91,
                    sonnet: 99,
                    weeklyResetsAt: reset
                ),
                codexUsage: CodexUsage(weekly: UsageMetric(utilization: 71, resetsAt: reset), fetchedAt: now),
                grokUsage: GrokUsage(weekly: UsageMetric(utilization: 100, resetsAt: reset), fetchedAt: now),
                now: now
            )
        )

        var fired: [WeeklyLimitKey: Int] = [:]
        for request in plan.requests {
            if case .weeklyMilestone(let limit, let threshold, _) = request.kind {
                fired[limit] = threshold
            }
        }
        XCTAssertEqual(fired[.claudeWeekly], 80)
        XCTAssertEqual(fired[.claudeWeeklyOpus], 90)
        XCTAssertEqual(fired[.codexWeekly], 70)
        XCTAssertEqual(fired[.grokWeekly], 100)
        XCTAssertEqual(fired.count, 4, "Sonnet must not produce a weekly alert")
    }

    func testWeeklyMilestoneBodyNamesTheLimit() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                codexUsage: CodexUsage(weekly: UsageMetric(utilization: 84, resetsAt: nil), fetchedAt: now),
                now: now
            )
        )
        let request = plan.requests.first
        XCTAssertEqual(request?.title, "Codex weekly usage alert")
        XCTAssertEqual(request?.body, "Codex weekly limit used 84%")
        XCTAssertEqual(request?.category, .weeklyMilestone)
    }

    func testWeeklyMilestoneDedupesAcrossRefreshesAndRollsOver() async throws {
        let windowOne = now.addingTimeInterval(2 * 86_400)
        let windowTwo = windowOne.addingTimeInterval(7 * 86_400)

        let first = NotificationPlanner.plan(
            NotificationPlanInput(
                codexUsage: CodexUsage(weekly: UsageMetric(utilization: 92, resetsAt: windowOne), fetchedAt: now),
                now: now
            )
        )
        XCTAssertEqual(first.requests.count, 1)

        let second = NotificationPlanner.plan(
            NotificationPlanInput(
                codexUsage: CodexUsage(weekly: UsageMetric(utilization: 95, resetsAt: windowOne), fetchedAt: now),
                state: first.state,
                now: now
            )
        )
        XCTAssertTrue(second.requests.isEmpty)

        let rolled = NotificationPlanner.plan(
            NotificationPlanInput(
                codexUsage: CodexUsage(weekly: UsageMetric(utilization: 73, resetsAt: windowTwo), fetchedAt: now),
                state: second.state,
                now: now
            )
        )
        XCTAssertEqual(rolled.requests.count, 1)
        if case .weeklyMilestone(_, let threshold, _) = rolled.requests[0].kind {
            XCTAssertEqual(threshold, 70)
        } else {
            XCTFail("expected a weekly milestone after rollover")
        }
    }

    func testWeeklyLadderAdvancesWhileNotificationsDisabled() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                grokUsage: GrokUsage(weekly: UsageMetric(utilization: 95, resetsAt: nil), fetchedAt: now),
                settings: NotificationSettings(notifyOnSessionReset: true, notifyOnUsageThresholds: false),
                now: now
            )
        )
        XCTAssertTrue(plan.requests.isEmpty)
        XCTAssertEqual(plan.state.weeklyState(for: .grokWeekly).lastNotifiedThreshold, 90)
    }

    // MARK: - Guardrail alerts

    private func insights(
        willHitCap: Bool = false,
        capMinutes: Int? = nil,
        resetMinutes: Int? = nil,
        pace: PaceTrack = .unknown
    ) -> SessionGuardrailInsights {
        SessionGuardrailInsights(
            state: .watch,
            currentUtilization: 70,
            timeToResetMinutes: resetMinutes,
            paceTrack: pace,
            baselineAtThisPoint: nil,
            predictedTimeToCapMinutes: capMinutes,
            willHitCapBeforeReset: willHitCap,
            typicalBurnPhase: .unknown,
            earlyHeavyUsageDetected: false,
            resetReliefSoon: false
        )
    }

    func testCapRiskAlertFiresOncePerSession() async throws {
        let sessionReset = now.addingTimeInterval(50 * 60)
        let input = NotificationPlanInput(
            usage: claude(session: 70, sessionResetsAt: sessionReset),
            insights: insights(willHitCap: true, capMinutes: 34, resetMinutes: 50),
            now: now
        )
        let first = NotificationPlanner.plan(input)
        let capRisk = first.requests.first { $0.kind == .capRisk }
        XCTAssertNotNil(capRisk)
        XCTAssertEqual(capRisk?.body, "Projected cap in 34m (reset in 50m).")
        XCTAssertTrue(first.state.guardrail.sentCapRisk)

        var repeated = input
        repeated.state = first.state
        let second = NotificationPlanner.plan(repeated)
        XCTAssertFalse(second.requests.contains { $0.kind == .capRisk })
    }

    func testResetSoonFiresUnderFifteenMinutes() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(session: 70, sessionResetsAt: now.addingTimeInterval(12 * 60)),
                insights: insights(resetMinutes: 12),
                now: now
            )
        )
        let request = plan.requests.first { $0.kind == .resetSoon }
        XCTAssertNotNil(request)
        XCTAssertEqual(request?.body, "Reset expected in about 12m.")
    }

    func testResetSoonDoesNotFireWithoutAResetTime() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(usage: claude(session: 70), insights: insights(), now: now)
        )
        XCTAssertFalse(plan.requests.contains { $0.kind == .resetSoon })
    }

    func testBelowUsualPaceFiresOnce() async throws {
        let sessionReset = now.addingTimeInterval(2 * 3_600)
        let input = NotificationPlanInput(
            usage: claude(session: 20, sessionResetsAt: sessionReset),
            insights: insights(pace: .belowUsual),
            now: now
        )
        let first = NotificationPlanner.plan(input)
        XCTAssertTrue(first.requests.contains { $0.kind == .belowUsualPace })

        var repeated = input
        repeated.state = first.state
        XCTAssertFalse(NotificationPlanner.plan(repeated).requests.contains { $0.kind == .belowUsualPace })
    }

    func testGuardrailStateResetsWhenSessionWindowJumps() async throws {
        let firstReset = now.addingTimeInterval(2 * 3_600)
        let first = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(session: 20, sessionResetsAt: firstReset),
                insights: insights(pace: .belowUsual),
                now: now
            )
        )
        XCTAssertTrue(first.state.guardrail.sentBelowPace)

        // A drift of a few minutes is the same rolling session.
        let drifted = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(session: 22, sessionResetsAt: firstReset.addingTimeInterval(4 * 60)),
                insights: insights(pace: .belowUsual),
                state: first.state,
                now: now
            )
        )
        XCTAssertFalse(drifted.requests.contains { $0.kind == .belowUsualPace })

        // A jump beyond 30 minutes is a brand-new session.
        let jumped = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(session: 5, sessionResetsAt: firstReset.addingTimeInterval(45 * 60)),
                insights: insights(pace: .belowUsual),
                state: drifted.state,
                now: now
            )
        )
        XCTAssertTrue(jumped.requests.contains { $0.kind == .belowUsualPace })
    }

    func testGuardrailAlertsRespectTheThresholdToggle() async throws {
        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                usage: claude(session: 70, sessionResetsAt: now.addingTimeInterval(10 * 60)),
                insights: insights(resetMinutes: 10),
                settings: NotificationSettings(notifyOnSessionReset: true, notifyOnUsageThresholds: false),
                now: now
            )
        )
        XCTAssertTrue(plan.requests.isEmpty)
    }

    func testNotificationStateSurvivesACodableRoundTrip() async throws {
        var state = NotificationState.empty
        state.lastNotifiedSessionThreshold = 85
        state.guardrail = GuardrailNotificationState(
            sessionEpochMs: 1_234_567,
            sentCapRisk: true,
            sentResetSoon: false,
            sentBelowPace: true
        )
        state.weekly[.codexWeekly] = WeeklyThresholdState(
            windowResetsAt: now,
            lastNotifiedThreshold: 80
        )

        let data = try JSONEncoder().encode(state)
        let decoded = try JSONDecoder().decode(NotificationState.self, from: data)
        XCTAssertEqual(decoded, state)
        XCTAssertEqual(decoded.weeklyState(for: .codexWeekly).lastNotifiedThreshold, 80)
        XCTAssertEqual(decoded.weeklyState(for: .grokWeekly), .empty)
    }
}
