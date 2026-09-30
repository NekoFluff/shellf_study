package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

/** The installed build, as the platform reports it: Android's `versionName`/`versionCode`, iOS's
 *  `CFBundleShortVersionString`/`CFBundleVersion`. */
data class AppVersion(val name: String, val build: String) {
    /** "1.13 (14)": the version people quote, with the build number that tells two uploads of it apart. */
    val label: String get() = "$name ($build)"
}

/** The running app's [AppVersion], or null if the platform won't say (a test host with no package
 *  info). Callers hide the version rather than showing a placeholder. */
@Composable
expect fun rememberAppVersion(): AppVersion?
