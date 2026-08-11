package com.crazyfluff.shellfstudy.core.notifications

/**
 * Intent-extra contract between a posted notification's tap target and [com.crazyfluff.shellfstudy.MainActivity].
 * Kept dependency-free (no Android imports) so both `core/notifications` and the root
 * `MainActivity`/`navigation` package can reference it without an awkward dependency direction.
 */
object NotificationDeepLink {
    const val EXTRA_DESTINATION = "notification_destination"
    const val DESTINATION_REVIEW = "review"
    const val DESTINATION_LESSON = "lesson"
    const val DESTINATION_DASHBOARD = "dashboard"
}
