import Foundation

/// Result of one refresh cycle across every configured provider.
public struct UsageRefreshResult: Sendable {
    public var claudeUsage: ClaudeUsage?
    public var claudeError: UsageError?
    public var codexUsage: CodexUsage?
    public var codexError: String?
    public var grokUsage: GrokUsage?
    public var grokError: String?
    public var history: [UsageHistoryPoint]
    public var insights: SessionGuardrailInsights
    public var notifications: [UsageNotificationRequest]

    public init(
        claudeUsage: ClaudeUsage? = nil,
        claudeError: UsageError? = nil,
        codexUsage: CodexUsage? = nil,
        codexError: String? = nil,
        grokUsage: GrokUsage? = nil,
        grokError: String? = nil,
        history: [UsageHistoryPoint] = [],
        insights: SessionGuardrailInsights = .empty(),
        notifications: [UsageNotificationRequest] = []
    ) {
        self.claudeUsage = claudeUsage
        self.claudeError = claudeError
        self.codexUsage = codexUsage
        self.codexError = codexError
        self.grokUsage = grokUsage
        self.grokError = grokError
        self.history = history
        self.insights = insights
        self.notifications = notifications
    }
}

/// Replaces `DashboardViewModel.fetchAndUpdate` + `UsageSyncWorker.doWork`:
/// one place that fetches every configured provider, evaluates guardrails and
/// returns the notifications that should be delivered. The SwiftUI view model
/// and the `BGAppRefreshTask` handler both call `refresh`.
public actor UsageRefreshService {
    private let claude: ClaudeUsageRepository
    private let codex: CodexUsageRepository
    private let grok: GrokUsageRepository
    private let credentials: CredentialStoring
    private let preferences: PreferencesStore
    private let history: UsageHistoryStore

    public init(
        claude: ClaudeUsageRepository,
        codex: CodexUsageRepository,
        grok: GrokUsageRepository,
        credentials: CredentialStoring,
        preferences: PreferencesStore,
        history: UsageHistoryStore
    ) {
        self.claude = claude
        self.codex = codex
        self.grok = grok
        self.credentials = credentials
        self.preferences = preferences
        self.history = history
    }

    public struct Configuration: Equatable, Sendable {
        public var isClaudeConfigured: Bool
        public var isCodexConfigured: Bool
        public var isGrokConfigured: Bool

        public var isAnyConfigured: Bool {
            isClaudeConfigured || isCodexConfigured || isGrokConfigured
        }

        public init(isClaudeConfigured: Bool, isCodexConfigured: Bool, isGrokConfigured: Bool) {
            self.isClaudeConfigured = isClaudeConfigured
            self.isCodexConfigured = isCodexConfigured
            self.isGrokConfigured = isGrokConfigured
        }
    }

    public func configuration() async -> Configuration {
        Configuration(
            isClaudeConfigured: credentials.sessionKey() != nil && credentials.orgId() != nil,
            isCodexConfigured: await codex.isAuthenticated,
            isGrokConfigured: await grok.isAuthenticated
        )
    }

    public func cachedSnapshot() async -> (ClaudeUsage?, CodexUsage?, GrokUsage?) {
        (await claude.cachedUsage(), await codex.cachedUsage(), await grok.cachedUsage())
    }

    public func refresh(urgent: Bool = false, now: Date = Date()) async -> UsageRefreshResult {
        let configuration = await configuration()
        let previousSessionUtilization = await claude.cachedUsage()?.fiveHour?.utilization

        var result = UsageRefreshResult()

        if configuration.isClaudeConfigured {
            do {
                result.claudeUsage = try await claude.fetchUsage(urgent: urgent, now: now)
            } catch let error as UsageError {
                result.claudeError = error
                result.claudeUsage = await claude.cachedUsage()
            } catch {
                result.claudeError = .unknown(message: "\(error)")
                result.claudeUsage = await claude.cachedUsage()
            }
        }

        if configuration.isCodexConfigured {
            do {
                result.codexUsage = try await codex.fetchWeekly(urgent: urgent, now: now)
            } catch {
                result.codexError = Self.message(from: error)
                result.codexUsage = await codex.cachedUsage()
            }
        }

        if configuration.isGrokConfigured {
            do {
                result.grokUsage = try await grok.fetchWeekly(urgent: urgent, now: now)
            } catch {
                result.grokError = Self.message(from: error)
                result.grokUsage = await grok.cachedUsage()
            }
        }

        result.history = history.history(now: now)
        result.insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: result.claudeUsage?.fiveHour,
            history: result.history,
            now: now
        )

        let plan = NotificationPlanner.plan(
            NotificationPlanInput(
                previousSessionUtilization: previousSessionUtilization,
                usage: result.claudeUsage,
                codexUsage: result.codexUsage,
                grokUsage: result.grokUsage,
                insights: result.insights,
                settings: preferences.notificationSettings,
                state: preferences.notificationState,
                now: now
            )
        )
        preferences.notificationState = plan.state
        result.notifications = plan.requests

        return result
    }

    public func clearAllData() async {
        credentials.clear()
        await claude.clearCachedData()
        await codex.disconnect()
        await grok.disconnect()
        preferences.clearAll()
    }

    static func message(from error: Error) -> String {
        if let provider = error as? ProviderError { return provider.message }
        if let usage = error as? UsageError { return usage.uiMessage }
        return "\(error)"
    }
}
