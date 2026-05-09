// NotificationHelper — single owner of UniScheduler's notification channels
// + show() entry point used by ReminderReceiver.
//
// Design notes:
//  • Channel is created once on App.onCreate (idempotent — Android dedupes).
//    Doing it lazily on first notify() also works, but eager creation makes
//    the channel show up in system Settings → Notifications immediately,
//    which lets users mute UniScheduler reminders independently of master
//    enable/disable inside the app.
//  • Importance.HIGH chosen because students expect a heads-up banner for
//    "your class starts in 30 min". DEFAULT would silently drop into the
//    notification drawer.
//  • content intent re-launches MainActivity so tapping the notification
//    deep-links straight to the app (which then routes to LecturerHome /
//    AdminHome based on session). No separate deep-link path needed.
package com.unischeduler.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unischeduler.MainActivity
import com.unischeduler.R
import com.unischeduler.util.NotificationPreferences

object NotificationHelper {

    const val CHANNEL_REMINDER_ID = "unischeduler_reminders"

    /** Idempotent — call from App.onCreate. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_REMINDER_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_REMINDER_ID,
            context.getString(R.string.notif_channel_reminder_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_reminder_description)
            enableLights(true)
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }

    /**
     * Show a reminder for one schedule entry. Honours both the master
     * notifications-enabled toggle and quiet-hours window.
     *
     * @param entryId stable id of the schedule_entries row — used as
     *                notification id so re-firing replaces the previous
     *                notification instead of stacking.
     */
    fun showReminder(
        context: Context,
        entryId: Int,
        courseLabel: String,
        startTime: String,
        classroomCode: String?
    ) {
        val prefs = NotificationPreferences(context)
        if (prefs.shouldSuppressNow()) return

        // POST_NOTIFICATIONS runtime permission — Android 13+. Without it,
        // notify() is a silent no-op (NotificationManagerCompat checks).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val title = context.getString(R.string.notif_reminder_title, courseLabel, startTime)
        val body  = if (!classroomCode.isNullOrBlank()) {
            context.getString(R.string.notif_reminder_body, classroomCode, startTime)
        } else {
            context.getString(R.string.notif_reminder_body_no_room, startTime)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entryId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(entryId, notif)
        }.onFailure { e ->
            // Most failures here are transient: SecurityException (rate limit
            // exceeded on some OEMs), RemoteException (notification service
            // restarting). Logging is enough — there's nothing to retry
            // because we're already past the alarm trigger point.
            android.util.Log.w("NotificationHelper", "notify(entryId=$entryId) failed", e)
        }
    }
}
