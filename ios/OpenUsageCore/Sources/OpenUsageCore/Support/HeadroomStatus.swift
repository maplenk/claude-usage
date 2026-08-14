import Foundation

/// Display bands from `ui/theme/Guardrail.kt`. These are deliberately separate
/// from the notification milestones.
public enum GuardrailLevel: String, Sendable, CaseIterable {
    case normal
    case elevated
    case high
    case critical
    case unknown

    public static let elevatedAt = 50.0
    public static let highAt = 75.0
    public static let criticalAt = 90.0

    public var label: String {
        switch self {
        case .normal: return "NORMAL"
        case .elevated: return "ELEVATED"
        case .high: return "HIGH"
        case .critical: return "CRITICAL"
        case .unknown: return "STALE"
        }
    }

    /// Never produces `.unknown` — that level is sync-derived only.
    public static func of(_ percentage: Double) -> GuardrailLevel {
        if percentage < elevatedAt { return .normal }
        if percentage < highAt { return .elevated }
        if percentage < criticalAt { return .high }
        return .critical
    }
}

/// Port of `HeadroomStatus` in `ui/dashboard/components/HeadroomStatus.kt`.
public enum HeadroomStatus: String, Sendable, CaseIterable {
    case normal
    case elevated
    case high
    case critical
    case stale

    public var label: String { guardrail.label }

    public var guardrail: GuardrailLevel {
        switch self {
        case .normal: return .normal
        case .elevated: return .elevated
        case .high: return .high
        case .critical: return .critical
        case .stale: return .unknown
        }
    }

    public static func of(_ metric: UsageMetric?, now: Date = Date()) -> HeadroomStatus {
        guard let metric, !metric.isExpired(now: now) else { return .stale }
        let utilization = metric.effectiveUtilization(now: now)
        if utilization >= GuardrailLevel.criticalAt { return .critical }
        if utilization >= GuardrailLevel.highAt { return .high }
        if utilization >= GuardrailLevel.elevatedAt { return .elevated }
        return .normal
    }

    /// Cards render `.stale` whenever the whole sync is stale, matching the
    /// `isStale && metric != nil` branch in the Compose components.
    public static func of(_ metric: UsageMetric?, isStale: Bool, now: Date = Date()) -> HeadroomStatus {
        if isStale && metric != nil { return .stale }
        return of(metric, now: now)
    }
}

/// Provider identity used for accent colours and marks.
public enum ProviderBrand: String, Sendable, CaseIterable {
    case claude
    case codex
    case grok

    public var displayName: String {
        switch self {
        case .claude: return "Claude"
        case .codex: return "Codex"
        case .grok: return "Grok"
        }
    }
}
