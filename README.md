# Shellf Study

Another [WaniKani](https://www.wanikani.com/) client for Android. Aim to be modern and minimalistic. Written/generated primarily using Claude.

## Stack

- Kotlin + Jetpack Compose (Material 3), no XML layouts
- MVVM / unidirectional data flow: `ViewModel` exposes a single `StateFlow<UiState>`, screens are
  stateless composables that take `uiState` + callback lambdas
- Hilt for dependency injection
- Retrofit + OkHttp + kotlinx.serialization for the WaniKani API v2 client
- Room for offline caching of subjects/assignments
- DataStore (Preferences) for the WaniKani API token, encrypted at rest via an AES-GCM key held in
  the Android Keystore
- Navigation-Compose for screen-to-screen navigation

## Package structure

Organized by feature:

```
core/network/        WaniKani API v2 Retrofit interface, DTOs, auth interceptor, Hilt network module
core/database/        Room database, entities, DAOs
core/data/            Repositories that combine network + database, domain models, token storage
core/designsystem/    Compose theme (colors, typography)
feature/auth/         API token entry + validation screen
feature/dashboard/    User info, lesson/review counts
feature/review/       Review session (quiz flow, SRS grading, submission)
navigation/           NavHost wiring the above screens together
```

## Getting started

1. Create a [WaniKani API v2 token](https://www.wanikani.com/settings/personal_access_tokens)
   (read-only is enough to browse; review submission needs the `assignments:start` and
   `reviews:create` scopes).
2. Open the project in Android Studio and let Gradle sync.
3. Run the `app` module on an emulator or device, then enter your API token on the login screen.

## Build & test

```bash
./gradlew build                    # compile + lint
./gradlew testDebugUnitTest        # JVM unit tests (ViewModels, repositories, MockWebServer)
./gradlew connectedAndroidTest     # instrumented tests (Compose UI tests, Espresso, Keystore) — needs a running emulator/device
```

## License

MIT — see [LICENSE](LICENSE).
