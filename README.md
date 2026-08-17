# Shellf Study

A [WaniKani](https://www.wanikani.com/) client for Android and iOS. Modern, minimalistic, written with Kotlin Multiplatform + Compose Multiplatform. Written/generated primarily using Claude.

## Stack

- **Kotlin Multiplatform** — business logic, repositories, ViewModels, and Compose UI all live in the `shared/` module and run on both Android and iOS
- **Compose Multiplatform (Material 3)** — single UI codebase, no XML layouts, no SwiftUI
- **MVVM / unidirectional data flow** — `ViewModel` exposes a single `StateFlow<UiState>`, screens are stateless composables that take `uiState` + callback lambdas
- **Koin** for dependency injection (shared modules + platform-specific extensions)
- **Ktor** + kotlinx.serialization for the WaniKani API v2 client (OkHttp engine on Android, Darwin engine on iOS)
- **Room** (multiplatform) for offline caching of subjects/assignments
- **DataStore (Preferences)** for settings and session state
- **Token storage**: Android Keystore (AES-GCM) on Android, Keychain on iOS

## Module layout

```
shared/                  Kotlin Multiplatform library — the whole app lives here
  commonMain/            Shared across all platforms
    network/             WaniKani API v2 Ktor client, DTOs, auth interceptor
    database/            Room entities, DAOs, migrations
    data/                Repositories, domain models, outbox drain, sync
    quiz/                QuizQueue, QuizGradingGuard, QuizAnswering — the quiz engine
    feature/             ViewModels + Compose screens for every feature
    designsystem/        Compose theme (colors, typography, shared components)
    navigation/          NavHost wiring all screens together
    notifications/       Notification coordinator, policies, builders
    sync/                SyncOrchestrator, WorkManager workers (shared logic)
    di/                  Koin shared modules
  androidMain/           Android-specific implementations (OkHttp, DataStore path)
  iosMain/               iOS-specific implementations (Darwin HTTP, Keychain, audio player)

app/                     Android application shell
  ShellfStudyApplication  Koin init, WorkManager config, Coil SVG setup
  di/                    Android-only Koin modules (workers, notification channels)
  core/                  Android-only integrations (notification workers, SVG design system)

iosApp/                  Xcode project — Swift wrapper, calls into shared KMP framework
  iOSApp.swift           App entry; calls IosEntryKt.doInitKoin()
  ContentView.swift      Hosts MainViewController() from shared
```

## Getting started

### Android
1. Create a [WaniKani API v2 token](https://www.wanikani.com/settings/personal_access_tokens) (read-only for browsing; `assignments:start` + `reviews:create` for full review submission).
2. Open the project in Android Studio and let Gradle sync.
3. Run the `app` module on an emulator or device, then enter your API token on the login screen.

### iOS
1. Run `./gradlew :shared:assembleDebugXCFramework` once (or let Xcode's Run Script phase call `embedAndSignAppleFrameworkForXcode` automatically).
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. Build and run on the iOS Simulator or a device.

> The Xcode Run Script build phase calls `./gradlew :shared:embedAndSignAppleFrameworkForXcode` — this builds the KMP framework, signs it, and copies Compose resources into the app bundle in one step.

## Build & test

```bash
./gradlew build                          # compile + lint (both platforms)

# Android tests
./gradlew :app:testDebugUnitTest         # JVM unit tests: ViewModels, Android-specific repos
./gradlew connectedAndroidTest           # instrumented tests — needs a running emulator/device

# Shared / KMP tests
./gradlew :shared:testAndroidHostTest    # commonTest suite on the JVM
./gradlew :shared:iosSimulatorArm64Test  # commonTest suite on the iOS Simulator
```

## License

MIT — see [LICENSE](LICENSE).
