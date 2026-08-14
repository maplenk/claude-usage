import Foundation

/// Mirrors `domain/model/UsageError.kt`. Kotlin wraps this in
/// `UsageApiException`; Swift throws the enum itself.
public enum UsageError: Error, Equatable, Sendable {
    case unauthorized
    case rateLimited
    case networkError
    case serverError(code: Int, message: String?)
    case noCredentials
    case unknown(message: String)

    /// Mirrors `DashboardScreen.toUiMessage()`.
    public var uiMessage: String {
        switch self {
        case .unauthorized: return "Session expired. Update it in Settings."
        case .rateLimited: return "Rate limited. Retrying shortly."
        case .networkError: return "Network error. Cached data is still shown."
        case .serverError(let code, _): return "Server error (\(code))."
        case .noCredentials: return "Not connected."
        case .unknown: return "Something went wrong."
        }
    }

    /// Mirrors the message mapping in `SettingsViewModel.validateAndSaveKey`.
    public var validationMessage: String {
        switch self {
        case .unauthorized: return "Invalid session key."
        case .networkError: return "Network error. Check your connection."
        case .rateLimited: return "Rate limited. Try again later."
        case .serverError: return "Server error. Try again later."
        case .noCredentials: return "Validation failed."
        case .unknown(let message): return "Validation failed: \(message)"
        }
    }

    public static func fromHTTPStatus(_ code: Int, message: String? = nil) -> UsageError {
        switch code {
        case 401, 403: return .unauthorized
        case 429: return .rateLimited
        default: return .serverError(code: code, message: message)
        }
    }
}

/// A provider operation that failed with a human-readable reason. The Codex and
/// Grok repositories on Android surface `error("…")` strings straight into the
/// UI, so the iOS port keeps the same shape.
public struct ProviderError: Error, Equatable, Sendable, CustomStringConvertible {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var description: String { message }
    public var localizedDescription: String { message }
}
