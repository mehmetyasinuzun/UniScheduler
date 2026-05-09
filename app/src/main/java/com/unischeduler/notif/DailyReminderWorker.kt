// DailyReminderWorker — runs once a day at ~23:00 to refresh the next
// 36 hours of class reminders.
//
// Why WorkManager and not just a single AlarmManager + setInexactRepeating?
//   • WorkManager survives app upgrades automatically.
//   • Doze-aware: WorkManager keeps periodic constraints across maintenance
//     windows so we don't need to worry about Android killing our process.
//   • Uniform with future background sync work (e.g. nightly schedule
//     pre-fetch) — adding more periodic jobs later is a one-liner.
//
// The worker itself does almost nothing — it delegates to ReminderScheduler
// which knows how to query the schedule and arm one-shot AlarmManager
// alarms. Splitting like this keeps the test boundary clean:
// ReminderScheduler.nextOccurrenceMillis is unit-testable in pure JVM.
package com.unischeduler.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleNextDayReminders(applicationContext)
        return Result.success()
    }
}
