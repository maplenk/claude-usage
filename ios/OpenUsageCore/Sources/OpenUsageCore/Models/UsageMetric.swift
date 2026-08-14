import Foundation

/// Mirrors `domain/model/UsageStatus.kt`.
public enum UsageStatus: String, Codable, Sendable, CaseIterable {
    case safe
    case moderate
    case critical

    public static func fromUtilization(_ value: Double) -> UsageStatus {
        if value >= 80.0 { return .critical }
        if value >= 50.0 { return .moderate }
        return .safe
    }
}

/// Mirrors `UsageMetric` in `domain/model/ClaudeUsage.kt`.
public struct UsageMetric: Equatable, Hashable, Codable, Sendable {
    public let utilization: Double
    public let resetsAt: Date?

    public init(utilization: Double, resetsAt: Date?) {
        self.utilization = utilization
        self.resetsAt = resetsAt
    }

    public var status: UsageStatus { UsageStatus.fromUtilization(utilization) }

    /// Returns effective utilization: 0% if the reset window has already elapsed.
    public func effectiveUtilization(now: Date = Date()) -> Double {
        if let resetsAt, now > resetsAt { return 0.0 }
        return utilization
    }

    /// Returns effective status based on whether the window has elapsed.
    public func effectiveStatus(now: Date = Date()) -> UsageStatus {
        UsageStatus.fromUtilization(effectiveUtilization(now: now))
    }

    /// True when the reset window has passed and cached data is stale.
    public func isExpired(now: Date = Date()) -> Bool {
        guard let resetsAt else { return false }
        return now > resetsAt
    }
}
