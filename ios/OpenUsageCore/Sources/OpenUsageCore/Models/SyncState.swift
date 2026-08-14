import Foundation

/// Faithful port of `ui/dashboard/SyncState.kt`.
public enum SyncState: Equatable, Sendable {
    case fresh(fetchedAt: Date?, ageMinutes: Int?)
    case ageing(fetchedAt: Date, ageMinutes: Int)
    case stale(fetchedAt: Date, ageMinutes: Int)
    case offline(fetchedAt: Date?, ageMinutes: Int?)

    public var fetchedAt: Date? {
        switch self {
        case .fresh(let date, _): return date
        case .ageing(let date, _): return date
        case .stale(let date, _): return date
        case .offline(let date, _): return date
        }
    }

    public var ageMinutes: Int? {
        switch self {
        case .fresh(_, let age): return age
        case .ageing(_, let age): return age
        case .stale(_, let age): return age
        case .offline(_, let age): return age
        }
    }

    public var isOffline: Bool {
        if case .offline = self { return true }
        return false
    }

    public var isStale: Bool {
        switch self {
        case .stale, .offline: return true
        default: return false
        }
    }

    public var isFresh: Bool {
        if case .fresh = self { return true }
        return false
    }

    public static func make(fetchedAt: Date?, isOnline: Bool, now: Date = Date()) -> SyncState {
        let ageMinutes = fetchedAt.map { max(minutesBetween($0, now), 0) }
        if !isOnline { return .offline(fetchedAt: fetchedAt, ageMinutes: ageMinutes) }
        guard let fetchedAt, let ageMinutes else {
            return .fresh(fetchedAt: nil, ageMinutes: nil)
        }
        if ageMinutes < 5 { return .fresh(fetchedAt: fetchedAt, ageMinutes: ageMinutes) }
        if ageMinutes < 10 { return .ageing(fetchedAt: fetchedAt, ageMinutes: ageMinutes) }
        return .stale(fetchedAt: fetchedAt, ageMinutes: ageMinutes)
    }
}
