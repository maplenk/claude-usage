import Foundation

/// Mirrors `data/local/UserPreferencesStore.kt` (Preferences DataStore).
/// `UserDefaults` is the closest iOS equivalent; nothing sensitive is stored
/// here — credentials live in the Keychain.
public final class PreferencesStore: @unchecked Sendable {
    public static let defaultRefreshIntervalSeconds = 30
    public static let minimumRefreshIntervalSeconds = 5
    public static let maximumRefreshIntervalSeconds = 300

    private enum Key {
        static let refreshInterval = "refresh_interval_seconds"
        static let selectedOrgId = "selected_org_id"
        static let notifyOnReset = "notify_on_session_reset"
        static let notifyOnUsageThresholds = "notify_on_usage_thresholds"
        static let useRelativeTime = "use_relative_time"
        static let notificationState = "notification_state_v1"
    }

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: - Refresh interval

    public var refreshIntervalSeconds: Int {
        get {
            let stored = defaults.object(forKey: Key.refreshInterval) as? Int
            return stored ?? Self.defaultRefreshIntervalSeconds
        }
        set {
            let clamped = clampInt(newValue, Self.minimumRefreshIntervalSeconds, Self.maximumRefreshIntervalSeconds)
            defaults.set(clamped, forKey: Key.refreshInterval)
        }
    }

    // MARK: - Organization

    public var selectedOrgId: String? {
        get { defaults.string(forKey: Key.selectedOrgId) }
        set {
            if let newValue {
                defaults.set(newValue, forKey: Key.selectedOrgId)
            } else {
                defaults.removeObject(forKey: Key.selectedOrgId)
            }
        }
    }

    // MARK: - Notification toggles

    public var notifyOnSessionReset: Bool {
        get { (defaults.object(forKey: Key.notifyOnReset) as? Bool) ?? true }
        set { defaults.set(newValue, forKey: Key.notifyOnReset) }
    }

    public var notifyOnUsageThresholds: Bool {
        get { (defaults.object(forKey: Key.notifyOnUsageThresholds) as? Bool) ?? true }
        set { defaults.set(newValue, forKey: Key.notifyOnUsageThresholds) }
    }

    /// When true, reset times show as relative countdowns; otherwise absolute.
    public var useRelativeTime: Bool {
        get { (defaults.object(forKey: Key.useRelativeTime) as? Bool) ?? true }
        set { defaults.set(newValue, forKey: Key.useRelativeTime) }
    }

    public var notificationSettings: NotificationSettings {
        NotificationSettings(
            notifyOnSessionReset: notifyOnSessionReset,
            notifyOnUsageThresholds: notifyOnUsageThresholds
        )
    }

    // MARK: - Notification ladder state

    public var notificationState: NotificationState {
        get {
            guard let data = defaults.data(forKey: Key.notificationState),
                  let decoded = try? JSONDecoder().decode(NotificationState.self, from: data) else {
                return .empty
            }
            return decoded
        }
        set {
            guard let data = try? JSONEncoder().encode(newValue) else { return }
            defaults.set(data, forKey: Key.notificationState)
        }
    }

    public func resetNotificationState() {
        defaults.removeObject(forKey: Key.notificationState)
    }

    // MARK: - Bulk clear

    public func clearAll() {
        for key in [
            Key.refreshInterval,
            Key.selectedOrgId,
            Key.notifyOnReset,
            Key.notifyOnUsageThresholds,
            Key.useRelativeTime,
            Key.notificationState,
        ] {
            defaults.removeObject(forKey: key)
        }
    }
}
