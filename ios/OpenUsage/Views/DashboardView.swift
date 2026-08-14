import OpenUsageCore
import SwiftUI

/// Port of `ui/dashboard/DashboardScreen.kt`.
struct DashboardView: View {
    let viewModel: DashboardViewModel
    let onOpenSettings: () -> Void

    var body: some View {
        Group {
            if viewModel.isConfigured {
                configuredContent
            } else {
                NotConfiguredView(onOpenSettings: onOpenSettings)
            }
        }
        .background(OpenUsageColor.screenBackground)
        .navigationTitle("OpenUsage")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if viewModel.isConfigured {
                    Button {
                        Task { await viewModel.refresh() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(viewModel.isLoading || viewModel.syncState.isOffline)
                    .accessibilityLabel("Refresh")
                }
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Settings")
            }
        }
        .onAppear { viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
    }

    private var configuredContent: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if !viewModel.syncState.isFresh {
                    SyncStateBanner(syncState: viewModel.syncState) {
                        Task { await viewModel.refresh() }
                    }
                }

                if viewModel.isClaudeConfigured {
                    if let error = viewModel.error {
                        ProviderErrorBanner(
                            provider: "Claude",
                            message: error.uiMessage,
                            onRetry: { Task { await viewModel.refresh() } },
                            onOpenSettings: onOpenSettings
                        )
                    }
                    SessionHeroCard(
                        metric: viewModel.usage?.fiveHour,
                        weeklyMetric: viewModel.usage?.sevenDay,
                        useRelativeTime: viewModel.useRelativeTime,
                        isStale: viewModel.syncState.isStale
                    )
                    if !viewModel.syncState.isStale {
                        SessionGuardrailCard(
                            metric: viewModel.usage?.fiveHour,
                            insights: viewModel.insights
                        )
                    }
                    ClaudeSecondaryLimits(
                        opus: viewModel.usage?.sevenDayOpus,
                        sonnet: viewModel.usage?.sevenDaySonnet,
                        useRelativeTime: viewModel.useRelativeTime,
                        isStale: viewModel.syncState.isStale
                    )
                }

                if viewModel.isCodexConfigured {
                    if let error = viewModel.codexError {
                        ProviderErrorBanner(
                            provider: "Codex",
                            message: error,
                            onRetry: { Task { await viewModel.refresh() } },
                            onOpenSettings: onOpenSettings
                        )
                    }
                    ProviderWeeklyCard(
                        brand: .codex,
                        label: "CODEX · WEEKLY",
                        metric: viewModel.codexUsage?.weekly,
                        useRelativeTime: viewModel.useRelativeTime,
                        isStale: viewModel.syncState.isStale
                    )
                }

                if viewModel.isGrokConfigured {
                    if let error = viewModel.grokError {
                        ProviderErrorBanner(
                            provider: "Grok",
                            message: error,
                            onRetry: { Task { await viewModel.refresh() } },
                            onOpenSettings: onOpenSettings
                        )
                    }
                    ProviderWeeklyCard(
                        brand: .grok,
                        label: "GROK · WEEKLY",
                        metric: viewModel.grokUsage?.weekly,
                        useRelativeTime: viewModel.useRelativeTime,
                        isStale: viewModel.syncState.isStale
                    )
                }

                DashboardMetaRow(
                    syncState: viewModel.syncState,
                    refreshIntervalSeconds: viewModel.refreshIntervalSeconds,
                    isRefreshing: viewModel.isLoading
                )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .refreshable {
            guard !viewModel.syncState.isOffline else { return }
            await viewModel.refresh()
        }
    }
}

/// The Opus and Sonnet weekly sub-limits. Android surfaces these through the
/// widget and the compact metric card; on iOS they live in one compact tile.
struct ClaudeSecondaryLimits: View {
    let opus: UsageMetric?
    let sonnet: UsageMetric?
    let useRelativeTime: Bool
    let isStale: Bool

    var body: some View {
        if opus != nil || sonnet != nil {
            UsageCardContainer {
                VStack(alignment: .leading, spacing: 14) {
                    ProviderLabel(brand: .claude, text: "CLAUDE · MODEL LIMITS")
                    if let opus {
                        row(title: "Weekly Opus", metric: opus)
                    }
                    if opus != nil && sonnet != nil {
                        CardDivider()
                    }
                    if let sonnet {
                        row(title: "Weekly Sonnet", metric: sonnet)
                    }
                }
            }
        }
    }

    private func row(title: String, metric: UsageMetric) -> some View {
        let status = HeadroomStatus.of(metric, isStale: isStale)
        let utilization = min(max(metric.effectiveUtilization(), 0), 100)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title).font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(Int(utilization.rounded()))%")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                HeadroomStatusChip(status: status)
            }
            UsageIndicator(
                progress: utilization / 100,
                color: status.foreground,
                height: 8,
                isStale: isStale
            )
            Text("Resets \(Formatters.resetLabel(for: metric, useRelativeTime: useRelativeTime))")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

struct NotConfiguredView: View {
    let onOpenSettings: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Text("Welcome to OpenUsage")
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)
            Text("Connect Claude, Codex, Grok, or any combination in Settings.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button("Connect a provider", action: onOpenSettings)
                .buttonStyle(.borderedProminent)
                .padding(.top, 12)
            Spacer()
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
