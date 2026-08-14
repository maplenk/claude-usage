// swift-tools-version:5.9
import PackageDescription

// OpenUsageCore holds every piece of OpenUsage that does not touch UIKit,
// SwiftUI, WidgetKit or UserNotifications. It depends on Foundation only so
// that it can be linked by the app, by unit tests running on macOS, and (in a
// future phase) by a widget extension that performs its own network fetch.
let package = Package(
    name: "OpenUsageCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v13),
    ],
    products: [
        .library(name: "OpenUsageCore", targets: ["OpenUsageCore"]),
    ],
    targets: [
        .target(name: "OpenUsageCore"),
        .testTarget(name: "OpenUsageCoreTests", dependencies: ["OpenUsageCore"]),
    ]
)
