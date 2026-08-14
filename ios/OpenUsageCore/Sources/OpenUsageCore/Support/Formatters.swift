import Foundation

/// Display helpers ported from the Compose components so both the SwiftUI app
/// and any future widget render identical strings.
public enum Formatters {
    /// `"2h 34m"` / `"12m"` — mirrors `formatMinutes` in `UsageRepositoryImpl`
    /// and `SessionGuardrailCard`.
    public static func minutes(_ totalMinutes: Int) -> String {
        let clamped = max(totalMinutes, 0)
        let hours = clamped / 60
        let remaining = clamped % 60
        return hours > 0 ? "\(hours)h \(remaining)m" : "\(remaining)m"
    }

    /// `"2h 45m 12s"` — mirrors `CountdownTimer.formatDuration`.
    public static func countdown(secondsRemaining: Int) -> String {
        if secondsRemaining <= 0 { return "Expired" }
        let days = secondsRemaining / 86_400
        let hours = (secondsRemaining % 86_400) / 3_600
        let minutes = (secondsRemaining % 3_600) / 60
        let seconds = secondsRemaining % 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m \(seconds)s" }
        return "\(minutes)m \(seconds)s"
    }

    /// Seconds until `date`, floored at zero.
    public static func secondsRemaining(until date: Date, now: Date = Date()) -> Int {
        let diff = Int(date.timeIntervalSince(now))
        return diff < 0 ? 0 : diff
    }

    /// Mirrors `UsageMetric?.resetLabel(useRelativeTime)` in `ProviderUsageCards.kt`.
    public static func resetLabel(
        for metric: UsageMetric?,
        useRelativeTime: Bool,
        now: Date = Date()
    ) -> String {
        guard let metric, let resetsAt = metric.resetsAt else { return "—" }
        if metric.isExpired(now: now) { return "Awaiting sync" }
        if !useRelativeTime { return absoluteReset(resetsAt) }

        let seconds = Int(resetsAt.timeIntervalSince(now))
        if seconds <= 0 { return "Expired" }
        let days = seconds / 86_400
        let hours = (seconds % 86_400) / 3_600
        let minutes = (seconds % 3_600) / 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    /// `"Mon, 2:30 PM"` — mirrors `DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.US)`.
    public static func absoluteReset(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "EEE, h:mm a"
        return formatter.string(from: date)
    }

    /// `"Aug 14, 2:30 PM"` — mirrors `DashboardMetaCard.absoluteTime`.
    public static func absoluteSyncTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "MMM d, h:mm a"
        return formatter.string(from: date)
    }

    /// Mirrors `Instant?.relativeAge()` in `DashboardMetaCard.kt`.
    public static func relativeAge(_ date: Date?, now: Date = Date()) -> String {
        guard let date else { return "not yet" }
        let elapsed = now.timeIntervalSince(date)
        if elapsed < 60 { return "just now" }
        let minutes = Int(elapsed / 60)
        if minutes < 60 { return "\(minutes)m ago" }
        let hours = Int(elapsed / 3_600)
        if hours < 24 { return "\(hours)h ago" }
        return "\(Int(elapsed / 86_400))d ago"
    }

    /// Mirrors `Long?.ageText()` in `DashboardScreen.kt`.
    public static func ageText(minutes: Int?) -> String {
        guard let minutes else { return "an unknown time ago" }
        if minutes < 1 { return "just now" }
        if minutes < 60 { return "\(minutes)m ago" }
        if minutes < 1_440 { return "\(minutes / 60)h ago" }
        return "\(minutes / 1_440)d ago"
    }

    /// Masks a session key the way `SettingsViewModel.maskKey` does.
    public static func maskKey(_ key: String) -> String {
        if key.count <= 8 { return "****" }
        let prefix = String(key.prefix(7))
        let suffix = String(key.suffix(4))
        return prefix + String(repeating: "*", count: key.count - 11) + suffix
    }
}
