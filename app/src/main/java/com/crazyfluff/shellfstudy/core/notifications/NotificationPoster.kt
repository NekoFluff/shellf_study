package com.crazyfluff.shellfstudy.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crazyfluff.shellfstudy.MainActivity
import com.crazyfluff.shellfstudy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one genuinely Android/untestable seam of the notification system — isolated behind an
 * interface the same way [com.crazyfluff.shellfstudy.core.sync.SyncScheduler] isolates WorkManager,
 * so [NotificationCoordinator] can be tested against a fake instead of the real
 * `NotificationManagerCompat`.
 */
interface NotificationPoster {
    fun canPost(): Boolean
    fun post(spec: NotificationSpec)
    fun cancel(id: Int)
}

@Singleton
class SystemNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationPoster {

    override fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun post(spec: NotificationSpec) {
        // Duplicates canPost()'s check inline (rather than delegating to it) because Android
        // Lint's MissingPermission analysis only recognizes a checkSelfPermission guard in the
        // same method as the notify() call it protects — it doesn't follow the check across a
        // separate function call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationDeepLink.EXTRA_DESTINATION, spec.destination)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            spec.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setPriority(spec.priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(spec.id, notification)
    }

    override fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
