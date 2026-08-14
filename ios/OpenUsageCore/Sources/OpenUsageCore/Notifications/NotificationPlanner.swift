import Foundation

/// A notification the app layer should deliver. The core never imports
/// `UserNotifications`; it only decides *what* to send.
public struct UsageNotificationRequest: Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        case sessionReset
        case sessionMilestone(threshold: Int, currentPercent: Int)
        case weeklyMilestone(limit: WeeklyLimitKey, threshold: Int, currentPercent: Int)
        case capRisk
        case resetSoon
        case belowUsualPace
    }

    public let kind: Kind
    /// Stable identifier — replaces Android's notification IDs.
    public let identifier: String
    /// Category identifier — the analogue of Android's notification channel.
    public let category: NotificationCategory
    public let title: String
    public let body: String

    public init(
        kind: Kind,
        identifier: String,
        category: NotificationCategory,
        title: String,
        body: String
    ) {
        self.kind = kind
        self.identifier = identifier
        self.category = category
        self.title = title
        self.body = body
    }
}

/// Mirrors the four Android notification channels (minus the persistent one,
/// which has no iOS equivalent).
public enum NotificationCategory: String, Sendable, CaseIterable {
    case sessionReset = "session_reset"
    case usageMilestone = "usage_milestone"
    case weeklyMilestone = "weekly_milestone"
    case guardrail = "session_guardrail"

    public var displayName: String {
        switch self {
        case .sessionReset: return "Session Reset"
        case .usageMilestone: return "Usage Milestones"
        case .weeklyMilestone: return "Weekly Milestones"
        case .guardrail: return "Session guardrail alerts"
        }
    }
}

/// Mirrors `GuardrailNotificationState` in `UserPreferencesStore.kt`.
public struct GuardrailNotificationState: Equatable, Sendable, Codable {
    public var sessionEpochMs: Int64?
    public var sentCapRisk: Bool
    public var sentResetSoon: Bool
    public var sentBelowPace: Bool

    public init(
        sessionEpochMs: Int64? = nil,
        sentCapRisk: Bool = false,
        sentResetSoon: Bool = false,
        sentBelowPace: Bool = false
    ) {
        self.sessionEpochMs = sessionEpochMs
        self.sentCapRisk = sentCapRisk
        self.sentResetSoon = sentResetSoon
        self.sentBelowPace = sentBelowPace
    }

    public static let empty = GuardrailNotificationState()
}

/// Everything the planner persists between refreshes.
public struct NotificationState: Equatable, Sendable, Codable {
    public var lastNotifiedSessionThreshold: Int?
    public var guardrail: GuardrailNotificationState
    public var weekly: [WeeklyLimitKey: WeeklyThresholdState]

    public init(
        lastNotifiedSessionThreshold: Int? = nil,
        guardrail: GuardrailNotificationState = .empty,
        weekly: [WeeklyLimitKey: WeeklyThresholdState] = [:]
    ) {
        self.lastNotifiedSessionThreshold = lastNotifiedSessionThreshold
        self.guardrail = guardrail
        self.weekly = weekly
    }

    public static let empty = NotificationState()

    public func weeklyState(for key: WeeklyLimitKey) -> WeeklyThresholdState {
        weekly[key] ?? .empty
    }

    // Dictionaries keyed by a custom enum encode as a flat array by default,
    // so the weekly map is bridged through `[String: …]` for readable JSON.
    private enum CodingKeys: String, CodingKey {
        case lastNotifiedSessionThreshold
        case guardrail
        case weekly
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.lastNotifiedSessionThreshold = try container.decodeIfPresent(
            Int.self,
            forKey: .lastNotifiedSessionThreshold
        )
        self.guardrail = try container.decodeIfPresent(
            GuardrailNotificationState.self,
            forKey: .guardrail
        ) ?? .empty
        let raw = try container.decodeIfPresent([String: WeeklyThresholdState].self, forKey: .weekly) ?? [:]
        var mapped: [WeeklyLimitKey: WeeklyThresholdState] = [:]
        for (key, value) in raw {
            if let limit = WeeklyLimitKey(rawValue: key) { mapped[limit] = value }
        }
        self.weekly = mapped
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(lastNotifiedSessionThreshold, forKey: .lastNotifiedSessionThreshold)
        try container.encode(guardrail, forKey: .guardrail)
        var raw: [String: WeeklyThresholdState] = [:]
        for (key, value) in weekly { raw[key.rawValue] = value }
        try container.encode(raw, forKey: .weekly)
    }
}

