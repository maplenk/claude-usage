# Android → iOS parity map

Every Android feature, where it lives on iOS, and what was deliberately left
out. Android paths are relative to
`app/src/main/java/com/qbapps/claudeusage/`; iOS paths to `ios/`.

## Domain model

| Android | iOS | Notes |
|---|---|---|
| `domain/model/ClaudeUsage.kt` (`ClaudeUsage`, `UsageMetric`) | `OpenUsageCore/Sources/OpenUsageCore/Models/UsageMetric.swift`, `Models/ProviderUsage.swift` | `effectiveUtilization`, `effectiveStatus` and `isExpired` ported verbatim. |
| `domain/model/UsageStatus.kt` | `Models/UsageMetric.swift` (`UsageStatus`) | Same 50 % / 80 % bands. |
| `domain/model/CodexUsage.kt`, `GrokUsage.kt` | `Models/ProviderUsage.swift` | `expiresAtMs: Long` became `expiresAt: Date`. |
| `domain/model/Organization.kt` | `Models/ProviderUsage.swift` | Also conforms to `Identifiable`. |
| `domain/model/UsageError.kt` | `Models/UsageError.swift` | Kotlin wraps this in `UsageApiException`; Swift throws the enum. `ProviderError` carries the Codex/Grok message strings. |
| `domain/model/UsageHistoryPoint.kt` | `Models/ProviderUsage.swift` | Identical fields. |
| `ui/dashboard/SyncState.kt` | `Models/SyncState.swift` | Same 5 / 10-minute fresh-ageing-stale bands. |

## Networking

| Android | iOS | Notes |
|---|---|---|
| `data/remote/ClaudeApiService.kt` | `Repositories/ClaudeUsageRepository.swift` (`ClaudeAPIContract`) | Same two endpoints and `sessionKey=` cookie format. |
| `data/remote/AuthInterceptor.kt` | Folded into `ClaudeUsageRepository` | The cookie is attached per request; there is no interceptor chain to hook. |
| `data/remote/UsageResponseDto.kt` | `Networking/ClaudeDTOs.swift` | Same `@SerializedName` → `CodingKeys` mapping. |
| `data/remote/UtilizationAdapter.kt` | `Networking/FlexibleNumber.swift` (`FlexibleDouble`) | Int / Double / String / null / missing all handled; unparseable → `0.0`, matching Gson. |
| `data/remote/CodexApiService.kt` | `Networking/CodexDTOs.swift` + `Repositories/CodexUsageRepository.swift` | Same base URLs, client ID, paths. `user_code` keeps the `usercode` alternate key. The `interval` field decodes from number *or* string (`FlexibleInt`). |
| `data/remote/GrokApiService.kt` | `Networking/GrokDTOs.swift` + `Repositories/GrokUsageRepository.swift` | Same device-code, token and credits endpoints and the `X-XAI-Token-Auth` header. |
| Retrofit + OkHttp + Gson | `URLSession` + `Codable` behind the `HTTPTransport` protocol | No third-party dependencies. The protocol seam is what lets the repositories be unit-tested. |

## Mapping

| Android | iOS |
|---|---|
| `data/mapper/UsageMapper.kt` | `Mapping/UsageMappers.swift` |
| `data/mapper/CodexUsageMapper.kt` | `Mapping/UsageMappers.swift` (`toWeeklyDomain`) — same ±1-hour seven-day window tolerance, same `reset_at` / `reset_after_seconds` precedence |
| `data/mapper/GrokUsageMapper.kt` | `Mapping/UsageMappers.swift` (`toGrokWeeklyDomain`) — same four error strings, same weekly-period-type guard |

`Instant.parse` accepts arbitrary fractional-second precision; `ISO8601DateFormatter`
does not, so `Support/ISO8601.swift` normalises before parsing. It is tested
against the exact `2026-08-08T04:01:09.238389+00:00` shape the Grok billing
endpoint returns.

## Repositories

| Android | iOS | Notes |
|---|---|---|
| `data/repository/UsageRepositoryImpl.kt` | `Repositories/ClaudeUsageRepository.swift` | Same 5-second minimum fetch interval and HTTP→`UsageError` mapping. Notification side effects were extracted (see below). |
| `data/repository/CodexUsageRepositoryImpl.kt` | `Repositories/CodexUsageRepository.swift` | Device-code polling with 403/404 → wait, 15-minute timeout, form-encoded authorization-code exchange, JSON refresh, 401/403 → force-refresh-and-retry, 5-minute usage throttle. |
| `data/repository/GrokUsageRepositoryImpl.kt` | `Repositories/GrokUsageRepository.swift` | `authorization_pending` / `slow_down` (+5 s) / `access_denied` / `expired_token` handled identically; HTTPS-only verification URL check retained. |
| `JwtClaims` (inside `CodexUsageRepositoryImpl.kt`) | `Repositories/JWTClaims.swift` | Same 5-minute refresh window, same top-level and `https://api.openai.com/auth` nested `chatgpt_account_id` lookup. |

Kotlin's `Mutex` around token refresh became actor isolation — each repository
is an `actor`, so refreshes cannot interleave.

