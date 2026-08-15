package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

/** Returns a lambda that, when invoked, asks the OS for POST_NOTIFICATIONS permission.
 *  [onResult] is called with `true` if already granted or newly granted, `false` if denied.
 *  Android: uses ActivityResultContracts.RequestPermission (required on API 33+).
 *  iOS: uses UNUserNotificationCenter.requestAuthorization. */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
