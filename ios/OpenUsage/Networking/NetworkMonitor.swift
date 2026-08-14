import Foundation
import Network
import Observation

/// Port of `data/network/NetworkMonitor.kt`. Drives the offline banner and the
/// "refresh on reconnect" behaviour.
@MainActor
@Observable
final class NetworkMonitor {
    private(set) var isOnline = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.qbapps.claudeusage.network")
    private var started = false

    /// Called whenever connectivity is regained after being lost.
    var onReconnect: (() -> Void)?

    func start() {
        guard !started else { return }
        started = true
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in
                guard let self else { return }
                let reconnected = !self.isOnline && online
                self.isOnline = online
                if reconnected { self.onReconnect?() }
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        guard started else { return }
        monitor.cancel()
        started = false
    }
}
