// NotificationPreferences — typed wrapper around the local-notification
// settings. Stored in app_prefs (the same SharedPreferences file used for
// theme + language) so a single backup/clear takes everything with it.
//
// All five preferences have safe defaults so a brand-new install (or a
// user who never visits the settings screen) still gets sensible
// behaviour: notifications on, 30 min before class, no quiet hours.
//
// Quiet hours store wall-clock minutes-from-midnight (0..1439) instead of
// "HH:MM" strings to avoid string parsing in hot paths
// (NotificationHelper.shouldSuppressNow() runs on every alarm fire).
package com.unischeduler.util

import android.content.Context
import android.content.SharedPreferences
import com.unischeduler.App
import java.util.Calendar

class NotificationPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Minutes before class to fire the reminder. One of {15, 30, 60, 120}. */
    var reminderMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_MIN, DEFAULT_REMINDER_MIN)
        set(value) {
            require(value in ALLOWED_REMINDER_MINUTES) { "Unsupported reminder offset: $value" }
            prefs.edit().putInt(KEY_REMINDER_MIN, value).apply()
        }

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, DEFAULT_QUIET_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    /** Quiet-hours start, minutes from midnight (0..1439). Default 22:00 = 1320. */
    var quietHoursStartMinutes: Int
        get() = prefs.getInt(KEY_QUIET_START, DEFAULT_QUIET_START)
        set(value) = prefs.edit().putInt(KEY_QUIET_START, value.coerceIn(0, 1439)).apply()

    /** Quiet-hours end, minutes from midnight. Default 07:00 = 420. */
    var quietHoursEndMinutes: Int
        get() = prefs.getInt(KEY_QUIET_END, DEFAULT_QUIET_END)
        set(value) = prefs.edit().putInt(KEY_QUIET_END, value.coerceIn(0, 1439)).apply()

    /**
     * Should a reminder fired at [minutesFromMidnight] be suppressed because
     * we're inside the configured quiet-hours window?
     *
     * Window can wrap midnight (e.g. start=22:00, end=07:00 means quiet from
     * 22:00 today through 07:00 tomorrow). Both endpoints are inclusive on
     * start and exclusive on end — matches Do-Not-Disturb conventions on
     * iOS / Google Calendar.
     */
    fun shouldSuppress(minutesFromMidnight: Int): Boolean {
        if (!enabled) return true
        if (!quietHoursEnabled) return false
        val start = quietHoursStartMinutes
        val end = quietHoursEndMinutes
        if (start == end) return false  // empty window
        return if (start < end) {
            minutesFromMidnight in start until end
        } else {
            // window wraps midnight
            minutesFromMidnight >= start || minutesFromMidnight < end
        }
    }

    /** Convenience — checks shouldSuppress against the device's current wall clock. */
    fun shouldSuppressNow(): Boolean {
        val cal = Calendar.getInstance()
        return shouldSuppress(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    companion object {
        const val KEY_ENABLED       = "notif_enabled"
        const val KEY_REMINDER_MIN  = "notif_reminder_minutes"
        const val KEY_QUIET_ENABLED = "notif_quiet_enabled"
        const val KEY_QUIET_START   = "notif_quiet_start_min"
        const val KEY_QUIET_END     = "notif_quiet_end_min"

        const val DEFAULT_ENABLED       = true
        const val DEFAULT_REMINDER_MIN  = 30
        const val DEFAULT_QUIET_ENABLED = false
        const val DEFAULT_QUIET_START   = 22 * 60  // 22:00
        const val DEFAULT_QUIET_END     = 7  * 60  // 07:00

        val ALLOWED_REMINDER_MINUTES = listOf(15, 30, 60, 120)
    }
}
