// ReminderReceiver — fires when an AlarmManager pendingIntent we scheduled
// in ReminderScheduler reaches its trigger time.
//
// All we do here is read the schedule_entries metadata out of the intent
// extras (we serialised it at schedule time) and ask NotificationHelper
// to display. We deliberately DO NOT re-query the database here — alarm
// receivers run with a 10-second budget on Android 8+ and a network
// query could miss it. The trade-off is that the notification reflects
// the schedule as it was when scheduled (yesterday at 23:00); if an
// admin moves the class between then and now we'd notify the user about
// the old time. We accept that — the alternative (foreground service +
// runtime DB pull) is heavyweight for a feature with this much value.
package com.unischeduler.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId      = intent.getIntExtra(EXTRA_ENTRY_ID, -1)
        val courseLabel  = intent.getStringExtra(EXTRA_COURSE_LABEL).orEmpty()
        val startTime    = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val classroom    = intent.getStringExtra(EXTRA_CLASSROOM)
        if (entryId < 0 || courseLabel.isBlank() || startTime.isBlank()) return

        NotificationHelper.showReminder(
            context = context.applicationContext,
            entryId = entryId,
            courseLabel = courseLabel,
            startTime = startTime,
            classroomCode = classroom
        )
    }

    companion object {
        const val EXTRA_ENTRY_ID     = "entry_id"
        const val EXTRA_COURSE_LABEL = "course_label"
        const val EXTRA_START_TIME   = "start_time"
        const val EXTRA_CLASSROOM    = "classroom"
    }
}
