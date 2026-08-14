# OpenUsage for iOS

A native SwiftUI port of the OpenUsage Android app: Claude session + weekly
usage, Codex weekly usage and Grok weekly unified-billing usage, fetched
directly from the phone with no server and no desktop bridge.

Everything is local-first. Credentials live in the iOS Keychain, preferences in
`UserDefaults`, and 30 days of usage history in a CSV file inside the app
container. There is no analytics pipeline and no push service.

## Layout

```
ios/
├── project.yml                 # XcodeGen spec — the source of truth for the project
├── OpenUsageCore/              # SwiftPM package: Foundation-only, fully unit-tested
│   ├── Package.swift
│   ├── Sources/OpenUsageCore/
│   │   ├── Models/             # UsageMetric, ClaudeUsage, CodexUsage, GrokUsage, SyncState…
│   │   ├── Networking/         # DTOs, flexible number decoding, URLSession transport
│   │   ├── Mapping/            # DTO → domain
│   │   ├── Guardrail/          # SessionGuardrailEvaluator (pace, cap risk, burn phase)
│   │   ├── Notifications/      # Session + weekly threshold ladders, NotificationPlanner
│   │   ├── Repositories/       # Claude / Codex / Grok, incl. device-code OAuth + refresh
│   │   ├── Storage/            # Preferences, usage cache, 30-day history, credential protocol
│   │   ├── Support/            # ISO-8601 parsing, formatters, guardrail display bands
│   │   └── UsageRefreshService.swift
│   └── Tests/OpenUsageCoreTests/
└── OpenUsage/                  # SwiftUI app target
    ├── OpenUsageApp.swift      # @main, background-task registration
    ├── AppEnvironment.swift    # composition root (the Hilt stand-in)
    ├── Background/             # BGAppRefreshTask scheduling
    ├── Networking/             # NWPathMonitor connectivity
    ├── Notifications/          # UNUserNotificationCenter delivery
    ├── Storage/                # Keychain credential store
    ├── Theme/                  # Colours, guardrail palette, shapes
    ├── ViewModels/             # Dashboard + Settings
    ├── Views/                  # Dashboard, Settings, cards
    ├── Assets.xcassets/
    └── Info.plist
```

