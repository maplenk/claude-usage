import BackgroundTasks
import Foundation
import OpenUsageCore

/// The closest iOS equivalent of Android's WorkManager chain.
///
/// **This cannot match Android's cadence.** `BGAppRefreshTask` runs only when
/// iOS decides to run it — typically a handful of times a day, weighted by how
/// often the user opens the app, and never on a fixed interval. The user's
/// configured refresh interval therefore drives the *foreground* poll loop
/// (`DashboardViewModel`) only; background work re-arms itself opportunistically
/// with the interval as the earliest-begin hint, and iOS is free to ignore it.
enum BackgroundRefreshController {
    static let taskIdentifier = "com.qbapps.claudeusage.refresh"

    /// Minimum gap iOS will honour; anything shorter is pointless to request.
    private static let minimumEarliestBeginInterval: TimeInterval = 15 * 60

    /// Submits (or re-submits) the app-refresh request. Safe to call repeatedly:
    /// BGTaskScheduler replaces a pending request with the same identifier.
    static func schedule(preferredInterval seconds: Int) {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(
            timeIntervalSinceNow: max(TimeInterval(seconds), minimumEarliestBeginInterval)
        )
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // Simulators and devices with Background App Refresh disabled throw
            // here; the app still works, it just will not refresh in the
            // background.
        }
    }

    static func cancelAll() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }
}
