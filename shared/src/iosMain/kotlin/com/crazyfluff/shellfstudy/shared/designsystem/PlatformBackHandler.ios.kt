package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

/**
 * Deliberately a no-op, and not because iOS handles this for us: the iOS host is a single Compose root
 * ([com.crazyfluff.shellfstudy.shared.IosEntry.MainViewController] inside `ContentView`), not a
 * `UINavigationController` or a SwiftUI `NavigationStack`, so there is no system back action for this
 * to intercept. The comment that used to sit here claimed otherwise, which would have hidden a real
 * gap: every screen relying on this must be dismissible by an on-screen affordance instead.
 *
 * The two call sites do have one — [com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet]
 * has both a back arrow and a Close button, and
 * [com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay] has its own dismiss control —
 * so this stays a no-op rather than pretending. Wiring a real back gesture would mean hosting Compose
 * in a navigation controller, which is a change to the iOS host, not to this function.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
