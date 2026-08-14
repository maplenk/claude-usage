import OpenUsageCore
import SwiftUI

/// Port of `ui/theme/Color.kt` and `ui/theme/Guardrail.kt`.
///
/// Guardrail colours are fixed values rather than system colours: a warning must
/// never be recoloured by an accent change, exactly as on Android where dynamic
/// colour is barred from recolouring guardrail state.
enum OpenUsageColor {
    // Provider accents
    static let claude = adaptive(light: 0x8F5024, dark: 0xFFB786)
    static let codex = adaptive(light: 0x3A5BA0, dark: 0xAFC6FF)
    static let grok = adaptive(light: 0x7B3F9E, dark: 0xE3B7F5)

    // Guardrail foregrounds
    static let statusNormal = adaptive(light: 0x10695B, dark: 0x6FDBC4)
    static let statusElevated = adaptive(light: 0x7A5900, dark: 0xF4C044)
    static let statusHigh = adaptive(light: 0x99400F, dark: 0xFFB59A)
    static let statusCritical = adaptive(light: 0xA8261F, dark: 0xFFB4AB)
    static let statusUnknown = adaptive(light: 0x4A4844, dark: 0xC9C6C2)

    // Guardrail containers
    static let containerNormal = adaptive(light: 0xA7F2E1, dark: 0x005044)
    static let containerElevated = adaptive(light: 0xFFDF9B, dark: 0x5C4200)
    static let containerHigh = adaptive(light: 0xFFDBCA, dark: 0x7A2E00)
    static let containerCritical = adaptive(light: 0xFFDAD5, dark: 0x93000A)
    static let containerUnknown = adaptive(light: 0xE5E3DE, dark: 0x3A3A3D)

    // Surfaces
    static let cardSurface = adaptive(light: 0xFFFFFF, dark: 0x0B0C0E)
    static let raisedSurface = adaptive(light: 0xEBE9E4, dark: 0x26282C)
    static let screenBackground = adaptive(light: 0xFBF9F7, dark: 0x111214)
    static let hairline = adaptive(light: 0xCDCBC5, dark: 0x3A3A3D)

    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255.0,
            green: CGFloat((hex >> 8) & 0xFF) / 255.0,
            blue: CGFloat(hex & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

extension HeadroomStatus {
    var foreground: Color {
        switch self {
        case .normal: return OpenUsageColor.statusNormal
        case .elevated: return OpenUsageColor.statusElevated
        case .high: return OpenUsageColor.statusHigh
        case .critical: return OpenUsageColor.statusCritical
        case .stale: return OpenUsageColor.statusUnknown
        }
    }

    var container: Color {
        switch self {
        case .normal: return OpenUsageColor.containerNormal
        case .elevated: return OpenUsageColor.containerElevated
        case .high: return OpenUsageColor.containerHigh
        case .critical: return OpenUsageColor.containerCritical
        case .stale: return OpenUsageColor.containerUnknown
        }
    }

    /// State is never colour-only: every level pairs a colour with a distinct
    /// glyph and a word, mirroring the Android guardrail glyphs.
    var symbolName: String {
        switch self {
        case .normal: return "checkmark.circle.fill"
        case .elevated: return "circle.lefthalf.filled"
        case .high: return "exclamationmark.triangle.fill"
        case .critical: return "exclamationmark.octagon.fill"
        case .stale: return "questionmark.circle.fill"
        }
    }
}

extension ProviderBrand {
    var accent: Color {
        switch self {
        case .claude: return OpenUsageColor.claude
        case .codex: return OpenUsageColor.codex
        case .grok: return OpenUsageColor.grok
        }
    }

    var symbolName: String {
        switch self {
        case .claude: return "asterisk"
        case .codex: return "chevron.left.forwardslash.chevron.right"
        case .grok: return "xmark"
        }
    }
}

enum OpenUsageShape {
    static let card = RoundedRectangle(cornerRadius: 24, style: .continuous)
    static let tile = RoundedRectangle(cornerRadius: 20, style: .continuous)
    static let pill = Capsule(style: .continuous)
}
