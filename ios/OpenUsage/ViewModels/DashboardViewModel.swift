import Foundation
import Observation
import OpenUsageCore

/// Port of `ui/dashboard/DashboardViewModel.kt`.
@MainActor
@Observable
final class DashboardViewModel {
    private(set) var usage: ClaudeUsage?
    private(set) var codexUsage: CodexUsage?
    private(set) var grokUsage: GrokUsage?
    private(set) var history: [UsageHistoryPoint] = []
    private(set) var insights: SessionGuardrailInsights = .empty()

    private(set) var isLoading = false
    private(set) var error: UsageError?
    private(set) var codexError: String?
    private(set) var grokError: String?

    private(set) var isClaudeConfigured = false
    private(set) var isCodexConfigured = false
    private(set) var isGrokConfigured = false
    var isConfigured: Bool { isClaudeConfigured || isCodexConfigured || isGrokConfigured }

    private(set) var syncState: SyncState = .fresh(fetchedAt: nil, ageMinutes: nil)
    private(set) var refreshIntervalSeconds = PreferencesStore.defaultRefreshIntervalSeconds
    private(set) var useRelativeTime = true

    private let environment: AppEnvironment
    private let networkMonitor = NetworkMonitor()
    private var pollTask: Task<Void, Never>?
    private var tickTask: Task<Void, Never>?

    init(environment: AppEnvironment) {
        self.environment = environment
        loadPreferences()
        loadCachedSnapshot()
        networkMonitor.onReconnect = { [weak self] in
            guard let self, self.isConfigured else { return }
            Task { await self.fetchAndUpdate(urgent: true) }
        }
        networkMonitor.start()
    }

    // The poll and tick tasks are torn down in `onDisappear`; a `deinit` cannot
    // touch main-actor state, and this view model lives for the app's lifetime.

    // MARK: - Lifecycle

    func onAppear() {
        startPolling()
        startSyncStateTicker()
    }

    func onDisappear() {
        pollTask?.cancel()
        pollTask = nil
        tickTask?.cancel()
        tickTask = nil
    }

    /// Pull-to-refresh.
    func refresh() async {
        isLoading = true
        error = nil
        await fetchAndUpdate(urgent: true)
    }

    /// Re-evaluates whether credentials are configured, e.g. after Settings.
    /// A provider that was just connected should be fetched straight away
    /// rather than waiting out the poll interval.
    func recheckConfiguration() {
        Task { [weak self] in
            guard let self else { return }
            let wasConfigured = self.isConfigured
            await self.refreshConfiguration()
            if !wasConfigured, self.isConfigured {
                await self.fetchAndUpdate(urgent: true)
            }
        }
    }

    private func refreshConfiguration() async {
        let configuration = await environment.refreshService.configuration()
        isClaudeConfigured = configuration.isClaudeConfigured
        isCodexConfigured = configuration.isCodexConfigured
        isGrokConfigured = configuration.isGrokConfigured
        loadPreferences()
        updateSyncState()
    }

    // MARK: - Internals

    private func loadPreferences() {
        refreshIntervalSeconds = environment.preferences.refreshIntervalSeconds
        useRelativeTime = environment.preferences.useRelativeTime
    }

    private func loadCachedSnapshot() {
        usage = environment.cache.claudeUsage
        codexUsage = environment.cache.codexUsage
        grokUsage = environment.cache.grokUsage
        history = environment.history.history()
        insights = SessionGuardrailEvaluator.evaluate(
            currentMetric: usage?.fiveHour,
            history: history
        )
        updateSyncState()
    }

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task { [weak self] in
            guard let self else { return }
            // Resolve configuration *before* the first iteration, otherwise a
            // cold start skips its first fetch and the dashboard sits empty for
            // a whole refresh interval.
            await self.refreshConfiguration()
            while !Task.isCancelled {
                if self.isConfigured {
                    await self.fetchAndUpdate(urgent: false)
                }
                let interval = self.refreshIntervalSeconds
                try? await Task.sleep(nanoseconds: UInt64(max(interval, 1)) * 1_000_000_000)
            }
        }
    }

    /// Keeps the "last sync" copy honest while the screen sits idle.
    private func startSyncStateTicker() {
        tickTask?.cancel()
        tickTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
                guard let self, !Task.isCancelled else { return }
                self.updateSyncState()
            }
        }
    }

    private func fetchAndUpdate(urgent: Bool) async {
        guard isConfigured else {
            isLoading = false
            return
        }
        guard networkMonitor.isOnline else {
            isLoading = false
            updateSyncState()
            return
        }
        isLoading = true
        error = nil
        codexError = nil
        grokError = nil

        let result = await environment.performRefresh(urgent: urgent)

        // A throttled non-urgent call is not a user-visible failure.
        if case .rateLimited = result.claudeError, !urgent {
            error = nil
        } else {
            error = result.claudeError
        }
        usage = result.claudeUsage ?? usage
        codexUsage = result.codexUsage ?? codexUsage
        grokUsage = result.grokUsage ?? grokUsage
        codexError = result.codexError
        grokError = result.grokError
        history = result.history
        insights = result.insights
        isLoading = false
        loadPreferences()
        updateSyncState()
    }

    private func updateSyncState() {
        let candidates = [
            isClaudeConfigured ? usage?.fetchedAt : nil,
            isCodexConfigured ? codexUsage?.fetchedAt : nil,
            isGrokConfigured ? grokUsage?.fetchedAt : nil,
        ].compactMap { $0 }
        syncState = SyncState.make(fetchedAt: candidates.min(), isOnline: networkMonitor.isOnline)
    }
}