public struct NotificationSettings: Equatable, Sendable {
    public var notifyOnSessionReset: Bool
    public var notifyOnUsageThresholds: Bool

    public init(notifyOnSessionReset: Bool = true, notifyOnUsageThresholds: Bool = true) {
        self.notifyOnSessionReset = notifyOnSessionReset
        self.notifyOnUsageThresholds = notifyOnUsageThresholds
    }
}

public struct NotificationPlanInput: Sendable {
    public var previousSessionUtilization: Double?
    public var usage: ClaudeUsage?
    public var codexUsage: CodexUsage?
    public var grokUsage: GrokUsage?
    public var insights: SessionGuardrailInsights?
    public var settings: NotificationSettings
    public var state: NotificationState
    public var now: Date

    public init(
        previousSessionUtilization: Double? = nil,
        usage: ClaudeUsage? = nil,
        codexUsage: CodexUsage? = nil,
        grokUsage: GrokUsage? = nil,
        insights: SessionGuardrailInsights? = nil,
        settings: NotificationSettings = NotificationSettings(),
        state: NotificationState = .empty,
        now: Date = Date()
    ) {
        self.previousSessionUtilization = previousSessionUtilization
        self.usage = usage
        self.codexUsage = codexUsage
        self.grokUsage = grokUsage
        self.insights = insights
        self.settings = settings
        self.state = state
        self.now = now
    }
}

public struct NotificationPlan: Equatable, Sendable {
    public var requests: [UsageNotificationRequest]
    public var state: NotificationState

    public init(requests: [UsageNotificationRequest], state: NotificationState) {
        self.requests = requests
        self.state = state
    }
}

/// Pure decision layer that replaces the notification orchestration living
/// inside `UsageRepositoryImpl` and `UsageSyncWorker` on Android.
public enum NotificationPlanner {
    /// Kotlin treats the rolling 5-hour window as a new session only when
    /// `resetsAt` jumps by more than 30 minutes.
    private static let newSessionThresholdMs: Int64 = 30 * 60 * 1000

    public static func plan(_ input: NotificationPlanInput) -> NotificationPlan {
        var state = input.state
        var requests: [UsageNotificationRequest] = []

        planSessionReset(input, &requests)
        planSessionMilestone(input, &state, &requests)
        planWeeklyMilestones(input, &state, &requests)
        planGuardrail(input, &state, &requests)

        return NotificationPlan(requests: requests, state: state)
    }

    // MARK: - Session reset (mirrors UsageSyncWorker)

    private static func planSessionReset(
        _ input: NotificationPlanInput,
        _ requests: inout [UsageNotificationRequest]
    ) {
        guard input.settings.notifyOnSessionReset else { return }
        guard let previous = input.previousSessionUtilization, previous > 0.0 else { return }
        guard let current = input.usage?.fiveHour?.utilization, current == 0.0 else { return }

        requests.append(
            UsageNotificationRequest(
                kind: .sessionReset,
                identifier: "session_reset",
                category: .sessionReset,
                title: "Session limit reset",
                body: "Claude is ready to use again"
            )
        )
    }

    // MARK: - Session milestone ladder (mirrors maybeNotifyUsageMilestone)

    private static func planSessionMilestone(
        _ input: NotificationPlanInput,
        _ state: inout NotificationState,
        _ requests: inout [UsageNotificationRequest]
    ) {
        let currentUtilization = input.usage?.fiveHour?.utilization
        let currentHighestReached = UsageThresholdEvaluator.highestReachedThreshold(
            currentUtilization: currentUtilization
        )

        guard let currentHighestReached else {
            // Dropped below the first threshold: clear for the next climb.
            state.lastNotifiedSessionThreshold = nil
            return
        }

        let lastNotified = state.lastNotifiedSessionThreshold
        let crossed = UsageThresholdEvaluator.highestCrossedThreshold(
            previousUtilization: input.previousSessionUtilization,
            currentUtilization: currentUtilization
        )

        // Upgrade-safe fallback: already above a threshold with nothing recorded.
        let thresholdToNotify: Int? = crossed ?? (lastNotified == nil ? currentHighestReached : nil)

        if let thresholdToNotify,
           lastNotified == nil || thresholdToNotify > lastNotified!,
           input.settings.notifyOnUsageThresholds,
           let currentUtilization {
            let currentPercent = Int(clamp(currentUtilization, 0.0, 100.0).rounded())
            requests.append(
                UsageNotificationRequest(
                    kind: .sessionMilestone(threshold: thresholdToNotify, currentPercent: currentPercent),
                    identifier: "usage_milestone_\(thresholdToNotify)",
                    category: .usageMilestone,
                    title: "Session usage alert",
                    body: "Limit used \(currentPercent)%"
                )
            )
        }

        // Advance the baseline even while notifications are disabled.
        if lastNotified == nil || currentHighestReached > lastNotified! {
            state.lastNotifiedSessionThreshold = currentHighestReached
        }
    }