`OpenUsageCore` deliberately imports **Foundation only** — no SwiftUI, no UIKit,
no WidgetKit, no UserNotifications. That keeps it testable on the command line
and makes it directly linkable by a future widget extension.

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| macOS | 14 or newer | — |
| Xcode | 15.0 or newer (built and verified with 26.6) | Mac App Store or [developer.apple.com](https://developer.apple.com/xcode/) |
| XcodeGen | 2.4x | `brew install xcodegen` |

Accept the Xcode licence once before anything else:

```bash
sudo xcodebuild -license accept
xcode-select -p            # should print /Applications/Xcode.app/Contents/Developer
```

## Generate the project

`OpenUsage.xcodeproj` is **generated** and is not committed. Recreate it after
every clone and after any change to `project.yml`:

```bash
brew install xcodegen
cd ios
xcodegen generate
open OpenUsage.xcodeproj
```

## Build and test from the command line

```bash
# Core logic: builds and tests on the Mac, no simulator required
cd ios/OpenUsageCore
swift build
swift test

# The app, for the simulator
cd ios
xcodebuild -project OpenUsage.xcodeproj -scheme OpenUsage \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

If no simulator runtime is installed, `xcodebuild` will report that the
destination is ineligible. Install one with Xcode → Settings → Components, or:

```bash
xcodebuild -downloadPlatform iOS
xcrun simctl list runtimes      # confirm it appears
```

---

# Installing on your iPhone with a **free** Apple ID

You do not need the $99/year Apple Developer Program to run this on your own
phone. A free "personal team" is enough, with the limits described below.

## What you need

- Your iPhone and a USB/USB-C cable.
- An Apple ID (any Apple ID; it does not need a paid membership).
- The Mac and phone on the same network is *not* required for a cable install.

## Step 1 — Add your Apple ID to Xcode

1. Xcode → **Settings…** → **Accounts**.
2. Click **+** → **Apple ID** → sign in.
3. Your account appears with a team named **"Your Name (Personal Team)"**. That
   is the free team.

## Step 2 — Enable Developer Mode on the phone (iOS 16 and newer)

Developer Mode is off by default and *must* be on or the app will refuse to
launch with "Untrusted Developer" / "Developer Mode disabled".

1. Connect the iPhone to the Mac with the cable and tap **Trust This Computer**
   on the phone, entering your passcode.
2. On the phone: **Settings → Privacy & Security → Developer Mode**.
   - If the row is missing, plug the phone into Xcode once (Window → Devices and
     Simulators) — the row appears after the Mac has talked to the device.
3. Turn **Developer Mode** on. The phone asks to restart. Restart it.
4. After the reboot, unlock the phone and confirm **Turn On** when prompted.

## Step 3 — Select the team and a unique bundle identifier

1. `cd ios && xcodegen generate`, then `open OpenUsage.xcodeproj`.
2. Select the **OpenUsage** target → **Signing & Capabilities**.
3. Tick **Automatically manage signing**.
4. Set **Team** to your personal team.
5. **You will probably have to change the bundle identifier.**
   `com.qbapps.claudeusage` may already be taken on Apple's side, and free teams
   cannot claim an identifier someone else registered. Change it to something
   unique to you, e.g. `com.yourname.openusage`.

   Do it in `project.yml` rather than in Xcode so it survives regeneration:

   ```yaml
   PRODUCT_BUNDLE_IDENTIFIER: com.yourname.openusage
   ```

   and update the matching entry in `OpenUsage/Info.plist`:

   ```xml
   <key>BGTaskSchedulerPermittedIdentifiers</key>
   <array><string>com.yourname.openusage.refresh</string></array>
   ```

   plus `BackgroundRefreshController.taskIdentifier` in
   `OpenUsage/Background/BackgroundRefreshController.swift`. The identifier in
   all three places must match exactly or iOS will crash the app on launch with
   *"BGTaskScheduler … not permitted"*.

   You can also set the team once in `project.yml` so it is applied on every
   regeneration:

   ```yaml
   DEVELOPMENT_TEAM: "ABCDE12345"   # your 10-character Team ID from Xcode → Settings → Accounts
   ```

## Step 4 — Build and run to the device

1. In Xcode's toolbar, pick your iPhone from the destination menu.
2. Press **⌘R**.
3. The first run fails with **"Could not launch … the application could not be
   verified"** or similar. This is expected — the certificate is not trusted yet.

## Step 5 — Trust the developer certificate on the phone

1. On the phone: **Settings → General → VPN & Device Management**.
2. Under **Developer App**, tap your Apple ID.
3. Tap **Trust "<your Apple ID>"** → **Trust**.
4. Back in Xcode, press **⌘R** again. The app launches.

## What a free personal team costs you

These are Apple's limits, not the app's:

- **7-day expiry.** Apps signed by a free personal team stop launching after
  **7 days**. Reconnect the phone and press ⌘R to re-sign; your Keychain data
  and preferences survive as long as you do not delete the app. A paid
  membership raises this to one year.
- **3 App IDs per device per 7 days.** A free team can register only three
  distinct bundle identifiers on a device in a rolling 7-day window. Do not
  churn the bundle ID while experimenting — you will lock yourself out for days.
- **10 App IDs per week per account**, across all your devices.
- **No App Groups.** Free teams cannot create App Group entitlements. This is
  why a future widget extension will have to perform its own network fetch and
  keep its own copy of the credentials in its own Keychain item, rather than
  sharing a container with the app. `OpenUsageCore` is structured for exactly
  that.
- **No push notifications.** Free teams get no APNs entitlement. The app uses
  only *local* notifications (`UNUserNotificationCenter`), which work fine.
- **No TestFlight and no App Store distribution.**
- **No CloudKit, no Sign in with Apple, no Wallet, no HealthKit** — all require
  a paid membership.

## Troubleshooting

| Symptom | Fix |
|---|---|
| *"Failed to register bundle identifier"* | The identifier is taken. Pick a new one in `project.yml`, then `xcodegen generate` again. |
| *"Unable to install … Developer Mode disabled"* | Step 2 — enable Developer Mode and reboot the phone. |
| *"Untrusted Developer"* on launch | Step 5 — trust the certificate in VPN & Device Management. |
| App launched fine, now says it "is no longer available" | The 7-day signature expired. Re-run from Xcode. |
| Crash on launch mentioning `BGTaskScheduler` | The identifier in Info.plist, `BackgroundRefreshController` and the bundle ID prefix do not match. |
| *"Maximum number of App IDs reached"* | You hit the 3-per-device/7-day limit. Wait it out or reuse an identifier you already registered. |

## Background refresh, honestly

iOS **cannot** reproduce Android's WorkManager cadence. `BGAppRefreshTask` runs
when the system decides to run it — typically a few times a day, weighted by how
often you open the app, and never on a fixed schedule. The refresh interval you
set in Settings drives the **foreground** poll loop only. Background refreshes
re-arm themselves after every run and use your interval as an
*earliest-begin* hint that iOS is free to ignore, with a 15-minute floor.

Practically: notifications will land reliably while the app is open or recently
used, and opportunistically otherwise. Widgets (phase 2) will improve this, since
WidgetKit timelines get their own refresh budget.
