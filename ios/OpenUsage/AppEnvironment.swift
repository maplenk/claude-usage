import Foundation
import OpenUsageCore
import SwiftUI

/// Composition root — the hand-rolled stand-in for Hilt's `AppModule` /
/// `NetworkModule`. Everything is constructed once and shared.
@MainActor
final class AppEnvironment {
    let credentials: CredentialStoring
    let preferences: PreferencesStore
    let cache: UsageCacheStore
    let history: UsageHistoryStore
    let claudeRepository: ClaudeUsageRepository
    let codexRepository: CodexUsageRepository
    let grokRepository: GrokUsageRepository
    let refreshService: UsageRefreshService
    let notifications: NotificationScheduler

    static let appVersion: String =
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.7.0"

    init(
        credentials: CredentialStoring = KeychainCredentialStore(),
        defaults: UserDefaults = .standard,
        historyDirectory: URL = UsageHistoryStore.defaultDirectory(),
        transport: HTTPTransport = URLSessionTransport.makeDefault()
    ) {
        self.credentials = credentials
        self.preferences = PreferencesStore(defaults: defaults)
        self.cache = UsageCacheStore(defaults: defaults)
        self.history = UsageHistoryStore(directory: historyDirectory)
        self.notifications = NotificationScheduler()

        self.claudeRepository = ClaudeUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: cache,
            history: history
        )
        self.codexRepository = CodexUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: cache
        )
        self.grokRepository = GrokUsageRepository(
            transport: transport,
            credentials: credentials,
            cache: cache,
            clientVersion: Self.appVersion
        )
        self.refreshService = UsageRefreshService(
            claude: claudeRepository,
            codex: codexRepository,
            grok: grokRepository,
            credentials: credentials,
            preferences: preferences,
            history: history
        )
    }

    private(set) lazy var dashboardViewModel = DashboardViewModel(environment: self)
    private(set) lazy var settingsViewModel = SettingsViewModel(environment: self)

    /// Runs one refresh and delivers whatever notifications it produced. Shared
    /// by the foreground poll loop and the background refresh task.
    @discardableResult
    func performRefresh(urgent: Bool) async -> UsageRefreshResult {
        let result = await refreshService.refresh(urgent: urgent)
        await notifications.deliver(result.notifications)
        return result
    }
}
