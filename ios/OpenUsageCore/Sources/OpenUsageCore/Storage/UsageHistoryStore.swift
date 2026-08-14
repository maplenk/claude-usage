import Foundation

/// Faithful port of `data/local/UsageHistoryStore.kt`: a CSV file of raw usage
/// samples, retained for 30 days and capped at 10,000 rows. The on-disk format
/// is byte-compatible with the Android file, including the 5-column legacy rows.
public final class UsageHistoryStore: @unchecked Sendable {
    public static let retentionDays = 30
    public static let maxPoints = 10_000
    public static let fileName = "usage_history.csv"

    private let fileURL: URL
    private let lock = NSLock()
    private var cached: [UsageHistoryPoint]?

    public init(directory: URL) {
        self.fileURL = directory.appendingPathComponent(Self.fileName)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    /// Default location: `Application Support/OpenUsage`.
    public static func defaultDirectory() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("OpenUsage", isDirectory: true)
    }

    public func history(now: Date = Date()) -> [UsageHistoryPoint] {
        lock.lock()
        defer { lock.unlock() }
        return loadLocked(now: now)
    }

    @discardableResult
    public func append(_ usage: ClaudeUsage, now: Date = Date()) -> [UsageHistoryPoint] {
        let point = UsageHistoryPoint(
            timestamp: usage.fetchedAt,
            fiveHourUtilization: usage.fiveHour?.utilization,
            fiveHourResetsAt: usage.fiveHour?.resetsAt,
            sevenDayUtilization: usage.sevenDay?.utilization,
            sevenDayOpusUtilization: usage.sevenDayOpus?.utilization,
            sevenDaySonnetUtilization: usage.sevenDaySonnet?.utilization
        )

        lock.lock()
        defer { lock.unlock() }
        let existing = loadLocked(now: now)
        let updated = Self.pruneAndTrim(existing + [point], now: now)
        cached = updated
        write(updated)
        return updated
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        cached = []
        try? FileManager.default.removeItem(at: fileURL)
    }

    // MARK: - Private

    private func loadLocked(now: Date) -> [UsageHistoryPoint] {
        if let cached { return cached }

        guard let contents = try? String(contentsOf: fileURL, encoding: .utf8) else {
            cached = []
            return []
        }

        let parsed = contents
            .split(separator: "\n", omittingEmptySubsequences: true)
            .compactMap { Self.parseLine(String($0)) }
            .sorted { $0.timestamp < $1.timestamp }

        let pruned = Self.pruneAndTrim(parsed, now: now)
        cached = pruned
        if pruned.count != parsed.count { write(pruned) }
        return pruned
    }

    private func write(_ points: [UsageHistoryPoint]) {
        var content = ""
        for point in points {
            content += "\(epochMillis(point.timestamp)),"
            content += "\(Self.token(point.fiveHourUtilization)),"
            content += "\(Self.token(point.fiveHourResetsAt)),"
            content += "\(Self.token(point.sevenDayUtilization)),"
            content += "\(Self.token(point.sevenDayOpusUtilization)),"
            content += "\(Self.token(point.sevenDaySonnetUtilization))\n"
        }
        try? content.write(to: fileURL, atomically: true, encoding: .utf8)
    }

    static func token(_ value: Double?) -> String {
        guard let value else { return "" }
        return String(value)
    }

    static func token(_ value: Date?) -> String {
        guard let value else { return "" }
        return String(epochMillis(value))
    }

    static func parseLine(_ line: String) -> UsageHistoryPoint? {
        let tokens = line.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
        guard tokens.count == 5 || tokens.count == 6 else { return nil }
        guard let timestampMs = Int64(tokens[0]) else { return nil }
        let timestamp = Date(timeIntervalSince1970: TimeInterval(timestampMs) / 1000.0)

        if tokens.count == 6 {
            return UsageHistoryPoint(
                timestamp: timestamp,
                fiveHourUtilization: Double(tokens[1]),
                fiveHourResetsAt: Int64(tokens[2]).map { Date(timeIntervalSince1970: TimeInterval($0) / 1000.0) },
                sevenDayUtilization: Double(tokens[3]),
                sevenDayOpusUtilization: Double(tokens[4]),
                sevenDaySonnetUtilization: Double(tokens[5])
            )
        }

        // Backward compatibility with the older cache format (no resets_at).
        return UsageHistoryPoint(
            timestamp: timestamp,
            fiveHourUtilization: Double(tokens[1]),
            fiveHourResetsAt: nil,
            sevenDayUtilization: Double(tokens[2]),
            sevenDayOpusUtilization: Double(tokens[3]),
            sevenDaySonnetUtilization: Double(tokens[4])
        )
    }

    static func pruneAndTrim(_ points: [UsageHistoryPoint], now: Date) -> [UsageHistoryPoint] {
        let cutoff = now.addingTimeInterval(-TimeInterval(retentionDays) * 86_400)
        let retained = points.filter { $0.timestamp >= cutoff }
        if retained.count > maxPoints {
            return Array(retained.suffix(maxPoints))
        }
        return retained
    }
}
