import Foundation

/// Mirrors `UsageDataStore`, `CodexUsageDataStore` and `GrokUsageDataStore`.
/// Cached values let the dashboard render immediately while a refresh runs, and
/// they survive a cold launch exactly like the DataStore caches on Android.
public final class UsageCacheStore: @unchecked Sendable {
    private enum Key {
        static let claude = "cache_claude_usage_v1"
        static let codex = "cache_codex_usage_v1"
        static let grok = "cache_grok_usage_v1"
    }

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: - Claude

    public var claudeUsage: ClaudeUsage? {
        get { decode(ClaudeUsage.self, forKey: Key.claude) }
        set { encode(newValue, forKey: Key.claude) }
    }

    // MARK: - Codex

    public var codexUsage: CodexUsage? {
        get { decode(CodexUsage.self, forKey: Key.codex) }
        set { encode(newValue, forKey: Key.codex) }
    }

    // MARK: - Grok

    public var grokUsage: GrokUsage? {
        get { decode(GrokUsage.self, forKey: Key.grok) }
        set { encode(newValue, forKey: Key.grok) }
    }

    public func clearClaude() { defaults.removeObject(forKey: Key.claude) }
    public func clearCodex() { defaults.removeObject(forKey: Key.codex) }
    public func clearGrok() { defaults.removeObject(forKey: Key.grok) }

    public func clearAll() {
        clearClaude()
        clearCodex()
        clearGrok()
    }

    // MARK: - Private

    private func decode<T: Decodable>(_ type: T.Type, forKey key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private func encode<T: Encodable>(_ value: T?, forKey key: String) {
        guard let value else {
            defaults.removeObject(forKey: key)
            return
        }
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: key)
    }
}
