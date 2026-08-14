import OpenUsageCore
import SwiftUI

/// Port of `ui/settings/SettingsScreen.kt`.
struct SettingsView: View {
    private static let refreshIntervalRange: ClosedRange<Double> =
        Double(PreferencesStore.minimumRefreshIntervalSeconds)...Double(PreferencesStore.maximumRefreshIntervalSeconds)

    @Bindable var viewModel: SettingsViewModel

    @State private var showClearDataConfirmation = false
    @State private var showClearHistoryConfirmation = false
    @State private var showAdvanced = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            claudeSection
            codexSection
            grokSection
            if !viewModel.organizations.isEmpty {
                organizationSection
            }
            refreshAndAlertsSection
            displaySection
            advancedSection
            dangerZoneSection
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.loadExistingSettings()
            viewModel.reloadOrganizationsIfNeeded()
        }
        .confirmationDialog(
            "Clear all data?",
            isPresented: $showClearDataConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear", role: .destructive) { viewModel.clearAllData() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will remove your session key, selected organization, cached usage data, and usage history. You will need to reconfigure the app.")
        }
        .confirmationDialog(
            "Clear usage history?",
            isPresented: $showClearHistoryConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear", role: .destructive) { viewModel.clearUsageHistory() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes stored session baseline and prediction history. New history will start collecting on the next refresh.")
        }
    }

    // MARK: - Claude

    private var claudeSection: some View {
        Section("Claude account") {
            if let masked = viewModel.maskedSessionKey {
                LabeledContent("Current key", value: masked)
                    .font(.footnote)
            }
            SecureField("sk-ant-sid01-…", text: $viewModel.sessionKeyInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if let error = viewModel.validationError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(OpenUsageColor.statusCritical)
            }
            Button {
                Task { await viewModel.validateAndSaveKey() }
            } label: {
                if viewModel.isValidating {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Validating…")
                    }
                } else {
                    Text(viewModel.isKeyValidated ? "Replace session key" : "Validate & save key")
                }
            }
            .disabled(viewModel.isValidating || viewModel.sessionKeyInput.isEmpty)

            DisclosureGroup("How do I find my session key?") {
                Text("""
                1. Open claude.ai in a browser
                2. Open Developer Tools
                3. Go to Application → Cookies
                4. Find the "sessionKey" cookie
                5. Copy its value (starts with sk-ant-sid01-)
                """)
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Codex

    private var codexSection: some View {
        Section("Codex account") {
            if viewModel.isCodexConnected {
                Text("Connected for weekly usage").font(.subheadline)
                Text("The phone refreshes Codex directly; tokens stay in the iOS Keychain.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Disconnect Codex", role: .destructive, action: viewModel.disconnectCodex)
            } else if let code = viewModel.codexDeviceCode {
                Text("Enter this one-time code to connect Codex:").font(.footnote)
                Text(code.userCode)
                    .font(.system(.title2, design: .monospaced).weight(.bold))
                    .textSelection(.enabled)
                Button("Copy code & open sign-in") {
                    UIPasteboard.general.string = code.userCode
                    if let url = URL(string: code.verificationUrl) { openURL(url) }
                }
                Button("Cancel", role: .cancel, action: viewModel.cancelCodexConnection)
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Waiting for authorization…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("Connect with a one-time device code. Tokens stay encrypted on this phone.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button(viewModel.isCodexConnecting ? "Starting…" : "Connect Codex", action: viewModel.connectCodex)
                    .disabled(viewModel.isCodexConnecting)
                if let error = viewModel.codexSignInError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(OpenUsageColor.statusCritical)
                }
            }
        }
    }

    // MARK: - Grok

    private var grokSection: some View {
        Section("Grok account") {
            if viewModel.isGrokConnected {
                Text("Connected for weekly unified-billing usage").font(.subheadline)
                Text("OpenUsage refreshes directly from xAI. Access and refresh tokens stay in the Keychain.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Disconnect Grok", role: .destructive, action: viewModel.disconnectGrok)
            } else if let code = viewModel.grokDeviceCode {
                Text("Confirm this one-time code with xAI:").font(.footnote)
                Text(code.userCode)
                    .font(.system(.title2, design: .monospaced).weight(.bold))
                    .textSelection(.enabled)
                Button("Copy code & open xAI") {
                    UIPasteboard.general.string = code.userCode
                    let target = code.verificationUrlComplete ?? code.verificationUrl
                    if let url = URL(string: target) { openURL(url) }
                }
                Button("Cancel", role: .cancel, action: viewModel.cancelGrokConnection)
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Waiting for xAI authorization…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("Connect with xAI's device-code flow. No computer or local server is required after sign-in.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button(viewModel.isGrokConnecting ? "Starting…" : "Connect Grok", action: viewModel.connectGrok)
                    .disabled(viewModel.isGrokConnecting)
                if let error = viewModel.grokSignInError {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(OpenUsageColor.statusCritical)
                }
            }
        }
    }

    // MARK: - Organization

    private var organizationSection: some View {
        Section("Organization") {
            ForEach(viewModel.organizations) { org in
                Button {
                    viewModel.selectOrganization(org)
                } label: {
                    HStack {
                        Text(org.name).foregroundStyle(.primary)
                        Spacer()
                        if viewModel.selectedOrgId == org.uuid {
                            Image(systemName: "checkmark")
                                .foregroundStyle(OpenUsageColor.claude)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Refresh & alerts

    private var refreshAndAlertsSection: some View {
        Section("Refresh & alerts") {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Refresh interval")
                    Spacer()
                    Text("\(viewModel.refreshInterval)s")
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
                Slider(
                    value: Binding(
                        get: { Double(viewModel.refreshInterval) },
                        set: { viewModel.updateRefreshInterval(Int($0.rounded())) }
                    ),
                    in: Self.refreshIntervalRange,
                    step: 5
                )
                Text("Applies while the app is open. iOS decides when background refreshes actually run.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Toggle(
                "Notify when session resets",
                isOn: Binding(
                    get: { viewModel.notifyOnReset },
                    set: viewModel.toggleNotifyOnReset
                )
            )

            Toggle(
                "Notify on usage milestones",
                isOn: Binding(
                    get: { viewModel.notifyOnUsageThresholds },
                    set: viewModel.toggleNotifyOnUsageThresholds
                )
            )

            Text("Session alerts at 75/80/85/90/100%. Weekly alerts at 70/80/90/100% for Claude weekly, Claude weekly Opus, Codex weekly and Grok weekly.")
                .font(.caption)
                .foregroundStyle(.secondary)

            if !viewModel.notificationsAuthorized {
                Button("Allow notifications", action: viewModel.requestNotificationAuthorization)
            }
        }
    }

    // MARK: - Display

    private var displaySection: some View {
        Section("Display") {
            Toggle(
                isOn: Binding(
                    get: { viewModel.useRelativeTime },
                    set: viewModel.toggleUseRelativeTime
                )
            ) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Relative countdown")
                    Text("Show \"2h 34m\" instead of a clock time")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    // MARK: - Advanced

    private var advancedSection: some View {
        Section("Advanced") {
            DisclosureGroup("Maintenance tools", isExpanded: $showAdvanced) {
                Text("Usage history powers pace baselines and cap predictions (retained for up to 30 days).")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Clear usage history", role: .destructive) {
                    showClearHistoryConfirmation = true
                }
            }
        }
    }

    // MARK: - Danger zone

    private var dangerZoneSection: some View {
        Section("Danger zone") {
            Text("Clear all local data and reset app configuration.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button("Clear all data", role: .destructive) {
                showClearDataConfirmation = true
            }
        }
    }
}
