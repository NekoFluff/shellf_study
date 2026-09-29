package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

/** Intercepts the system/gesture back action when [enabled].
 *
 *  Android maps this to `BackHandler`. iOS is a no-op — there is no system back action to intercept
 *  in the single-Compose-root host it runs in (see the actual), so anything reachable only this way
 *  must also have an on-screen dismiss. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
