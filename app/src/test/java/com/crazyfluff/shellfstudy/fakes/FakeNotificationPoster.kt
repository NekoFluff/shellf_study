package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.notifications.NotificationPoster
import com.crazyfluff.shellfstudy.core.notifications.NotificationSpec

/** In-memory stand-in for [NotificationPoster] — the real one needs a real Context/NotificationManager. */
class FakeNotificationPoster(private var canPostValue: Boolean = true) : NotificationPoster {
    val posted = mutableListOf<NotificationSpec>()
    val cancelled = mutableListOf<Int>()

    fun setCanPost(value: Boolean) {
        canPostValue = value
    }

    override fun canPost(): Boolean = canPostValue

    override fun post(spec: NotificationSpec) {
        if (canPostValue) posted.add(spec)
    }

    override fun cancel(id: Int) {
        cancelled.add(id)
    }
}
