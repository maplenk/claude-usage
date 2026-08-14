import Foundation

/// `Instant.parse` on the JVM accepts any ISO-8601 instant, including fractional
/// seconds of arbitrary precision (`2026-08-08T04:01:09.238389+00:00`) which is
/// exactly what the Grok billing endpoint returns. `ISO8601DateFormatter` is
/// fussier than that, so parsing is normalised first and the formatters are
/// tried in order.
public enum ISO8601 {
    private static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    public static func parse(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }

        if let date = fractional.date(from: trimmed) { return date }
        if let date = plain.date(from: trimmed) { return date }

        let normalised = normalise(trimmed)
        if normalised != trimmed {
            if let date = fractional.date(from: normalised) { return date }
            if let date = plain.date(from: normalised) { return date }
        }
        return nil
    }

    public static func string(from date: Date) -> String {
        plain.string(from: date)
    }

    /// Truncates sub-millisecond precision and rewrites a `+00:00`-style offset
    /// into the `Z`/`+0000` shapes the system formatter reliably accepts.
    private static func normalise(_ value: String) -> String {
        var body = value
        var suffix = ""

        if body.hasSuffix("Z") || body.hasSuffix("z") {
            suffix = "Z"
            body.removeLast()
        } else if let offsetStart = offsetIndex(in: body) {
            suffix = String(body[offsetStart...])
            body = String(body[body.startIndex..<offsetStart])
        }

        if let dot = body.lastIndex(of: ".") {
            let digitsStart = body.index(after: dot)
            let digits = body[digitsStart...].prefix { $0.isNumber }
            if digits.isEmpty {
                body.removeSubrange(dot..<digitsStart)
            } else if digits.count > 3 {
                let keepEnd = body.index(digitsStart, offsetBy: 3)
                let dropEnd = body.index(digitsStart, offsetBy: digits.count)
                body.removeSubrange(keepEnd..<dropEnd)
            }
        }

        if suffix.isEmpty { suffix = "Z" }
        return body + suffix
    }

    /// Finds the start of a trailing `+HH:MM` / `-HHMM` timezone offset, taking
    /// care not to mistake the date's own hyphens for a negative offset.
    private static func offsetIndex(in value: String) -> String.Index? {
        guard let timeSeparator = value.firstIndex(of: "T") else { return nil }
        var index = value.endIndex
        while index > timeSeparator {
            index = value.index(before: index)
            let character = value[index]
            if character == "+" || character == "-" { return index }
            if character == "T" { return nil }
        }
        return nil
    }
}
