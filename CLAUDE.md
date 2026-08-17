# Shellf Study

A WaniKani (Japanese kanji/vocabulary SRS) client for Android and iOS, built with Kotlin Multiplatform + Compose Multiplatform.

## Stack

- **Kotlin Multiplatform** — the entire app (business logic, repositories, ViewModels, and Compose UI) lives in the `shared/` module
- **Compose Multiplatform (Material 3)**, no XML layouts, no SwiftUI
- **MVVM / unidirectional data flow** — `ViewModel` exposes a single `StateFlow<UiState>`, screens are stateless composables that take `uiState` + callback lambdas
- **Koin** for dependency injection (`shared/di/SharedModules.kt` + platform extensions)
- **Ktor** + kotlinx.serialization for the WaniKani API v2 client (OkHttp on Android, Darwin on iOS)
- **Room** (multiplatform) for offline caching — entities and DAOs in `shared/commonMain`
- **DataStore (Preferences)** for settings, session state, and the API token
- **Token storage**: Android Keystore AES-GCM on Android (`shared/androidMain/.../data/`), Keychain on iOS (`shared/iosMain/.../data/KeychainTokenCipher.kt`)

## Module layout

```
shared/                   KMP library — the whole app
  commonMain/             Shared across both platforms
    network/              WaniKani API v2 Ktor client, DTOs, auth interceptor
    database/             Room entities, DAOs
    data/                 Repositories, domain models, outbox drain, sync freshness
    quiz/                 QuizQueue, QuizGradingGuard, QuizAnswering — quiz engine
    feature/              ViewModels + Compose screens for every feature
    designsystem/         Compose theme, shared UI components
    navigation/           NavHost + route definitions
    notifications/        Coordinator, policies, scheduling
    sync/                 SyncOrchestrator + workers (shared logic only)
    di/                   Koin shared modules
  androidMain/            Android-specific: OkHttp, DataStore path
  iosMain/                iOS-specific: Darwin HTTP, Keychain, audio player, DataStore path
    IosEntry.kt           MainViewController() and initKoin() — called by Swift

app/                      Android application shell (thin wrapper)
  ShellfStudyApplication  Koin init, WorkManager config, Coil + SVG setup
  di/                     Android-only Koin modules (workers, notification channels)
  core/                   Android-only integrations (WorkManager workers, SVG interceptor)

iosApp/                   Xcode project
  iOSApp.swift            @main; calls IosEntryKt.doInitKoin()
  ContentView.swift        Hosts IosEntryKt.MainViewController() via UIViewControllerRepresentable
```

## Build & run

```bash
./gradlew build                          # compile + lint

# Android
./gradlew :app:testDebugUnitTest         # JVM unit tests
./gradlew connectedAndroidTest           # instrumented tests — needs emulator/device

# Shared (runs on JVM, fast)
./gradlew :shared:testAndroidHostTest    # commonTest + androidMain on JVM

# iOS tests (needs booted simulator)
./gradlew :shared:iosSimulatorArm64Test
```

For iOS: open `iosApp/iosApp.xcodeproj`. The Run Script phase calls
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` — builds the KMP framework, signs it, and copies Compose resources in one shot. Do not replace this with `linkDebugFrameworkIosSimulatorArm64`; that only links the binary and leaves resources uncopied.

> **Swift/ObjC naming quirk:** Kotlin functions starting with `init` are prefixed with `do` in Swift interop — `fun initKoin()` becomes `IosEntryKt.doInitKoin()`.

## WaniKani API

- Base URL: `https://api.wanikani.com/v2/`, auth via `Authorization: Bearer <token>`, every request needs `Wanikani-Revision: 20170710` (see auth interceptor in `shared/commonMain/network/`).
- DTOs use `ignoreUnknownKeys = true` / `coerceInputValues = true` — the real API has more fields than modelled here. Cross-check names against https://docs.api.wanikani.com/20170710/ before trusting an untested DTO field.
- Review submission in `WaniKaniRepository.submitReview` is simplified to "had any incorrect attempt" (0 or 1) rather than WaniKani's exact incorrect-count tracking — correct SRS progression, not a byte-for-byte match.

## Testing

Tests are split across three source sets. Default new tests to the fastest set that can run them.

