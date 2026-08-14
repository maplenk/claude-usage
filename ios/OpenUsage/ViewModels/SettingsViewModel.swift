import Foundation
import Observation
import OpenUsageCore

/// Port of `ui/settings/SettingsViewModel.kt`.
@MainActor
@Observable
final class SettingsViewModel {
    // Claude
    var sessionKeyInput = ""
    private(set) var maskedSessionKey: String?
    private(set) var organizations: [Organization] = []
    private(set) var selectedOrgId: String?
    private(set) var isValidating = false
    private(set) var validationError: String?
    private(set) var isKeyValidated = false

    // Codex
    private(set) var isCodexConnected = false
    private(set) var isCodexConnecting = false
    private(set) var codexDeviceCode: CodexDeviceCode?
    private(set) var codexSignInError: String?

    // Grok
    private(set) var isGrokConnected = false
    private(set) var isGrokConnecting = false
    private(set) var grokDeviceCode: GrokDeviceCode?
    private(set) var grokSignInError: String?

    // Preferences
    var refreshInterval = PreferencesStore.defaultRefreshIntervalSeconds
    var notifyOnReset = true
    var notifyOnUsageThresholds = true
    var useRelativeTime = true

    private(set) var notificationsAuthorized = true

    private let environment: AppEnvironment
    private var codexLoginTask: Task<Void, Never>?
    private var grokLoginTask: Task<Void, Never>?

    init(environment: AppEnvironment) {
        self.environment = environment
        loadExistingSettings()
    }

    // MARK: - Claude session key

    func validateAndSaveKey() async {
        let key = sessionKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        guard key.hasPrefix("sk-ant-") else {
            validationError = "Key must start with sk-ant-"
            return
        }
        guard key.count >= 40 else {
            validationError = "Key appears too short"
            return
        }

        isValidating = true
        validationError = nil

        do {
            let orgs = try await environment.claudeRepository.validateSessionKey(key)
            environment.credentials.saveSessionKey(key)
            isValidating = false
            maskedSessionKey = Formatters.maskKey(key)
            organizations = orgs
            isKeyValidated = true
            validationError = nil
            sessionKeyInput = ""
            if orgs.count == 1, let only = orgs.first {
                selectOrganization(only)
            }
        } catch let error as UsageError {
            isValidating = false
            validationError = error.validationMessage
        } catch {
            isValidating = false
            validationError = "Validation failed: \(error.localizedDescription)"
        }
    }

    func selectOrganization(_ org: Organization) {
        environment.credentials.saveOrgId(org.uuid)
        environment.preferences.selectedOrgId = org.uuid
        selectedOrgId = org.uuid
        BackgroundRefreshController.schedule(preferredInterval: refreshInterval)
    }

    // MARK: - Codex

    func connectCodex() {
        codexLoginTask?.cancel()
        isCodexConnecting = true
        codexDeviceCode = nil
        codexSignInError = nil

        codexLoginTask = Task { [weak self] in
            guard let self else { return }
            do {
                let code = try await self.environment.codexRepository.startDeviceLogin()
                self.codexDeviceCode = code
                _ = try await self.environment.codexRepository.completeDeviceLogin(code)
                self.isCodexConnected = true
                self.isCodexConnecting = false
                self.codexDeviceCode = nil
                self.codexSignInError = nil
                BackgroundRefreshController.schedule(preferredInterval: self.refreshInterval)
            } catch is CancellationError {
                self.isCodexConnecting = false
                self.codexDeviceCode = nil
            } catch {
                self.isCodexConnecting = false
                self.codexDeviceCode = nil
                self.codexSignInError = Self.message(from: error, fallback: "Codex sign-in failed.")
            }
        }
    }

    func cancelCodexConnection() {
        codexLoginTask?.cancel()
        codexLoginTask = nil
        isCodexConnecting = false
        codexDeviceCode = nil
        codexSignInError = nil
    }

    func disconnectCodex() {
        codexLoginTask?.cancel()
        Task { [weak self] in
            guard let self else { return }
            await self.environment.codexRepository.disconnect()
            self.isCodexConnected = false
            self.isCodexConnecting = false
            self.codexDeviceCode = nil
            self.codexSignInError = nil
        }
    }

    // MARK: - Grok

