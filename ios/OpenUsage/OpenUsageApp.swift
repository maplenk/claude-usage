import OpenUsageCore
import SwiftUI

@main
struct OpenUsageApp: App {
    @State private var environment = AppEnvironment()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(environment: environment)
                .task {
                    // Only ask for notification permission once the user has
                    // actually connected a provider — otherwise the system alert
                    // lands on top of the onboarding screen before there is
                    // anything to be notified about.
                    let configuration = await environment.refreshService.configuration()
                    if configuration.isAnyConfigured,
                       environment.preferences.notifyOnUsageThresholds
                        || environment.preferences.notifyOnSessionReset {
                        await environment.notifications.requestAuthorizationIfNeeded()
                    }
                    BackgroundRefreshController.schedule(
                        preferredInterval: environment.preferences.refreshIntervalSeconds
                    )
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                BackgroundRefreshController.schedule(
                    preferredInterval: environment.preferences.refreshIntervalSeconds
                )
            }
        }
        // iOS runs this opportunistically — it cannot match Android's
        // WorkManager cadence. The task re-arms itself on every run.
        .backgroundTask(.appRefresh(BackgroundRefreshController.taskIdentifier)) {
            await handleBackgroundRefresh()
        }
    }

    @MainActor
    private func handleBackgroundRefresh() async {
        BackgroundRefreshController.schedule(
            preferredInterval: environment.preferences.refreshIntervalSeconds
        )
        await environment.performRefresh(urgent: true)
    }
}

struct RootView: View {
    let environment: AppEnvironment
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            DashboardView(
                viewModel: environment.dashboardViewModel,
                onOpenSettings: { showSettings = true }
            )
            .navigationDestination(isPresented: $showSettings) {
                SettingsView(viewModel: environment.settingsViewModel)
                    .onDisappear {
                        environment.dashboardViewModel.recheckConfiguration()
                    }
            }
        }
    }
}
