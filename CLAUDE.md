# Shellf Study

A modern, ground-up rewrite of a WaniKani (Japanese kanji/vocabulary SRS) client, built as a
successor to the old Java/View-based "Smouldering Durtles" / "Flaming Durtles" apps. This is the
first milestone only: project scaffold, architecture, login, dashboard, and a working review
session. Self-study, audio, fonts, theming, notifications, widgets, and backup/restore are not
implemented yet.

## Stack

- Kotlin + Jetpack Compose (Material 3), no XML layouts
- MVVM / unidirectional data flow: `ViewModel` exposes a single `StateFlow<UiState>`, screens are
  stateless composables that take `uiState` + callback lambdas
- Hilt for dependency injection
- Retrofit + OkHttp + kotlinx.serialization for the WaniKani API v2 client
- Room for offline caching of subjects/assignments
- DataStore (Preferences) for the WaniKani API token, encrypted at rest via an AES-GCM key held in
  the Android Keystore (`core/data/TokenCipher.kt`)
- Navigation-Compose for screen-to-screen navigation

## Package structure

Organized by feature, not by layer:

```
core/network/       WaniKani API v2 Retrofit interface, DTOs, auth interceptor, Hilt network module
core/database/       Room database, entities, DAOs
core/data/           Repositories that combine network + database, domain models, token storage
core/designsystem/   Compose theme (colors, typography)
feature/auth/        API token entry + validation screen
feature/dashboard/    User info, lesson/review counts
feature/review/       Review session (quiz flow, SRS grading, submission)
navigation/          NavHost wiring the above screens together
```

## Build & test

```bash
./gradlew build                    # compile + lint
./gradlew testDebugUnitTest        # JVM unit tests (ViewModels, repositories, MockWebServer)
./gradlew connectedAndroidTest     # instrumented tests (Compose UI tests, Espresso, Keystore) — needs a running emulator/device
```

## Testing approach

- **Unit tests** (`src/test`): ViewModels and repositories tested against real collaborators where
  practical — `MockWebServer` for the WaniKani API, in-memory fake Room DAOs
  (`fakes/FakeDaos.kt`), a temp-file-backed real `DataStore`, and a no-op `FakeTokenCipher` (the
  real Keystore-backed cipher can't run on the host JVM). `Turbine` is used to assert `StateFlow`
  emissions in order.
- **Instrumented tests** (`src/androidTest`): Compose UI tests
  (`androidx.compose.ui.test.junit4`) drive each screen composable directly by state, and Espresso
  (`Espresso.pressBack()`) is used for system-level interactions Compose has no native API for.
  `AndroidKeystoreTokenCipherTest` runs on-device because the Android Keystore provider isn't
  available under Robolectric/the host JVM.
- Every screen has both: ViewModel/business-logic coverage (correct/incorrect grading, requeue
  behavior, error states) and UI coverage (rendering, button/text-field interaction).

## WaniKani API notes

- Base URL: `https://api.wanikani.com/v2/`, auth via `Authorization: Bearer <token>`, and every
  request needs a `Wanikani-Revision: 20170710` header (see `AuthInterceptor`).
- DTOs use `ignoreUnknownKeys = true` / `coerceInputValues = true` defensively — the real API has
  more fields than this app currently models. Cross-check field names against
  https://docs.api.wanikani.com/20170710/ before trusting a DTO field that hasn't been exercised
  against the live API yet.
- The review submission grading in `WaniKaniRepository.submitReview` is simplified to "had any
  incorrect attempt" (0 or 1) rather than the exact incorrect-answer count WaniKani tracks
  internally — good enough for correct SRS progression, not a byte-for-byte match of official
  client behavior.
