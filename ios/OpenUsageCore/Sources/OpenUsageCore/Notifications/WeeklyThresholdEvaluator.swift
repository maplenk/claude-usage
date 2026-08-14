import Foundation

/// The four weekly limits that carry their own milestone ladder. The Claude
/// weekly *Sonnet* limit is deliberately excluded.
public enum WeeklyLimitKey: String, CaseIterable, Sendable, Codable {
    case claudeWeekly = "claude_weekly"
    case claudeWeeklyOpus = "claude_weekly_opus"
    case codexWeekly = "codex_weekly"
    case grokWeekly = "grok_weekly"

    /// Used in notification copy, e.g. "Claude weekly limit used 82%".
    public var displayName: String {
        switch self {
        case .claudeWeekly: return "Claude weekly"
        case .claudeWeeklyOpus: return "Claude weekly Opus"
        case .codexWeekly: return "Codex weekly"
        case .grokWeekly: return "Grok weekly"
        }
    }

    /// Stable numeric offset so each limit gets its own notification slot.
    public var notificationSlot: Int {
        switch self {
        case .claudeWeekly: return 0
        case .claudeWeeklyOpus: return 1
        case .codexWeekly: return 2
        case .grokWeekly: return 3
        }
    }
}

/// Per-limit dedup state. Persisted alongside the other notification state.
public struct WeeklyThresholdState: Equatable, Sendable, Codable {
    /// The `resetsAt` of the window the `lastNotifiedThreshold` belongs to.
    public var windowResetsAt: Date?
    public var lastNotifiedThreshold: Int?

    public init(windowResetsAt: Date? = nil, lastNotifiedThreshold: Int? = nil) {
        self.windowResetsAt = windowResetsAt
        self.lastNotifiedThreshold = lastNotifiedThreshold
    }

    public static let empty = WeeklyThresholdState()
}

public struct WeeklyThresholdDecision: Equatable, Sendable {
    /// The single highest threshold crossed on this refresh, or `nil`.
    public let thresholdToNotify: Int?
    /// The percentage to render in the notification body.
    public let currentPercent: Int?
    public let newState: WeeklyThresholdState
    /// True when this refresh observed a brand-new weekly window.
    public let didRollOver: Bool

    public init(
        thresholdToNotify: Int?,
        currentPercent: Int?,
        newState: WeeklyThresholdState,
        didRollOver: Bool
    ) {
        self.thresholdToNotify = thresholdToNotify
        self.currentPercent = currentPercent
        self.newState = newState
        self.didRollOver = didRollOver
    }
}

/// The weekly milestone ladder.
///
/// Rules, mirroring the session ladder's behaviour where they overlap:
///  - thresholds are 70 / 80 / 90 / 100;
///  - at most one notification per limit per refresh — the highest crossed;
///  - dedup is keyed on the metric's `resetsAt`. A **later** `resetsAt` means a
///    new weekly window, so that limit's ladder state is cleared;
///  - if utilization falls below the first threshold the state is cleared too,
///    which covers a window that rolls over without the reset time moving;
///  - the "upgrade-safe" fallback of the session ladder applies: if nothing has
///    been notified this window and usage is already above a threshold, the
///    current highest threshold fires once.
public enum WeeklyThresholdEvaluator {
    public static let thresholds = [70, 80, 90, 100]

    public static func highestReachedThreshold(currentUtilization: Double?) -> Int? {
        guard let currentUtilization else { return nil }
        let current = clamp(currentUtilization, 0.0, 100.0)
        return thresholds.last { current >= Double($0) }
    }

    public static func evaluate(
        metric: UsageMetric?,
        previousState: WeeklyThresholdState
    ) -> WeeklyThresholdDecision {
        guard let metric else {
            return WeeklyThresholdDecision(
                thresholdToNotify: nil,
                currentPercent: nil,
                newState: previousState,
                didRollOver: false
            )
        }

        var state = previousState
        var didRollOver = false

        if let incoming = metric.resetsAt {
            if let stored = state.windowResetsAt {
                if incoming > stored {
                    // A later reset time means the previous window closed.
                    state = WeeklyThresholdState(windowResetsAt: incoming, lastNotifiedThreshold: nil)
                    didRollOver = true
                }
                // An identical or earlier reset time keeps the current window.
            } else {
                state.windowResetsAt = incoming
            }
        }

        let utilization = clamp(metric.utilization, 0.0, 100.0)
        let currentPercent = Int(utilization.rounded())

        guard let currentHighest = highestReachedThreshold(currentUtilization: utilization) else {
            // Below the first rung: reset the ladder for the next climb.
            state.lastNotifiedThreshold = nil
            return WeeklyThresholdDecision(
                thresholdToNotify: nil,
                currentPercent: currentPercent,
                newState: state,
                didRollOver: didRollOver
            )
        }

        let shouldNotify: Bool
        if let last = state.lastNotifiedThreshold {
            shouldNotify = currentHighest > last
        } else {
            shouldNotify = true
        }

        state.lastNotifiedThreshold = currentHighest

        return WeeklyThresholdDecision(
            thresholdToNotify: shouldNotify ? currentHighest : nil,
            currentPercent: currentPercent,
            newState: state,
            didRollOver: didRollOver
        )
    }
}
