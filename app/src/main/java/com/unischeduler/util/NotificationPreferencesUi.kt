// NotificationPreferencesUi — wires the card_notification_preferences.xml
// include block to the NotificationPreferences store.
//
// Used from LecturerHome (the only role that gets reminders). Centralised
// here so a future preference (e.g. snooze duration) only needs adding in
// one place + the layout, not in every host fragment.
package com.unischeduler.util

import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.unischeduler.R
import com.unischeduler.notif.ReminderScheduler

object NotificationPreferencesUi {

    /**
     * Bind the notification-preferences card. The host passes in [root]
     * = the root of the included card_notification_preferences layout
     * and we wire up children by id.
     *
     * @param permissionLauncher  optional launcher provided by a Fragment
     *   for requesting POST_NOTIFICATIONS at runtime (Android 13+). When
     *   the user toggles master ON we fire it; the host's callback then
     *   updates the switch state if denied.
     * @param onAnyChange  fires whenever the user changes a value. Host
     *   typically uses it to call ReminderScheduler.rescheduleAll() so
     *   tomorrow's alarms reflect the new settings.
     */
    fun bind(
        root: View,
        prefs: NotificationPreferences,
        permissionLauncher: ActivityResultLauncher<String>? = null,
        onAnyChange: () -> Unit = {}
    ) {
        val ctx = root.context
        val swMaster   = root.findViewById<MaterialSwitch>(R.id.swNotifMaster)
        val rb15       = root.findViewById<android.widget.RadioButton>(R.id.rbNotif15)
        val rb30       = root.findViewById<android.widget.RadioButton>(R.id.rbNotif30)
        val rb60       = root.findViewById<android.widget.RadioButton>(R.id.rbNotif60)
        val rb120      = root.findViewById<android.widget.RadioButton>(R.id.rbNotif120)
        val rgOffset   = root.findViewById<android.widget.RadioGroup>(R.id.rgNotifOffset)
        val swQuiet    = root.findViewById<MaterialSwitch>(R.id.swQuietHours)
        val rowQuiet   = root.findViewById<View>(R.id.llQuietHoursRow)
        val btnStart   = root.findViewById<MaterialButton>(R.id.btnQuietStart)
        val btnEnd     = root.findViewById<MaterialButton>(R.id.btnQuietEnd)

        // ── Initial population ──
        swMaster.isChecked = prefs.enabled
        when (prefs.reminderMinutes) {
            15  -> rb15.isChecked = true
            30  -> rb30.isChecked = true
            60  -> rb60.isChecked = true
            120 -> rb120.isChecked = true
        }
        swQuiet.isChecked = prefs.quietHoursEnabled
        rowQuiet.visibility = if (prefs.quietHoursEnabled) View.VISIBLE else View.GONE
        btnStart.text = formatHm(prefs.quietHoursStartMinutes)
        btnEnd.text   = formatHm(prefs.quietHoursEndMinutes)

        // Disable / enable sub-controls based on master switch — visual hint
        // that "off" means everything is off, not just one thing.
        applyMasterEnabled(swMaster.isChecked, rgOffset, swQuiet, rowQuiet)

        // ── Listeners ──
        swMaster.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            applyMasterEnabled(checked, rgOffset, swQuiet, rowQuiet)
            // Android 13+ runtime permission. Only ask when the user has
            // just turned reminders ON — turning OFF doesn't need permission
            // and asking would feel intrusive.
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            onAnyChange()
        }

        rgOffset.setOnCheckedChangeListener { _, id ->
            val mins = when (id) {
                R.id.rbNotif15  -> 15
                R.id.rbNotif30  -> 30
                R.id.rbNotif60  -> 60
                R.id.rbNotif120 -> 120
                else            -> return@setOnCheckedChangeListener
            }
            prefs.reminderMinutes = mins
            onAnyChange()
        }

        swQuiet.setOnCheckedChangeListener { _, checked ->
            prefs.quietHoursEnabled = checked
            rowQuiet.visibility = if (checked) View.VISIBLE else View.GONE
            onAnyChange()
        }

        btnStart.setOnClickListener {
            pickTime(ctx, prefs.quietHoursStartMinutes) { newMins ->
                prefs.quietHoursStartMinutes = newMins
                btnStart.text = formatHm(newMins)
                onAnyChange()
            }
        }
        btnEnd.setOnClickListener {
            pickTime(ctx, prefs.quietHoursEndMinutes) { newMins ->
                prefs.quietHoursEndMinutes = newMins
                btnEnd.text = formatHm(newMins)
                onAnyChange()
            }
        }
    }

    private fun applyMasterEnabled(
        enabled: Boolean,
        rgOffset: android.widget.RadioGroup,
        swQuiet: MaterialSwitch,
        rowQuiet: View
    ) {
        // Recursively grey out children when master is off — RadioGroup
        // doesn't propagate isEnabled to its RadioButton children.
        rgOffset.isEnabled = enabled
        for (i in 0 until rgOffset.childCount) rgOffset.getChildAt(i).isEnabled = enabled
        swQuiet.isEnabled = enabled
        rowQuiet.alpha = if (enabled) 1f else 0.4f
    }

    private fun pickTime(ctx: Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
        val hour = currentMinutes / 60
        val min  = currentMinutes % 60
        TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, hour, min, true).show()
    }

    private fun formatHm(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    /** Convenience: ask the scheduler to refresh after a settings change.
     *  Lives here (not at the call sites) so neither fragment needs to
     *  know about ReminderScheduler internals. */
    fun rescheduleAfterChange(context: Context) {
        ReminderScheduler.scheduleNextDayReminders(context.applicationContext)
    }
}
