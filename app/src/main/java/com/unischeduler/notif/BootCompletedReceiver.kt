// BootCompletedReceiver — re-arms reminder alarms after a device reboot
// or after the package is replaced (APK update).
//
// AlarmManager loses its queue across reboots, so without this receiver
// every restart silently drops upcoming reminders until the next 23:00
// WorkManager run. With it, the user gets reliable reminders within a
// few seconds of the device coming back up.
//
// We also handle MY_PACKAGE_REPLACED — the OS sends this exactly once,
// to the new APK, after a self-update finishes. This means a fresh
// install via APK side-load (or a Play Store update) immediately re-arms
// without the user having to open the app first.
package com.unischeduler.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderScheduler.ensureDailyWorker(context.applicationContext)
                ReminderScheduler.scheduleNextDayReminders(context.applicationContext)
            }
        }
    }
}
