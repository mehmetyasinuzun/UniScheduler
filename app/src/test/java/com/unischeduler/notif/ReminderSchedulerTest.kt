package com.unischeduler.notif

import com.unischeduler.data.model.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests for the day-of-week + time-offset arithmetic in
 * ReminderScheduler.nextOccurrenceMillis. The function is `internal` and
 * pure — no Android system services touched — so we can call it directly.
 */
class ReminderSchedulerTest {

    private fun entry(day: String, start: String) = ScheduleEntry(
        id = 1, day = day, startTime = start, endTime = "23:59"
    )

    @Test
    fun `next occurrence rolls forward to next week if today's slot has passed`() {
        val now = Calendar.getInstance()
        val today = dayName(now.get(Calendar.DAY_OF_WEEK)) ?: return
        // Pick a class that starts at 00:00 today — guaranteed in the past
        // unless we run at exactly midnight.
        val e = entry(today, "00:00")
        val target = ReminderScheduler.nextOccurrenceMillis(e, offsetMin = 30)
        // Should be at least 6 days in the future (next week's same DOW).
        val sixDaysMs = 6L * 24 * 60 * 60 * 1000
        assertTrue("Target $target should be > now + 6 days", target > now.timeInMillis + sixDaysMs)
    }

    @Test
    fun `unknown day returns Long MAX_VALUE`() {
        val e = entry("Bayram", "10:00")
        assertEquals(Long.MAX_VALUE, ReminderScheduler.nextOccurrenceMillis(e, 30))
    }

    @Test
    fun `offset moves trigger backwards`() {
        val e = entry("Monday", "10:00")
        val target30 = ReminderScheduler.nextOccurrenceMillis(e, offsetMin = 30)
        val target60 = ReminderScheduler.nextOccurrenceMillis(e, offsetMin = 60)
        // 60-minute reminder fires earlier (smaller millis) than 30-minute.
        assertTrue("60m offset should be 30m earlier than 30m offset",
            target60 == target30 - 30 * 60 * 1000L)
    }

    @Test
    fun `next occurrence falls on the requested day-of-week`() {
        val e = entry("Wednesday", "14:30")
        val target = ReminderScheduler.nextOccurrenceMillis(e, offsetMin = 30)
        val cal = Calendar.getInstance().apply { timeInMillis = target }
        assertEquals(Calendar.WEDNESDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    private fun dayName(dow: Int): String? = when (dow) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> null
    }
}
