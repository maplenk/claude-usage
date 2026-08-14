import Foundation

// MARK: - Claude (mirrors data/mapper/UsageMapper.kt)

public extension UsageMetricDTO {
    func toDomain() -> UsageMetric {
        UsageMetric(utilization: utilization, resetsAt: ISO8601.parse(resetsAt))
    }
}

public extension UsageResponseDTO {
    func toDomain(now: Date = Date()) -> ClaudeUsage {
        ClaudeUsage(
            fiveHour: fiveHour?.toDomain(),
            sevenDay: sevenDay?.toDomain(),
            sevenDayOpus: sevenDayOpus?.toDomain(),
            sevenDaySonnet: sevenDaySonnet?.toDomain(),
            fetchedAt: now
        )
    }
}

public extension OrganizationDTO {
    func toDomain() -> Organization {
        Organization(uuid: uuid, name: name)
    }
}

public extension Array where Element == OrganizationDTO {
    func toDomain() -> [Organization] { map { $0.toDomain() } }
}

// MARK: - Codex (mirrors data/mapper/CodexUsageMapper.kt)

private let codexWindowToleranceSeconds = 60 * 60

public extension CodexUsageResponseDTO {
    /// Picks whichever rate-limit window is a seven-day window (± 1 hour) and
    /// maps it to the weekly metric. Returns `nil` when no weekly window exists,
    /// so a five-hour window is never mislabelled as weekly.
    func toWeeklyDomain(now: Date = Date()) -> CodexUsage? {
        let windows = [rateLimit?.primaryWindow, rateLimit?.secondaryWindow].compactMap { $0 }
        let weekly = windows.first { window in
            guard let duration = window.limitWindowSeconds else { return false }
            return abs(duration - CodexAPIContract.weeklyWindowSeconds) <= codexWindowToleranceSeconds
        }
        guard let weekly, let utilization = weekly.usedPercent else { return nil }

        return CodexUsage(
            weekly: UsageMetric(
                utilization: min(max(utilization, 0.0), 100.0),
                resetsAt: weekly.resetInstant(now: now)
            ),
            fetchedAt: now
        )
    }
}

extension CodexRateLimitWindowDTO {
    func resetInstant(now: Date) -> Date? {
        if let resetAtEpochSeconds, resetAtEpochSeconds > 0 {
            return Date(timeIntervalSince1970: TimeInterval(resetAtEpochSeconds))
        }
        if let resetAfterSeconds, resetAfterSeconds >= 0 {
            return now.addingTimeInterval(TimeInterval(resetAfterSeconds))
        }
        return nil
    }
}

// MARK: - Grok (mirrors data/mapper/GrokUsageMapper.kt)

public extension GrokCreditsResponseDTO {
    /// Throws with the same user-facing copy the Android mapper uses.
    func toGrokWeeklyDomain(fetchedAt: Date = Date()) throws -> GrokUsage {
        guard let credits = config else {
            throw ProviderError("Grok billing response changed.")
        }
        guard let period = credits.currentPeriod else {
            throw ProviderError("Grok billing period was missing.")
        }
        guard period.type == GrokAPIContract.weeklyPeriodType else {
            throw ProviderError("This Grok account does not have a weekly unified-billing limit yet.")
        }
        guard let resetAt = ISO8601.parse(period.end) else {
            throw ProviderError("Grok weekly reset time was invalid.")
        }
        return GrokUsage(
            weekly: UsageMetric(
                utilization: min(max(credits.creditUsagePercent ?? 0.0, 0.0), 100.0),
                resetsAt: resetAt
            ),
            fetchedAt: fetchedAt
        )
    }
}
