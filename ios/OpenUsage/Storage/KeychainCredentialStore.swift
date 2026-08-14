import Foundation
import OpenUsageCore
import Security

/// iOS analogue of Android's `SecureCredentialStore` (EncryptedSharedPreferences
/// backed by the Android Keystore).
///
/// Everything sensitive — the Claude session key and every OAuth token — lives
/// in the Keychain with `kSecAttrAccessibleAfterFirstUnlock` so a background
/// refresh can still read it while the device is locked. No value is ever
/// logged, and the store is deliberately the only place that touches them.
///
/// A future widget extension gets its own instance with a different
/// `serviceName`, because a free personal Apple team cannot use App Groups or
/// Keychain sharing groups.
final class KeychainCredentialStore: CredentialStoring {
    private enum Key {
        static let sessionKey = "session_key"
        static let orgId = "selected_org_id"
        static let codexAccessToken = "codex_access_token"
        static let codexRefreshToken = "codex_refresh_token"
        static let codexIdToken = "codex_id_token"
        static let codexAccountId = "codex_account_id"
        static let grokAccessToken = "grok_access_token"
        static let grokRefreshToken = "grok_refresh_token"
        static let grokIdToken = "grok_id_token"
        static let grokExpiresAt = "grok_expires_at"

        static let all = [
            sessionKey, orgId,
            codexAccessToken, codexRefreshToken, codexIdToken, codexAccountId,
            grokAccessToken, grokRefreshToken, grokIdToken, grokExpiresAt,
        ]
    }

    private let serviceName: String

    init(serviceName: String = "com.qbapps.claudeusage.credentials") {
        self.serviceName = serviceName
    }

    // MARK: - Claude

    func sessionKey() -> String? { read(Key.sessionKey) }

    func saveSessionKey(_ key: String) { write(key, for: Key.sessionKey) }

    func orgId() -> String? { read(Key.orgId) }

    func saveOrgId(_ orgId: String) { write(orgId, for: Key.orgId) }

    // MARK: - Codex

    func codexTokens() -> CodexAuthTokens? {
        guard let accessToken = read(Key.codexAccessToken), !accessToken.isEmpty,
              let refreshToken = read(Key.codexRefreshToken), !refreshToken.isEmpty else {
            return nil
        }
        return CodexAuthTokens(
            accessToken: accessToken,
            refreshToken: refreshToken,
            idToken: read(Key.codexIdToken),
            accountId: read(Key.codexAccountId)
        )
    }

    func saveCodexTokens(_ tokens: CodexAuthTokens) {
        write(tokens.accessToken, for: Key.codexAccessToken)
        write(tokens.refreshToken, for: Key.codexRefreshToken)
        write(tokens.idToken, for: Key.codexIdToken)
        write(tokens.accountId, for: Key.codexAccountId)
    }

    func clearCodexTokens() {
        [Key.codexAccessToken, Key.codexRefreshToken, Key.codexIdToken, Key.codexAccountId]
            .forEach(delete)
    }

    // MARK: - Grok

    func grokTokens() -> GrokAuthTokens? {
        guard let accessToken = read(Key.grokAccessToken), !accessToken.isEmpty,
              let refreshToken = read(Key.grokRefreshToken), !refreshToken.isEmpty else {
            return nil
        }
        let expiresAt = read(Key.grokExpiresAt)
            .flatMap(Double.init)
            .map(Date.init(timeIntervalSince1970:))
            ?? Date.distantFuture
        return GrokAuthTokens(
            accessToken: accessToken,
            refreshToken: refreshToken,
            idToken: read(Key.grokIdToken),
            expiresAt: expiresAt
        )
    }

    func saveGrokTokens(_ tokens: GrokAuthTokens) {
        write(tokens.accessToken, for: Key.grokAccessToken)
        write(tokens.refreshToken, for: Key.grokRefreshToken)
        write(tokens.idToken, for: Key.grokIdToken)
        write(String(tokens.expiresAt.timeIntervalSince1970), for: Key.grokExpiresAt)
    }

    func clearGrokTokens() {
        [Key.grokAccessToken, Key.grokRefreshToken, Key.grokIdToken, Key.grokExpiresAt]
            .forEach(delete)
    }

    func clear() {
        Key.all.forEach(delete)
    }

    // MARK: - Keychain primitives

    private func baseQuery(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(_ account: String) -> String? {
        var query = baseQuery(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String?, for account: String) {
        guard let value else {
            delete(account)
            return
        }
        let data = Data(value.utf8)
        let query = baseQuery(account)

        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]

        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    private func delete(_ account: String) {
        SecItemDelete(baseQuery(account) as CFDictionary)
    }
}
