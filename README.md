# Claude Usage Tracker - Android

Android app + home screen widget that displays your Claude AI usage metrics (session limits, daily limits, per-model breakdowns). Calls the same API as the [macOS Claude-Usage-Tracker](https://github.com/hamed-elfayome/Claude-Usage-Tracker).

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK | 17 | `brew install openjdk@17` |
| Android SDK | API 35 | Via Android Studio or `sdkmanager` |
| Gradle | 8.11.1 | Bundled wrapper (`./gradlew`) |

Set environment variables before building (or let Android Studio handle it):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Output at: app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected device
./gradlew installDebug
```

## Project Structure

```
cc-usage/
├── build.gradle.kts                       # Project-level plugins
├── settings.gradle.kts                    # Module config
├── gradle.properties                      # Build settings
├── gradle/
│   ├── wrapper/gradle-wrapper.properties  # Gradle 8.11.1
│   └── libs.versions.toml                 # Version catalog (all dependency versions here)
└── app/
    ├── build.gradle.kts                   # App dependencies & config
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/qbapps/claudeusage/
        │   │
        │   ├── ClaudeUsageApp.kt          # @HiltAndroidApp, WorkManager init
        │   ├── MainActivity.kt            # Single activity, hosts Compose nav
        │   │
        │   ├── domain/                    # Pure Kotlin models & interfaces
        │   │   ├── model/
        │   │   │   ├── ClaudeUsage.kt     # Core data: fiveHour, sevenDay, opus, sonnet metrics
        │   │   │   ├── Organization.kt    # Org uuid + name
        │   │   │   ├── UsageError.kt      # Sealed class: Unauthorized, RateLimited, etc.
        │   │   │   └── UsageStatus.kt     # SAFE / MODERATE / CRITICAL enum (thresholds: 50%, 80%)
        │   │   └── repository/
        │   │       └── UsageRepository.kt # Interface
        │   │
        │   ├── data/                      # API, storage, repo implementation
        │   │   ├── remote/
        │   │   │   ├── ClaudeApiService.kt      # Retrofit: GET /api/organizations, GET /api/organizations/{id}/usage
        │   │   │   ├── AuthInterceptor.kt       # OkHttp interceptor, injects Cookie header
        │   │   │   ├── UsageResponseDto.kt      # JSON DTOs with @SerializedName
        │   │   │   └── UtilizationAdapter.kt    # Gson adapter (utilization can be Int/Double/String)
        │   │   ├── local/
        │   │   │   ├── SecureCredentialStore.kt  # EncryptedSharedPreferences (session key, org ID)
        │   │   │   ├── UsageDataStore.kt         # Preferences DataStore — app cache
        │   │   │   └── UserPreferencesStore.kt   # Preferences DataStore — user settings (refresh interval, org)
        │   │   ├── repository/
        │   │   │   └── UsageRepositoryImpl.kt    # Fetches API → saves cache → pushes to widget
        │   │   └── mapper/
        │   │       └── UsageMapper.kt            # DTO → domain conversion
        │   │
        │   ├── di/                        # Hilt dependency injection
        │   │   ├── AppModule.kt           # Binds UsageRepository
        │   │   └── NetworkModule.kt       # Provides Retrofit, OkHttp, Gson
        │   │
        │   ├── ui/                        # Jetpack Compose screens
        │   │   ├── theme/
        │   │   │   ├── Color.kt           # Material3 palette + status colors
        │   │   │   ├── Theme.kt           # ClaudeUsageTheme, dynamic color support
        │   │   │   └── Type.kt
        │   │   ├── navigation/
        │   │   │   ├── Screen.kt          # Sealed class: Dashboard, Settings
        │   │   │   └── AppNavHost.kt      # NavHost with bottom nav bar
        │   │   ├── dashboard/
        │   │   │   ├── DashboardScreen.kt       # Pull-to-refresh, usage cards, error handling
        │   │   │   ├── DashboardViewModel.kt    # Auto-refresh loop, fetches usage
        │   │   │   └── components/
        │   │   │       ├── UsageCard.kt          # Card: label, progress bar, %, countdown
        │   │   │       ├── UsageProgressBar.kt   # Animated color-coded progress bar
        │   │   │       ├── CountdownTimer.kt     # Live countdown to reset time
        │   │   │       └── StatusIndicator.kt    # Colored dot
        │   │   └── settings/
        │   │       ├── SettingsScreen.kt         # Key input, org picker, interval slider, clear
        │   │       ├── SettingsViewModel.kt      # Validates key, saves config, starts sync
        │   │       └── components/
        │   │           ├── SessionKeyInput.kt    # Password field with paste + visibility toggle
        │   │           └── RefreshIntervalSlider.kt  # 5–300 second slider
        │   │
        │   ├── widget/                    # Glance home screen widget
        │   │   ├── UsageWidget.kt               # GlanceAppWidget, SizeMode.Responsive (3 sizes)
        │   │   ├── UsageWidgetContent.kt        # Widget composable: progress bars, status colors
        │   │   ├── UsageWidgetReceiver.kt       # GlanceAppWidgetReceiver
        │   │   ├── UsageWidgetStateDefinition.kt # Separate DataStore for widget state
        │   │   ├── WidgetActionCallback.kt      # Tap → open app, refresh button
        │   │   └── WidgetDataPusher.kt          # Pushes usage data into all widget instances
        │   │
        │   └── worker/                    # Background sync
        │       ├── UsageSyncWorker.kt           # @HiltWorker, fetches + re-enqueues
        │       └── WorkManagerScheduler.kt      # Schedules OneTimeWork chain + 15-min periodic fallback
        │
        └── res/
            ├── xml/usage_widget_info.xml        # Widget provider metadata (sizes, preview)
            ├── layout/glance_default_loading_layout.xml
            ├── drawable/
            │   ├── widget_background.xml        # Light mode rounded rect
            │   └── widget_background_dark.xml   # Dark mode rounded rect
            ├── mipmap-*/                        # App icons (all densities + adaptive + round)
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── values-night/colors.xml
```

## Architecture

```
Claude API (Retrofit)
       │
       ▼
  UsageRepositoryImpl ──► UsageDataStore (app cache)
       │                        │
       │                        ▼
       │                  DashboardViewModel ──► Compose UI
       │
       ├──► pushDataToWidgets() ──► Glance Widget State ──► Widget UI
       │
       ▼
  UsageSyncWorker (WorkManager)
       │
       └──► re-enqueues itself at configured interval
```

- **Foreground**: ViewModel refresh loop at user-configured interval (5–300s)
- **Background**: WorkManager OneTimeWork chain for sub-15-min intervals + 15-min periodic safety net
- **Widget**: Reads from its own Glance Preferences DataStore, updated by repository on every fetch

## API

| Endpoint | Method | Auth |
|----------|--------|------|
| `https://claude.ai/api/organizations` | GET | `Cookie: sessionKey=sk-ant-sid01-...` |
| `https://claude.ai/api/organizations/{orgId}/usage` | GET | Same |

## Key Dependencies

All versions managed in `gradle/libs.versions.toml`:

- **Compose BOM** 2024.12.01 (Material3)
- **Glance** 1.1.1 (widget framework)
- **Hilt** 2.53.1 (DI)
- **Retrofit** 2.11.0 + OkHttp 4.12.0
- **DataStore** 1.1.1
- **WorkManager** 2.10.0
- **Security Crypto** 1.1.0-alpha06 (EncryptedSharedPreferences)
- **Min SDK** 26 (Android 8.0) | **Target SDK** 35

## Setup Flow

1. Open app → redirected to Settings if no session key
2. Paste your Claude session key (`sk-ant-sid01-...`)
3. Tap Validate → fetches organizations → select org
4. Dashboard shows usage metrics, widget starts updating
5. Add widget to home screen from widget picker

## Credits

- Original project inspiration and API behavior reference:
  [hamed-elfayome/Claude-Usage-Tracker](https://github.com/hamed-elfayome/Claude-Usage-Tracker)

## Contributors

- [@claude](https://github.com/claude)
