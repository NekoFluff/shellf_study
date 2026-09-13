package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Returns a lambda that, when invoked, asks the OS for POST_NOTIFICATIONS permission.
 *  [onResult] is called with `true` if already granted or newly granted, `false` if denied.
 *  Android: uses ActivityResultContracts.RequestPermission (required on API 33+).
 *  iOS: uses UNUserNotificationCenter.requestAuthorization. */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/**
 * The app-wide [rememberNotificationPermissionRequest], provided by `ShellfStudyApp`.
 *
 * A *factory* rather than a bound trigger, because the trigger a caller needs depends on the result
 * callback it wants to pass. Null means "no override": the consumer falls back to
 * [rememberNotificationPermissionRequest] itself, so this is additive — a composable rendered outside
 * the app root still asks for real, and only a test that wants to drive the grant/deny path provides
 * one. Same shape as `LocalShareText`.
 */
val LocalNotificationPermissionRequest =
    staticCompositionLocalOf<(@Composable (onResult: (Boolean) -> Unit) -> () -> Unit)?> { null }

/**
 * Resolves the trigger the caller should use: the app-wide override when one is provided, otherwise
 * the platform implementation. Both [SettingsRoute][com.crazyfluff.shellfstudy.shared.feature.settings.SettingsRoute]
 * and [AuthRoute][com.crazyfluff.shellfstudy.shared.feature.auth.AuthRoute] call this rather than
 * composing the two cases themselves.
 *
 * The platform implementation is only reached when no override is present, and that is load-bearing
 * rather than tidiness: its Android actual registers an `ActivityResultLauncher`, which needs an
 * `ActivityResultRegistryOwner` in the composition, so instantiating it unconditionally would make
 * this untestable outside an Activity-backed test.
 */
@Composable
fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val override = LocalNotificationPermissionRequest.current
    if (override != null) return override(onResult)
    return rememberNotificationPermissionRequest(onResult)
}
