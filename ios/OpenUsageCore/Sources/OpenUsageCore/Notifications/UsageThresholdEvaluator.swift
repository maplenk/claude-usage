import Foundation

/// Faithful port of `notification/UsageThresholdEvaluator.kt` — the Claude
/// 5-hour session milestone ladder.
public enum UsageThresholdEvaluator {
    public static let thresholds = [75, 80, 85, 90, 100]

    public static func highestReachedThreshold(currentUtilization: Double?) -> Int? {
        guard let currentUtilization else { return nil }
        let current = clamp(currentUtilization, 0.0, 100.0)
        return thresholds.last { current >= Double($0) }
    }

    public static func highestCrossedThreshold(
        previousUtilization: Double?,
        currentUtilization: Double?
    ) -> Int? {
        guard let currentUtilization else { return nil }
        let current = clamp(currentUtilization, 0.0, 100.0)
        guard let currentHighest = highestReachedThreshold(currentUtilization: currentUtilization) else {
            return nil
        }
        guard let previousUtilization else { return currentHighest }

        let previous = clamp(previousUtilization, 0.0, 100.0)
        return thresholds.last { threshold in
            previous < Double(threshold) && current >= Double(threshold)
        }
    }
}
