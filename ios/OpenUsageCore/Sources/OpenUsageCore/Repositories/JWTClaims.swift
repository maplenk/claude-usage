import Foundation

/// Faithful port of the `JwtClaims` object in `CodexUsageRepositoryImpl.kt`.
/// Tokens are only ever decoded locally; nothing here logs or transmits them.
public enum JWTClaims {
    private static let accountClaimsKey = "https://api.openai.com/auth"
    private static let accountIdKey = "chatgpt_account_id"
    private static let refreshWindowSeconds: TimeInterval = 5 * 60

    public static func isExpiringSoon(_ token: String, now: Date = Date()) -> Bool {
        guard let payload = payload(token),
              let exp = numeric(payload["exp"]) else { return false }
        return exp <= now.timeIntervalSince1970 + refreshWindowSeconds
    }

    public static func accountId(idToken: String?, accessToken: String) -> String? {
        if let idToken, let found = findAccountId(payload(idToken)) { return found }
        return findAccountId(payload(accessToken))
    }

    static func findAccountId(_ payload: [String: Any]?) -> String? {
        guard let payload else { return nil }
        if let direct = payload[accountIdKey] as? String { return direct }
        if let nested = payload[accountClaimsKey] as? [String: Any],
           let value = nested[accountIdKey] as? String {
            return value
        }
        return nil
    }

    static func payload(_ token: String) -> [String: Any]? {
        let segments = token.split(separator: ".", omittingEmptySubsequences: false)
        guard segments.count > 1 else { return nil }
        guard let data = base64URLDecode(String(segments[1])) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    static func base64URLDecode(_ value: String) -> Data? {
        var normalised = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = normalised.count % 4
        if remainder != 0 {
            normalised += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: normalised)
    }

    private static func numeric(_ value: Any?) -> TimeInterval? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let double = value as? Double { return double }
        if let int = value as? Int { return TimeInterval(int) }
        if let string = value as? String { return TimeInterval(string) }
        return nil
    }
}