## Guardrail

`domain/guardrail/SessionGuardrailEvaluator.kt` → `Guardrail/SessionGuardrailEvaluator.swift`.

Ported line for line, including the parts whose behaviour depends on Kotlin
semantics:

- `SessionGuardrailState.ordinal` comparisons are preserved via an explicit
  `rank` (safe 0 … critical 4).
- `averageBurnByPhase(...).maxByOrNull { it.value }` returns the **first**
  maximum of a `LinkedHashMap` ordered EARLY, MID, LATE. The Swift version
  iterates in that order with a strict `>`, so ties still resolve to `.early`,
  and an empty sample set still yields `.unknown`.
- `Duration.toMinutes()` truncates toward zero; `minutesBetween` does the same.
- The 0.03 %/min slope floor, the 8-point pace band, the 6-sample baseline
  minimum, the 10- and 20-minute slope windows, the 0.7/0.3 slope blend, the
  +30-minute watch bump and the "downgrade CRITICAL→HIGH when reset ≤ 20 min"
  rule are all unchanged.

## Notifications

Android has no direct iOS equivalent for notification channels, so each channel
maps to a `UNNotificationCategory`.

| Android | iOS |
|---|---|
| `notification/UsageThresholdEvaluator.kt` | `Notifications/UsageThresholdEvaluator.swift` — 75/80/85/90/100, highest-crossed-only, upgrade-safe fallback. All ten Kotlin unit tests were ported. |
| *(new)* weekly ladder | `Notifications/WeeklyThresholdEvaluator.swift` — 70/80/90/100 |
| `notification/UsageNotificationHelper.kt` copy | `Notifications/NotificationPlanner.swift` (titles/bodies) + `OpenUsage/Notifications/NotificationScheduler.swift` (delivery) |
| Notification IDs | Stable string identifiers, so re-delivering replaces rather than stacks |
| Channel `session_reset` / `usage_milestone` / `session_guardrail` | `NotificationCategory` cases of the same names, plus a new `weekly_milestone` |
| Guardrail dedup in `UsageRepositoryImpl.maybeNotifyGuardrailSignals` | `NotificationPlanner.planGuardrail` — same 30-minute "is this a new session?" drift tolerance, same three one-shot flags |
| Session-reset detection in `worker/UsageSyncWorker.kt` | `NotificationPlanner.planSessionReset` — same "was > 0, now == 0" rule |

**The weekly ladder** (matching the parallel Android change): thresholds
70/80/90/100; at most one notification per limit per refresh, always the highest
crossed; dedup state is keyed on the metric's `resetsAt`, and a **later**
`resetsAt` clears that limit's state because the window rolled over. It runs
independently for four limits — Claude weekly, Claude weekly Opus, Codex weekly
and Grok weekly. **Sonnet is excluded**, exactly as specified. Dropping below
70 % also clears the ladder, which covers a window that rolls over without the
reset time moving.

The decision logic is pure and lives in `OpenUsageCore`; the app layer only
turns `UsageNotificationRequest` values into `UNNotificationRequest`s. That is
what makes both ladders unit-testable without a device.

## Storage

| Android | iOS | Notes |
|---|---|---|
| `data/local/SecureCredentialStore.kt` (EncryptedSharedPreferences + Keystore) | `CredentialStoring` protocol in core, `OpenUsage/Storage/KeychainCredentialStore.swift` in the app | `kSecClassGenericPassword` with `kSecAttrAccessibleAfterFirstUnlock` so background refresh can read while locked. Nothing is ever logged. The protocol lives in core so a widget can supply its own store. |
| `data/local/UserPreferencesStore.kt` (Preferences DataStore) | `Storage/PreferencesStore.swift` (`UserDefaults`) | Same defaults: 30 s interval, notifications on, relative time on. |
| `data/local/UsageDataStore.kt`, `CodexUsageDataStore.kt`, `GrokUsageDataStore.kt` | `Storage/UsageCacheStore.swift` | One store, three JSON-encoded slots in `UserDefaults`. |
| `data/local/UsageHistoryStore.kt` | `Storage/UsageHistoryStore.swift` | Same CSV format, same 30-day retention and 10 000-row cap, same 5-column legacy-row fallback. Lives in `Application Support/OpenUsage/usage_history.csv`. |

## UI

