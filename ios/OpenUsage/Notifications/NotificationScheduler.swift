import Foundation
import OpenUsageCore
import UserNotifications

/// Delivers the notifications that `NotificationPlanner` decides on.
///
/// iOS has no notification channels, so each Android channel maps to a
/// `UNNotificationCategory`; users still get a per-app switch plus the in-app
/// toggles that mirror Android's. Everything is local — there is no push
/// certificate and no server, which also keeps the app installable on a free
/// personal Apple team.
@MainActor
final class NotificationScheduler {
    private let center: UNUserNotificationCenter
    private var categoriesRegistered = false

    init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    /// Registers the categories that stand in for Android's channels.
    ///
    /// Deliberately *not* called at launch: on recent iOS versions touching the
    /// notification centre before the user has any reason to care triggers the
    /// system permission alert over the onboarding screen. Categories are
    /// registered once authorization actually exists.
    func registerCategoriesIfNeeded() {
        guard !categoriesRegistered else { return }
        categoriesRegistered = true
        let categories = Set(
            NotificationCategory.allCases.map { category in
                UNNotificationCategory(
                    identifier: category.rawValue,
                    actions: [],
                    intentIdentifiers: [],
                    options: []
                )
            }
        )
        center.setNotificationCategories(categories)
    }

    @discardableResult
    func requestAuthorizationIfNeeded() async -> Bool {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            if granted { registerCategoriesIfNeeded() }
            return granted
        case .denied:
            return false
        default:
            registerCategoriesIfNeeded()
            return true
        }
    }

    func isAuthorized() async -> Bool {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        default: return false
        }
    }

    /// Fires each planned notification immediately. Identifiers are stable, so
    /// re-delivering the same alert replaces rather than stacks it — the
    /// equivalent of reusing an Android notification ID.
    func deliver(_ requests: [UsageNotificationRequest]) async {
        guard !requests.isEmpty else { return }
        guard await isAuthorized() else { return }
        registerCategoriesIfNeeded()

        for request in requests {
            let content = UNMutableNotificationContent()
            content.title = request.title
            content.body = request.body
            content.categoryIdentifier = request.category.rawValue
            content.sound = .default

            let notification = UNNotificationRequest(
                identifier: request.identifier,
                content: content,
                trigger: nil // deliver now
            )
            try? await center.add(notification)
        }
    }

    func removeAllDelivered() {
        center.removeAllDeliveredNotifications()
        center.removeAllPendingNotificationRequests()
    }
}
