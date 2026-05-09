package com.unischeduler.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the quiet-hours window logic. The class internally
 * uses SharedPreferences but the only interesting behaviour
 * (`shouldSuppress`) is bit-banging on Ints — we test it by isolating that
 * function into a helper that mirrors the real implementation, so we don't
 * need to mock Android system services.
 *
 * If [NotificationPreferences.shouldSuppress] is changed, this helper must
 * change too. Trade-off: simpler test infra (no Robolectric) vs. mirror
 * drift risk. Given the function is ~10 lines and rarely changes, the
 * mirror is safer than pulling in Robolectric for one class.
 */
class NotificationPreferencesTest {

    /** Mirror of NotificationPreferences.shouldSuppress logic. */
    private fun suppress(
        minutesFromMidnight: Int,
        enabled: Boolean = true,
        quietEnabled: Boolean = false,
        start: Int = 0,
        end: Int = 0
    ): Boolean {
        if (!enabled) return true
        if (!quietEnabled) return false
        if (start == end) return false
        return if (start < end) {
            minutesFromMidnight in start until end
        } else {
            minutesFromMidnight >= start || minutesFromMidnight < end
        }
    }

    @Test
    fun `defaults constants are reasonable`() {
        assertTrue(NotificationPreferences.DEFAULT_ENABLED)
        assertEquals(30, NotificationPreferences.DEFAULT_REMINDER_MIN)
        assertFalse(NotificationPreferences.DEFAULT_QUIET_ENABLED)
        assertEquals(22 * 60, NotificationPreferences.DEFAULT_QUIET_START)
        assertEquals(7 * 60, NotificationPreferences.DEFAULT_QUIET_END)
    }

    @Test
    fun `allowed reminder offsets contain common choices`() {
        val expected = setOf(15, 30, 60, 120)
        assertEquals(expected, NotificationPreferences.ALLOWED_REMINDER_MINUTES.toSet())
    }

    @Test
    fun `notifications disabled suppresses everything`() {
        assertTrue(suppress(0,    enabled = false))
        assertTrue(suppress(720,  enabled = false))
        assertTrue(suppress(1439, enabled = false))
    }

    @Test
    fun `quiet hours disabled allows all times`() {
        assertFalse(suppress(0,    quietEnabled = false))
        assertFalse(suppress(720,  quietEnabled = false))
        assertFalse(suppress(1439, quietEnabled = false))
    }

    @Test
    fun `non-wrapping window 13 to 15 suppresses inside only`() {
        val s = 13 * 60; val e = 15 * 60
        assertFalse(suppress(12 * 60 + 59, quietEnabled = true, start = s, end = e))
        assertTrue (suppress(13 * 60,      quietEnabled = true, start = s, end = e))  // inclusive start
        assertTrue (suppress(14 * 60,      quietEnabled = true, start = s, end = e))
        assertFalse(suppress(15 * 60,      quietEnabled = true, start = s, end = e))  // exclusive end
        assertFalse(suppress(16 * 60,      quietEnabled = true, start = s, end = e))
    }

    @Test
    fun `wrapping window 22 to 07 suppresses across midnight`() {
        val s = 22 * 60; val e = 7 * 60
        assertTrue (suppress(22 * 60,      quietEnabled = true, start = s, end = e))
        assertTrue (suppress(23 * 60 + 30, quietEnabled = true, start = s, end = e))
        assertTrue (suppress(0,            quietEnabled = true, start = s, end = e))
        assertTrue (suppress(3 * 60,       quietEnabled = true, start = s, end = e))
        assertTrue (suppress(6 * 60 + 59,  quietEnabled = true, start = s, end = e))
        assertFalse(suppress(7 * 60,       quietEnabled = true, start = s, end = e))
        assertFalse(suppress(12 * 60,      quietEnabled = true, start = s, end = e))
        assertFalse(suppress(21 * 60 + 59, quietEnabled = true, start = s, end = e))
    }

    @Test
    fun `empty window start equals end never suppresses`() {
        assertFalse(suppress(600, quietEnabled = true, start = 600, end = 600))
        assertFalse(suppress(0,   quietEnabled = true, start = 0,   end = 0))
    }
}
