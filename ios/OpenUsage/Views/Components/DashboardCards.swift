import OpenUsageCore
import SwiftUI

/// Port of `SessionHeroCard.kt` — the Claude 5-hour session with the weekly
/// reading nested underneath.
struct SessionHeroCard: View {
    let metric: UsageMetric?
    let weeklyMetric: UsageMetric?
    let useRelativeTime: Bool
    let isStale: Bool

    private var status: HeadroomStatus {
        HeadroomStatus.of(metric, isStale: isStale)
    }

    var body: some View {
        UsageCardContainer(
            borderColor: status == .critical ? OpenUsageColor.statusCritical : nil,
            borderWidth: 2
        ) {
            VStack(alignment: .leading, spacing: 16) {
                header
                if let metric {
                    let utilization = min(max(metric.effectiveUtilization(), 0), 100)
                    MetricReading(
                        utilization: utilization,
                        trailing: "\(Int(max(100 - utilization, 0).rounded()))% left",
                        trailingColor: utilization >= 90 ? status.foreground : .secondary
                    )
                    UsageIndicator(
                        progress: utilization / 100,
                        color: status.foreground,
                        height: 14,
                        isStale: isStale
                    )
                    CardDivider()
                    resetRow(for: metric)
                    CardDivider()
                    weeklyReading
                } else {
                    Text("No session data yet")
                        .font(.title3.weight(.semibold))
                    Text("Pull to refresh. Cached usage will stay visible if a later refresh fails.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var header: some View {
        HStack {
            ProviderLabel(brand: .claude, text: "CLAUDE · SESSION")
            Spacer()
            HeadroomStatusChip(status: status)
        }
    }

    @ViewBuilder
    private func resetRow(for metric: UsageMetric) -> some View {
        HStack {
            Text(metric.isExpired() ? "Last window" : "Resets in")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
            if isStale {
                Text("\(Formatters.resetLabel(for: metric, useRelativeTime: useRelativeTime)) est.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else if metric.isExpired() {
                Text("Awaiting fresh data")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(status.foreground)
            } else if let resetsAt = metric.resetsAt {
                if useRelativeTime {
                    CountdownText(resetsAt: resetsAt)
                } else {
                    Text(Formatters.absoluteReset(resetsAt))
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                }
            } else {
                Text("—").font(.subheadline)
            }
        }
    }

    private var weeklyReading: some View {
        let weeklyStatus = HeadroomStatus.of(weeklyMetric, isStale: isStale)
        let utilization = weeklyMetric.map { min(max($0.effectiveUtilization(), 0), 100) }
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                ProviderLabel(brand: .claude, text: "CLAUDE · WEEKLY")
                Spacer()
                HeadroomStatusChip(status: weeklyStatus)
            }
            MetricReading(
                utilization: utilization,
                valueSize: 34,
                trailing: Formatters.resetLabel(for: weeklyMetric, useRelativeTime: useRelativeTime)
                    + (isStale ? " est." : "")
            )
            UsageIndicator(
                progress: (utilization ?? 0) / 100,
                color: weeklyStatus.foreground,
                isStale: isStale
            )
        }
    }
}

/// Port of `ProviderUsageCards.kt` — the Codex and Grok weekly tiles.
struct ProviderWeeklyCard: View {
    let brand: ProviderBrand
    let label: String
    let metric: UsageMetric?
    let useRelativeTime: Bool
    let isStale: Bool

    var body: some View {
        let status = HeadroomStatus.of(metric, isStale: isStale)
        let utilization = metric.map { min(max($0.effectiveUtilization(), 0), 100) }

        UsageCardContainer {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    ProviderLabel(brand: brand, text: label)
                    Spacer()
                    HeadroomStatusChip(status: status)
                }

                if let metric {
                    MetricReading(
                        utilization: utilization,
                        valueSize: 44,
                        suffix: "% used",
                        trailing: "\(Int(max(100 - (utilization ?? 0), 0).rounded()))% left"
                    )
                    UsageIndicator(
                        progress: (utilization ?? 0) / 100,
                        color: brand.accent,
                        isStale: isStale
                    )
                    CardDivider()
                    HStack {
                        Text(metric.isExpired() ? "Last window" : "Resets in")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(
                            Formatters.resetLabel(for: metric, useRelativeTime: useRelativeTime)
                                + (isStale ? " est." : "")
                        )
                        .font(.subheadline.weight(.semibold))
                    }
                } else {
                    Text("No weekly data yet")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

/// Port of `SessionGuardrailCard.kt`. Only appears when the pace actually
/// warrants an advisory.
struct SessionGuardrailCard: View {
    let metric: UsageMetric?
    let insights: SessionGuardrailInsights

    private var shouldShow: Bool {
        insights.willHitCapBeforeReset || (metric?.effectiveUtilization() ?? 0) >= 90
    }

    private var advisoryStatus: HeadroomStatus {
        switch insights.state {
        case .safe, .steady: return HeadroomStatus.of(metric)
        case .watch: return .elevated
        case .high: return .high
        case .critical: return .critical
        }
    }

    var body: some View {
        if shouldShow {
            UsageCardContainer(borderColor: advisoryStatus.foreground.opacity(0.45)) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Pace advisory")
                            .font(.headline)
                        Spacer()
                        HeadroomStatusChip(status: advisoryStatus)
                    }
                    Text(primaryMessage)
                        .font(.subheadline)
                    Text(secondaryMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var primaryMessage: String {
        if insights.willHitCapBeforeReset {
            let capIn = insights.predictedTimeToCapMinutes.map(Formatters.minutes) ?? "under an hour"
            return "At this pace you may hit the cap in \(capIn)."
        }
        return "Very little session headroom remains."
    }

    private var secondaryMessage: String {
        let resetIn = insights.timeToResetMinutes.map(Formatters.minutes) ?? "—"
        return "Resets in \(resetIn). Weekly limits are unaffected."
    }
}

/// Port of `DashboardScreen.SyncStateBanner`.
struct SyncStateBanner: View {
    let syncState: SyncState
    let onRetry: () -> Void

    var body: some View {
        if !syncState.isFresh {
            HStack(spacing: 12) {
                Image(systemName: "questionmark.circle.fill")
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.semibold))
                    Text(message).font(.footnote).foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Button("Refresh", action: onRetry)
                    .font(.subheadline)
                    .disabled(syncState.isOffline)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OpenUsageColor.raisedSurface, in: OpenUsageShape.tile)
        }
    }

    private var title: String {
        switch syncState {
        case .ageing: return "Sync is taking longer"
        case .stale: return "Usage data may be out of date"
        case .offline: return "You're offline"
        case .fresh: return ""
        }
    }

    private var message: String {
        let age = Formatters.ageText(minutes: syncState.ageMinutes)
        switch syncState {
        case .ageing: return "Last successful update was \(age)."
        case .stale: return "Showing cached values from \(age)."
        case .offline:
            return syncState.fetchedAt == nil
                ? "Connect to the internet to load usage."
                : "Showing the last data seen \(age)."
        case .fresh: return ""
        }
    }
}

/// Port of `DashboardScreen.ProviderErrorBanner`.
struct ProviderErrorBanner: View {
    let provider: String
    let message: String
    let onRetry: () -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(provider) could not refresh")
                    .font(.subheadline.weight(.bold))
                Text(message)
                    .font(.footnote)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Button(provider == "Claude" ? "Update key" : "Sign in", action: onOpenSettings)
                Button("Retry", action: onRetry)
            }
            .font(.footnote)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(OpenUsageColor.containerCritical, in: OpenUsageShape.tile)
    }
}

/// Port of `DashboardMetaCard.kt`.
struct DashboardMetaRow: View {
    let syncState: SyncState
    let refreshIntervalSeconds: Int
    let isRefreshing: Bool
    @State private var showAbsoluteTime = false

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(indicatorColor)
                .frame(width: 6, height: 6)
            Text(text)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .onLongPressGesture { showAbsoluteTime.toggle() }
        .accessibilityElement(children: .combine)
    }

    private var indicatorColor: Color {
        switch syncState {
        case .fresh: return OpenUsageColor.statusNormal
        case .ageing: return OpenUsageColor.statusElevated
        case .stale, .offline: return OpenUsageColor.statusUnknown
        }
    }

    private var text: String {
        if isRefreshing { return "Refreshing providers…" }
        if showAbsoluteTime, let fetchedAt = syncState.fetchedAt {
            return "Last sync \(Formatters.absoluteSyncTime(fetchedAt))"
        }
        let age = Formatters.relativeAge(syncState.fetchedAt)
        switch syncState {
        case .offline: return "Offline · last seen \(age)"
        case .stale: return "Last sync \(age) · data is stale"
        case .ageing: return "Last sync \(age) · checking soon"
        case .fresh: return "Synced \(age) · auto every \(refreshIntervalSeconds)s"
        }
    }
}