| Android | iOS |
|---|---|
| `ui/dashboard/DashboardScreen.kt` | `Views/DashboardView.swift` |
| `ui/dashboard/DashboardViewModel.kt` | `ViewModels/DashboardViewModel.swift` (`@Observable`, iOS 17) |
| `ui/dashboard/components/SessionHeroCard.kt` | `Views/Components/DashboardCards.swift` → `SessionHeroCard` |
| `ui/dashboard/components/ProviderUsageCards.kt` | `DashboardCards.swift` → `ProviderWeeklyCard` |
| `ui/dashboard/components/SessionGuardrailCard.kt` | `DashboardCards.swift` → `SessionGuardrailCard` |
| `ui/dashboard/components/DashboardMetaCard.kt` | `DashboardCards.swift` → `DashboardMetaRow` (long-press still toggles absolute time) |
| `ui/dashboard/components/HeadroomStatus.kt` | `OpenUsageCore/Support/HeadroomStatus.swift` + `Theme/OpenUsageTheme.swift` |
| `ui/dashboard/components/CountdownTimer.kt` | `Views/Components/UsageComponents.swift` → `CountdownText` (`TimelineView`, 1 Hz) |
| `ui/dashboard/components/UsageProgressBar.kt` / `UsageIndicator.kt` | `UsageComponents.swift` → `UsageIndicator` |
| `ui/settings/SettingsScreen.kt` | `Views/SettingsView.swift` |
| `ui/settings/SettingsViewModel.kt` | `ViewModels/SettingsViewModel.swift` |
| `ui/settings/components/SessionKeyInput.kt` | `SettingsView.claudeSection` (`SecureField` + the same `sk-ant-` / 40-char validation) |
| `ui/settings/components/RefreshIntervalSlider.kt` | `SettingsView.refreshAndAlertsSection` (5–300 s, step 5) |
| `ui/theme/Color.kt`, `Guardrail.kt` | `Theme/OpenUsageTheme.swift` — same hex values, guardrail colours still fixed so no accent can recolour a warning |
| `ui/navigation/AppNavHost.kt` | `NavigationStack` + `navigationDestination` in `OpenUsageApp.swift` |

Status is never colour-only on either platform: each level pairs a fixed colour
with a distinct SF Symbol and an uppercase word.

## Background work

| Android | iOS |
|---|---|
| `worker/UsageSyncWorker.kt` + `WorkManagerScheduler.kt` (self-chaining one-shot + 15-min periodic fallback) | `Background/BackgroundRefreshController.swift` + `.backgroundTask(.appRefresh(…))` in `OpenUsageApp.swift` |
| Foreground auto-refresh loop in `DashboardViewModel` | Same loop in `ViewModels/DashboardViewModel.swift`, driven by the user's configured interval |
| `data/network/NetworkMonitor.kt` | `Networking/NetworkMonitor.swift` (`NWPathMonitor`), including refresh-on-reconnect |

**This is not equivalent.** Android's chain fires at the user's interval, down
to seconds. `BGAppRefreshTask` runs only when iOS chooses, typically a handful
of times a day, with a 15-minute floor on the earliest-begin hint. The
foreground loop honours the configured interval exactly; background refresh is
best-effort. This is an OS limit, not an implementation shortcut.

## Deliberately not ported

| Android feature | Why |
|---|---|
| **Glance home-screen widgets** (`widget/`, both `UsageWidget` and `FourLimitUsageWidget`) | Explicitly deferred to phase 2. `OpenUsageCore` is structured so a WidgetKit extension can link it and fetch on its own — no App-Group assumptions anywhere, and `CredentialStoring` is a protocol precisely so the extension can hold its own Keychain copy (free personal teams cannot use App Groups). |
| **Persistent/ongoing status-bar notification** (`showPersistentNotification`, `updatePersistentNotification`, the drawn battery-cell icon) | iOS has no ongoing-notification concept. The nearest equivalent is a Live Activity, which needs ActivityKit and is a separate design problem. The preference key is omitted rather than left dead. |
| **Battery-optimisation exemption prompt** | Android-specific. |
| **Debug log export** (`worker/SyncLog.kt`, "Export Debug Log") | The log existed to debug WorkManager scheduling; the iOS background model is different enough that a straight port would mislead. |
| **Dynamic colour / Material You** | No iOS equivalent. The fixed OpenUsage palette is used, which is what Android falls back to anyway for guardrail colours. |
| **Widget-focus deep links** (`focusProvider` / `providerRequest` in `AppNavHost`) | Only reachable from widgets, which are deferred. |
| **Wear OS module** (`wear/`) | Out of scope; watchOS is not part of v1. |

## Intentional differences

- **Grok `referrer`** — Android sends `openusage-android`; iOS sends
  `openusage-ios` (`GrokAPIContract.referrer`). Likewise the `User-Agent` is
  `OpenUsage iOS`. If xAI ever rejects the value, changing that one constant
  reverts it.
- **Grok `x-grok-client-version`** — Android passes `BuildConfig.VERSION_NAME`;
  iOS passes `CFBundleShortVersionString` (currently `0.7.0`, matching the
  Android `versionName`).
- **Claude organizations** are re-fetched when Settings appears so the picker is
  populated for an already-saved key; Android only populated it right after
  validation.
- **Opus and Sonnet weekly limits** get their own compact card on the iOS
  dashboard. On Android they are surfaced mainly through the four-limit widget;
  since v1 has no widget, they needed a home in the app.
- **Errors** are Swift `throws` rather than `Result`, and the throttled
  non-urgent Claude refetch is not surfaced as a user-visible error the way a
  `Result.failure(RateLimited)` could be.
- **The notification permission prompt is deferred** until at least one provider
  is connected. Registering notification categories at launch turned out to
  raise the system alert over the onboarding screen, which is a poor first run;
  categories are now registered the first time authorization actually exists.
  Android has no equivalent prompt before API 33 and no equivalent ordering
  problem.