    func connectGrok() {
        grokLoginTask?.cancel()
        isGrokConnecting = true
        grokDeviceCode = nil
        grokSignInError = nil

        grokLoginTask = Task { [weak self] in
            guard let self else { return }
            do {
                let code = try await self.environment.grokRepository.startDeviceLogin()
                self.grokDeviceCode = code
                _ = try await self.environment.grokRepository.completeDeviceLogin(code)
                self.isGrokConnected = true
                self.isGrokConnecting = false
                self.grokDeviceCode = nil
                self.grokSignInError = nil
                BackgroundRefreshController.schedule(preferredInterval: self.refreshInterval)
            } catch is CancellationError {
                self.isGrokConnecting = false
                self.grokDeviceCode = nil
            } catch {
                self.isGrokConnecting = false
                self.grokDeviceCode = nil
                self.grokSignInError = Self.message(from: error, fallback: "Grok sign-in failed.")
            }
        }
    }

    func cancelGrokConnection() {
        grokLoginTask?.cancel()
        grokLoginTask = nil
        isGrokConnecting = false
        grokDeviceCode = nil
        grokSignInError = nil
    }

    func disconnectGrok() {
        grokLoginTask?.cancel()
        Task { [weak self] in
            guard let self else { return }
            await self.environment.grokRepository.disconnect()
            self.isGrokConnected = false
            self.isGrokConnecting = false
            self.grokDeviceCode = nil
            self.grokSignInError = nil
        }
    }

    // MARK: - Preferences

    func updateRefreshInterval(_ seconds: Int) {
        refreshInterval = seconds
        environment.preferences.refreshIntervalSeconds = seconds
        BackgroundRefreshController.schedule(preferredInterval: seconds)
    }

    func toggleNotifyOnReset(_ enabled: Bool) {
        notifyOnReset = enabled
        environment.preferences.notifyOnSessionReset = enabled
        if enabled { requestNotificationAuthorization() }
    }

    func toggleNotifyOnUsageThresholds(_ enabled: Bool) {
        notifyOnUsageThresholds = enabled
        environment.preferences.notifyOnUsageThresholds = enabled
        if enabled { requestNotificationAuthorization() }
    }

    func toggleUseRelativeTime(_ enabled: Bool) {
        useRelativeTime = enabled
        environment.preferences.useRelativeTime = enabled
    }

    func requestNotificationAuthorization() {
        Task { [weak self] in
            guard let self else { return }
            let granted = await self.environment.notifications.requestAuthorizationIfNeeded()
            self.notificationsAuthorized = granted
        }
    }

    // MARK: - Destructive actions

    func clearUsageHistory() {
        Task { [weak self] in
            await self?.environment.claudeRepository.clearUsageHistory()
        }
    }

    func clearAllData() {
        codexLoginTask?.cancel()
        grokLoginTask?.cancel()
        BackgroundRefreshController.cancelAll()
        environment.notifications.removeAllDelivered()

        Task { [weak self] in
            guard let self else { return }
            await self.environment.refreshService.clearAllData()
            self.sessionKeyInput = ""
            self.maskedSessionKey = nil
            self.organizations = []
            self.selectedOrgId = nil
            self.isKeyValidated = false
            self.validationError = nil
            self.isCodexConnected = false
            self.isGrokConnected = false
            self.codexDeviceCode = nil
            self.grokDeviceCode = nil
            self.loadExistingSettings()
        }
    }

    // MARK: - Internals

    func loadExistingSettings() {
        let existingKey = environment.credentials.sessionKey()
        maskedSessionKey = existingKey.map(Formatters.maskKey)
        isKeyValidated = existingKey != nil
        selectedOrgId = environment.credentials.orgId()

        refreshInterval = environment.preferences.refreshIntervalSeconds
        notifyOnReset = environment.preferences.notifyOnSessionReset
        notifyOnUsageThresholds = environment.preferences.notifyOnUsageThresholds
        useRelativeTime = environment.preferences.useRelativeTime

        Task { [weak self] in
            guard let self else { return }
            self.isCodexConnected = await self.environment.codexRepository.isAuthenticated
            self.isGrokConnected = await self.environment.grokRepository.isAuthenticated
            self.notificationsAuthorized = await self.environment.notifications.isAuthorized()
        }
    }

    /// Refreshes the organization list for an already-saved key so the picker is
    /// populated when Settings is revisited.
    func reloadOrganizationsIfNeeded() {
        guard organizations.isEmpty, let key = environment.credentials.sessionKey() else { return }
        Task { [weak self] in
            guard let self else { return }
            if let orgs = try? await self.environment.claudeRepository.fetchOrganizations(sessionKey: key) {
                self.organizations = orgs
            }
        }
    }

    private static func message(from error: Error, fallback: String) -> String {
        if let provider = error as? ProviderError { return provider.message }
        if let usage = error as? UsageError { return usage.uiMessage }
        return fallback
    }
}
