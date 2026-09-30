package com.crazyfluff.shellfstudy.shared.designsystem.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/** The "j" skeleton expands to the locale's preferred hour field, and the user's 24-Hour Time
 *  toggle overrides it — so the expanded pattern carries an "a" (AM/PM marker) exactly when the
 *  device shows a 12-hour clock. */
@Composable
actual fun rememberIs24HourClock(): Boolean = remember {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", options = 0u, locale = NSLocale.currentLocale)
    pattern?.contains('a') != true
}
