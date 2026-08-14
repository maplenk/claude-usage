import Foundation

/// Mirrors `CodexAuthTokens` in `data/local/SecureCredentialStore.kt`.
public struct CodexAuthTokens: Equatable, Sendable {
    public var accessToken: String
    public var refreshToken: String
    public var idToken: String?
    public var accountId: String?

    public init(accessToken: String, refreshToken: String, idToken: String?, accountId: String?) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.idToken = idToken
        self.accountId = accountId
    }
}

/// Mirrors `GrokAuthTokens` in `data/local/SecureCredentialStore.kt`.
public struct GrokAuthTokens: Equatable, Sendable {
    public var accessToken: String
    public var refreshToken: String
    public var idToken: String?
    public var expiresAt: Date

    public init(accessToken: String, refreshToken: String, idToken: String?, expiresAt: Date) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.idToken = idToken
        self.expiresAt = expiresAt
    }
}

/// The iOS analogue of `SecureCredentialStore` (EncryptedSharedPreferences).
/// The concrete Keychain implementation lives in the app target so that this
/// package stays Foundation-only; a future widget extension supplies its own
/// copy backed by its own keychain item.
///
/// Implementations must never log any value passed through this protocol.
public protocol CredentialStoring: AnyObject {
    func sessionKey() -> String?
    func saveSessionKey(_ key: String)

    func orgId() -> String?
    func saveOrgId(_ orgId: String)

    func codexTokens() -> CodexAuthTokens?
    func saveCodexTokens(_ tokens: CodexAuthTokens)
    func clearCodexTokens()

    func grokTokens() -> GrokAuthTokens?
    func saveGrokTokens(_ tokens: GrokAuthTokens)
    func clearGrokTokens()

    func clear()
}

/// Non-persistent implementation used by unit tests and SwiftUI previews.
public final class InMemoryCredentialStore: CredentialStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var _sessionKey: String?
    private var _orgId: String?
    private var _codex: CodexAuthTokens?
    private var _grok: GrokAuthTokens?

    public init(
        sessionKey: String? = nil,
        orgId: String? = nil,
        codexTokens: CodexAuthTokens? = nil,
        grokTokens: GrokAuthTokens? = nil
    ) {
        self._sessionKey = sessionKey
        self._orgId = orgId
        self._codex = codexTokens
        self._grok = grokTokens
    }

    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    public func sessionKey() -> String? { withLock { _sessionKey } }
    public func saveSessionKey(_ key: String) { withLock { _sessionKey = key } }
    public func orgId() -> String? { withLock { _orgId } }
    public func saveOrgId(_ orgId: String) { withLock { _orgId = orgId } }
    public func codexTokens() -> CodexAuthTokens? { withLock { _codex } }
    public func saveCodexTokens(_ tokens: CodexAuthTokens) { withLock { _codex = tokens } }
    public func clearCodexTokens() { withLock { _codex = nil } }
    public func grokTokens() -> GrokAuthTokens? { withLock { _grok } }
    public func saveGrokTokens(_ tokens: GrokAuthTokens) { withLock { _grok = tokens } }
    public func clearGrokTokens() { withLock { _grok = nil } }

    public func clear() {
        withLock {
            _sessionKey = nil
            _orgId = nil
            _codex = nil
            _grok = nil
        }
    }
}