    // MARK: - Weekly milestone ladder

    private static func planWeeklyMilestones(
        _ input: NotificationPlanInput,
        _ state: inout NotificationState,
        _ requests: inout [UsageNotificationRequest]
    ) {
        let metrics: [(WeeklyLimitKey, UsageMetric?)] = [
            (.claudeWeekly, input.usage?.sevenDay),
            (.claudeWeeklyOpus, input.usage?.sevenDayOpus),
            (.codexWeekly, input.codexUsage?.weekly),
            (.grokWeekly, input.grokUsage?.weekly),
        ]

        for (key, metric) in metrics {
            let decision = WeeklyThresholdEvaluator.evaluate(
                metric: metric,
                previousState: state.weeklyState(for: key)
            )
            state.weekly[key] = decision.newState

            guard input.settings.notifyOnUsageThresholds,
                  let threshold = decision.thresholdToNotify,
                  let currentPercent = decision.currentPercent else { continue }

            requests.append(
                UsageNotificationRequest(
                    kind: .weeklyMilestone(limit: key, threshold: threshold, currentPercent: currentPercent),
                    identifier: "weekly_milestone_\(key.rawValue)_\(threshold)",
                    category: .weeklyMilestone,
                    title: "\(key.displayName) usage alert",
                    body: "\(key.displayName) limit used \(currentPercent)%"
                )
            )
        }
    }

    // MARK: - Guardrail alerts (mirrors maybeNotifyGuardrailSignals)

    private static func planGuardrail(
        _ input: NotificationPlanInput,
        _ state: inout NotificationState,
        _ requests: inout [UsageNotificationRequest]
    ) {
        guard input.settings.notifyOnUsageThresholds else { return }
        guard let insights = input.insights else { return }

        let sessionEpoch = input.usage?.fiveHour?.resetsAt.map(epochMillis)
        var guardrail = state.guardrail

        let isNewSession: Bool
        if let sessionEpoch, let stored = guardrail.sessionEpochMs {
            isNewSession = abs(sessionEpoch - stored) > newSessionThresholdMs
        } else {
            isNewSession = true
        }

        if isNewSession {
            guardrail = GuardrailNotificationState(
                sessionEpochMs: sessionEpoch,
                sentCapRisk: false,
                sentResetSoon: false,
                sentBelowPace: false
            )
        }

        if insights.willHitCapBeforeReset && !guardrail.sentCapRisk {
            let capIn = insights.predictedTimeToCapMinutes.map(Formatters.minutes) ?? "soon"
            let resetIn = insights.timeToResetMinutes.map(Formatters.minutes) ?? "unknown"
            requests.append(
                UsageNotificationRequest(
                    kind: .capRisk,
                    identifier: "guardrail_cap_risk",
                    category: .guardrail,
                    title: "Session may hit cap before reset",
                    body: "Projected cap in \(capIn) (reset in \(resetIn))."
                )
            )
            guardrail.sentCapRisk = true
        }

        if let timeToReset = insights.timeToResetMinutes, timeToReset <= 15, !guardrail.sentResetSoon {
            requests.append(
                UsageNotificationRequest(
                    kind: .resetSoon,
                    identifier: "guardrail_reset_soon",
                    category: .guardrail,
                    title: "Session reset expected soon",
                    body: "Reset expected in about \(Formatters.minutes(timeToReset))."
                )
            )
            guardrail.sentResetSoon = true
        }

        if insights.paceTrack == .belowUsual && !guardrail.sentBelowPace {
            requests.append(
                UsageNotificationRequest(
                    kind: .belowUsualPace,
                    identifier: "guardrail_below_pace",
                    category: .guardrail,
                    title: "Below your usual pace",
                    body: "You are tracking below your typical session usage pace today."
                )
            )
            guardrail.sentBelowPace = true
        }

        state.guardrail = guardrail
    }
}
