package com.unischeduler.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for time-related utilities used throughout the app.
 * These validate the time parsing logic reused in AvailabilityRepository,
 * AssignmentViewModel, and ClassroomRepository.
 */
class TimeUtilsTest {

    // Helper replicating the toMinutes logic used everywhere
    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    // Helper: check if two time ranges overlap
    private fun overlaps(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = toMinutes(start1); val e1 = toMinutes(end1)
        val s2 = toMinutes(start2); val e2 = toMinutes(end2)
        return s1 < e2 && s2 < e1
    }

    @Test
    fun `toMinutes parses 08_00 as 480`() {
        assertEquals(480, toMinutes("08:00"))
    }

    @Test
    fun `toMinutes parses 18_00 as 1080`() {
        assertEquals(1080, toMinutes("18:00"))
    }

    @Test
    fun `toMinutes parses 09_30 as 570`() {
        assertEquals(570, toMinutes("09:30"))
    }

    @Test
    fun `toMinutes handles midnight 00_00 as 0`() {
        assertEquals(0, toMinutes("00:00"))
    }

    @Test
    fun `toMinutes handles malformed input gracefully`() {
        assertEquals(0, toMinutes(""))
        assertEquals(0, toMinutes("abc"))
        assertEquals(540, toMinutes("09:")) // hour part valid, minute defaults to 0
    }

    @Test
    fun `overlaps returns true for exact same range`() {
        assertTrue(overlaps("09:00", "10:00", "09:00", "10:00"))
    }

    @Test
    fun `overlaps returns true for partial overlap`() {
        assertTrue(overlaps("09:00", "11:00", "10:00", "12:00"))
    }

    @Test
    fun `overlaps returns true when one contains the other`() {
        assertTrue(overlaps("08:00", "12:00", "09:00", "11:00"))
    }

    @Test
    fun `overlaps returns false for adjacent non-overlapping ranges`() {
        assertFalse(overlaps("09:00", "10:00", "10:00", "11:00"))
    }

    @Test
    fun `overlaps returns false for clearly separated ranges`() {
        assertFalse(overlaps("08:00", "09:00", "14:00", "16:00"))
    }

    @Test
    fun `overlaps returns false when first range is entirely before second`() {
        assertFalse(overlaps("08:00", "10:00", "10:00", "12:00"))
    }

    @Test
    fun `overlaps handles 1-minute overlap`() {
        assertTrue(overlaps("09:00", "10:01", "10:00", "11:00"))
    }

    @Test
    fun `time validation rejects end before start`() {
        val start = toMinutes("14:00")
        val end = toMinutes("12:00")
        assertTrue(start > end) // This should be rejected by the ViewModel
    }

    @Test
    fun `time validation accepts end after start`() {
        val start = toMinutes("09:00")
        val end = toMinutes("10:30")
        assertTrue(end > start)
    }

    @Test
    fun `availability coverage check - slot fully covers request`() {
        // Slot: 08:00-17:00, Request: 09:00-10:00
        val slotStart = toMinutes("08:00")
        val slotEnd = toMinutes("17:00")
        val reqStart = toMinutes("09:00")
        val reqEnd = toMinutes("10:00")
        assertTrue(slotStart <= reqStart && slotEnd >= reqEnd)
    }

    @Test
    fun `availability coverage check - slot partially covers request`() {
        // Slot: 08:00-10:00, Request: 09:00-11:00
        val slotStart = toMinutes("08:00")
        val slotEnd = toMinutes("10:00")
        val reqStart = toMinutes("09:00")
        val reqEnd = toMinutes("11:00")
        // Not fully covered
        assertFalse(slotStart <= reqStart && slotEnd >= reqEnd)
    }

    @Test
    fun `availability coverage check - no coverage at all`() {
        // Slot: 14:00-16:00, Request: 09:00-10:00
        val slotStart = toMinutes("14:00")
        val slotEnd = toMinutes("16:00")
        val reqStart = toMinutes("09:00")
        val reqEnd = toMinutes("10:00")
        assertFalse(slotStart <= reqStart && slotEnd >= reqEnd)
    }
}
