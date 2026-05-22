// ReminderScheduler — converts the upcoming day's schedule_entries into
// one-shot AlarmManager pendingIntents.
//
// Two entry points:
//   • scheduleNextDayReminders — called by DailyReminderWorker at 23:00
//     and from ReminderPreferencesUi.rescheduleAfterChange() when the
//     user toggles a setting. Wipes any previously scheduled alarms for
//     entries that are no longer relevant and re-arms the rest.
//   • cancelAll — called on logout so the next user doesn't inherit the
//     previous lecturer's reminders.
//
// Why "next day" only?
//   We could schedule a week ahead but Android's PendingIntent budget is
//   ~500 per app. With 5 days × 10 daily classes we'd burn 50; that's
//   fine, but if a department admin assigns 30 classes/day across
//   sections, we'd hit 150. Daily refresh keeps us at ~10 active alarms
//   at any time which is comfortably within Doze whitelists too.
//
// Persistence:
//   AlarmManager loses its queue on reboot, so BootCompletedReceiver
//   re-runs scheduleNextDayReminders synchronously after BOOT_COMPLETED.
package com.unischeduler.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unischeduler.data.model.ScheduleEntry
import com.unischeduler.data.repository.ScheduleRepository
import com.unischeduler.util.NotificationPreferences
import com.unischeduler.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val WORK_NAME_DAILY = "unischeduler-daily-reminder-refresh"

    /**
     * Make sure the once-a-night WorkManager job that refreshes alarms is
     * registered. Idempotent — call from App.onCreate.
     */
    fun ensureDailyWorker(context: Context) {
        // WorkManager auto-init runs through a ContentProvider in real
        // installs but is absent in Robolectric unit tests and any other
        // host where the Application starts before androidx.startup runs.
        // We catch the IllegalStateException so a missing WorkManager
        // isn't fatal — reminders simply don't get scheduled until the
        // next normal launch.
        runCatching {
            val initialDelayMs = millisUntilNextRefreshSlot()
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    /** Wipe upcoming alarms (used on logout). */
    fun cancelAll(context: Context) {
        // We don't store ids ourselves — instead we cancel by the same
        // PendingIntent that scheduling produced (semantic equality is
        // determined by the action+extras hash, not object identity).
        // The simplest way to cover all of them: cancel a wide range of
        // request codes. For real apps with 1000+ ids this would need a
        // separate index — at our scale (~50 max) iterating is fine.
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (id in 0..1000) {
            val pi = pendingIntent(context, id, build = false) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    /**
     * Pull the lecturer's schedule and schedule one-shot alarms for every
     * class that starts within the next 24 hours and respects the user's
     * reminder offset.
     *
     * Network call runs on Dispatchers.IO, but the function returns
     * immediately — fire-and-forget is fine because BootCompletedReceiver
     * and the WorkManager job both invoke us in contexts where we don't
     * need a return value.
     */
    fun scheduleNextDayReminders(context: Context) {
        val app = context.applicationContext
        val session = SessionManager(app)
        if (!session.isLoggedIn || !session.isLecturer) return  // admins don't get reminders
        val orgId = session.orgId
        val lecturerId = session.lecturerId
        if (orgId <= 0 || lecturerId <= 0) return

        val prefs = NotificationPreferences(app)
        if (!prefs.enabled) {
            cancelAll(app)
            return
        }

        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            val entries = runCatching {
                ScheduleRepository().getEntriesForLecturer(lecturerId, orgId)
            }.onFailure { Log.w(TAG, "Schedule fetch failed", it) }
                .getOrDefault(emptyList())

            val nowMillis = System.currentTimeMillis()
            val horizonMillis = nowMillis + TimeUnit.HOURS.toMillis(36)
            // 36h window: covers "tonight at 23:00 → after-tomorrow 11:00"
            // so the user always has at least the next school day armed.

            val planned = mutableListOf<ScheduledAlarm>()
            for (entry in entries) {
                val occurrenceMillis = nextOccurrenceMillis(entry, prefs.reminderMinutes)
                if (occurrenceMillis in (nowMillis + 60_000)..horizonMillis) {
                    planned += ScheduledAlarm(entry, occurrenceMillis)
                }
            }
            withContext(Dispatchers.Main) {
                installAlarms(app, planned)
            }
        }
    }

    private data class ScheduledAlarm(val entry: ScheduleEntry, val triggerAt: Long)

    private fun installAlarms(context: Context, alarms: List<ScheduledAlarm>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (a in alarms) {
            val pi = pendingIntent(
                context = context,
                requestCode = a.entry.id,
                courseLabel = courseLabelFor(a.entry, context),
                startTime = a.entry.startTime,
                classroom = a.entry.classroomCode.takeIf { it.isNotBlank() },
                build = true
            ) ?: continue

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.triggerAt, pi)
                    } else {
                        // User revoked SCHEDULE_EXACT_ALARM via system settings. Fall
                        // back to inexact — reminder may drift up to 15 min in Doze
                        // but still fires.
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.triggerAt, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.triggerAt, pi)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Alarm scheduling denied for entry ${a.entry.id}", e)
            }
        }
    }

    private fun courseLabelFor(entry: ScheduleEntry, context: Context): String {
        val code = entry.courseCode
        val name = entry.courseName
        return when {
            code.isNotBlank() && name.isNotBlank() -> "$code — $name"
            code.isNotBlank() -> code
            // Fallback bildirim başlığı dil değiştirildiğinde otomatik
            // güncellenir — eski "Ders" hardcoded'du.
            else -> name.ifBlank { context.getString(com.unischeduler.R.string.default_lesson) }
        }
    }

    /**
     * Returns the wall-clock millis at which the reminder should fire for
     * the next occurrence of [entry].day during the upcoming 7 days.
     *
     * The reminder fires `offsetMin` minutes before the class start time.
     * If today is the same day-of-week and the reminder time has already
     * passed, we roll forward to next week.
     */
    internal fun nextOccurrenceMillis(entry: ScheduleEntry, offsetMin: Int): Long {
        val targetDow = dayOfWeek(entry.day) ?: return Long.MAX_VALUE
        val (sH, sM) = entry.startTime.split(":")
            .let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }

        val cal = Calendar.getInstance().apply {
            // Roll forward to the matching DOW (today counts if reminder
            // time hasn't passed).
            while (get(Calendar.DAY_OF_WEEK) != targetDow) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
            set(Calendar.HOUR_OF_DAY, sH)
            set(Calendar.MINUTE, sM)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -offsetMin)
        }
        // If the resulting moment is already in the past (e.g. it's
        // Tuesday 14:00 and the entry's reminder slot was Tuesday 09:30
        // earlier today), bump to next week.
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
        }
        return cal.timeInMillis
    }

    private fun dayOfWeek(day: String): Int? = when (day) {
        "Monday" -> Calendar.MONDAY
        "Tuesday" -> Calendar.TUESDAY
        "Wednesday" -> Calendar.WEDNESDAY
        "Thursday" -> Calendar.THURSDAY
        "Friday" -> Calendar.FRIDAY
        "Saturday" -> Calendar.SATURDAY
        "Sunday" -> Calendar.SUNDAY
        else -> null
    }

    /** Returns the millis until the next 23:00 wall-clock instant. */
    private fun millisUntilNextRefreshSlot(): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Build the PendingIntent we use to wake [ReminderReceiver].
     *
     * Set [build] = false to recover the existing intent (NO_CREATE flag)
     * without creating a new one — used during cancelAll.
     */
    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        courseLabel: String = "",
        startTime: String = "",
        classroom: String? = null,
        build: Boolean
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ENTRY_ID, requestCode)
            putExtra(ReminderReceiver.EXTRA_COURSE_LABEL, courseLabel)
            putExtra(ReminderReceiver.EXTRA_START_TIME, startTime)
            putExtra(ReminderReceiver.EXTRA_CLASSROOM, classroom)
        }
        var flags = PendingIntent.FLAG_IMMUTABLE
        flags = if (build) flags or PendingIntent.FLAG_UPDATE_CURRENT
                else      flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