### Where tests live

| Source set | Runner | What goes here |
|---|---|---|
| `shared/src/commonTest/` | JVM (`testAndroidHostTest`) + iOS Simulator | Pure Kotlin logic: repositories, quiz engine, calculators, parsers, KMP utilities |
| `app/src/test/` | JVM (Robolectric for Compose) | Android ViewModels, Room repositories, screen tests that don't need a real device |
| `app/src/androidTest/` | On-device / emulator | Anything that genuinely needs the Android runtime: Android Keystore, back-gesture handling |

### Patterns

- **ViewModels and repositories** use real collaborators where practical — `MockWebServer` for the WaniKani API, in-memory fake DAOs (`app/src/test/.../fakes/`), a temp-file-backed `DataStore`, and a no-op `FakeTokenCipher`.
- **`Turbine`** for asserting `StateFlow` emissions in order.
- **`TestRepositories` / `buildTestRepositories()`** in `app/src/test/.../fakes/TestApiFactory.kt` wires the full repository graph for ViewModel tests — prefer this over hand-constructing individual repos.
- **Compose screen tests** in `app/src/test/` run under Robolectric on the JVM. Pin with `@Config(sdk = [35])` (Robolectric 4.15.1 doesn't have shadows for `targetSdk` 37 yet). Use `@RunWith(AndroidJUnit4::class)` — same runner as real instrumentation, so a test can be moved to `androidTest` unchanged.
- **commonTest** coroutine tests: use `runTest` from `kotlinx.coroutines.test`; annotate classes that call `advanceUntilIdle` or other `@ExperimentalCoroutinesApi` APIs with `@OptIn(ExperimentalCoroutinesApi::class)`.

### What to test

Every feature should have both ViewModel/business-logic coverage and screen/UI coverage. Key things that must always be tested:

- **Happy path and error path** — `fetchFreshQueue` sets `errorMessage` on `ApiResult.Error`; retry clears it.
- **SRS stage transitions** — `passedAt` and `burnedAt` are set (and not overwritten) correctly.
- **Quiz engine invariants** — `QuizQueue.moveMatchingToFront` uses `indexOfLast`; `QuizGradingGuard` blocks concurrent submissions.
- **Silent drops** — missing DB rows (e.g. subject not cached) produce empty collections, not crashes.
- **Guard clauses** — `startSelectedLessons` with empty selection, `pauseActiveSegment` after session complete, etc.

### Running instrumented tests safely

**Never run `connectedAndroidTest` on a physical device** — `MainActivityFlowTest` calls `tokenRepository.clearToken()` in `setUp()`, which will log out a real device. Always target the emulator:

```bash
# Run all instrumented tests — emulator only
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest

# Run a single test class or method on emulator
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="com.example.MyTest#myMethod"
```

`ANDROID_SERIAL` is the correct way to pin `adb` to one device; `-Pandroid.device.serial` is not a standard Gradle flag and does not prevent other connected devices from being targeted.

### When to run instrumented tests

Instrumented tests are slow (~3–5 min). Don't run them on every change. Run them when:

- You add or modify a file in `app/src/androidTest/`
- You change back-navigation logic (anything touching `BackHandler`, `NavHost`, or `onBackPressed`)
- You change token storage (`KeychainTokenCipher`, `KeystoreTokenCipher`, `TokenRepository`)
- Before committing a batch of changes that touch screens or navigation

For everything else — ViewModel logic, repositories, quiz engine, screen composition — the JVM tests are sufficient:

```bash
./gradlew :shared:testAndroidHostTest    # commonTest (fast)
./gradlew :app:testDebugUnitTest         # Robolectric (medium)
```

### Known Compose test limitations

- **`swipeUp()` on `anchoredDraggable` handles does not call toggle callbacks** — swipe drives internal drag state only; use `performClick()` to trigger `clickable(onClick = ...)` callbacks. Do not write tests that `swipeUp` and then assert a callback was called on a draggable handle.
- **`Espresso.pressBack()` with `BackHandler` in a bare `ComponentActivity` test** — unreliable on API 33+ (predictive back gesture). System-back interception is tested at the full-activity level in `MainActivityFlowTest` instead.
