import OpenUsageCore
import SwiftUI

/// Status chip — glyph plus word, never colour alone.
struct HeadroomStatusChip: View {
    let status: HeadroomStatus

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: status.symbolName)
                .font(.system(size: 10, weight: .bold))
            Text(status.label)
                .font(.system(size: 11, weight: .bold))
                .kerning(0.6)
        }
        .foregroundStyle(status.foreground)
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(status.container, in: OpenUsageShape.pill)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(status.label.capitalized)
    }
}

/// Provider label row: mark + uppercase caption, tinted with the provider accent.
struct ProviderLabel: View {
    let brand: ProviderBrand
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: brand.symbolName)
                .font(.system(size: 12, weight: .heavy))
            Text(text)
                .font(.system(size: 12, weight: .bold))
                .kerning(1.1)
        }
        .foregroundStyle(brand.accent)
    }
}

/// Flat progress rail. Stale readings are rendered hollow so a cached value is
/// never mistaken for a live one.
struct UsageIndicator: View {
    let progress: Double
    let color: Color
    var height: CGFloat = 10
    var isStale = false

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(OpenUsageColor.raisedSurface)
                Capsule()
                    .fill(isStale ? AnyShapeStyle(color.opacity(0.35)) : AnyShapeStyle(color))
                    .frame(width: geometry.size.width * min(max(progress, 0), 1))
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

/// Live countdown to a reset instant, ticking once a second.
struct CountdownText: View {
    let resetsAt: Date
    var font: Font = .system(size: 17, weight: .semibold, design: .rounded)

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            Text(Formatters.countdown(
                secondsRemaining: Formatters.secondsRemaining(until: resetsAt, now: context.date)
            ))
            .font(font)
            .monospacedDigit()
        }
    }
}

/// Shared card chrome for every dashboard tile.
struct UsageCardContainer<Content: View>: View {
    var borderColor: Color?
    var borderWidth: CGFloat = 1
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(.horizontal, 22)
            .padding(.vertical, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(OpenUsageColor.cardSurface, in: OpenUsageShape.card)
            .overlay {
                if let borderColor {
                    OpenUsageShape.card.strokeBorder(borderColor, lineWidth: borderWidth)
                }
            }
    }
}

/// Big "72 %" reading with the remaining-headroom caption beside it.
struct MetricReading: View {
    let utilization: Double?
    var valueSize: CGFloat = 58
    var suffix: String = "%"
    var trailing: String?
    var trailingColor: Color = .secondary

    var body: some View {
        HStack(alignment: .lastTextBaseline) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(utilization.map { String(Int($0.rounded())) } ?? "—")
                    .font(.system(size: valueSize, weight: .semibold, design: .rounded))
                    .monospacedDigit()
                if utilization != nil {
                    Text(suffix)
                        .font(.system(size: valueSize * 0.36, weight: .semibold))
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(trailingColor)
            }
        }
    }
}

struct CardDivider: View {
    var body: some View {
        Rectangle()
            .fill(OpenUsageColor.hairline.opacity(0.75))
            .frame(height: 1)
    }
}
