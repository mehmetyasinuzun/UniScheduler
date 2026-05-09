package com.unischeduler.util

import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.ScheduleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    private fun sampleEntry(
        id: Int = 1,
        day: String = "Monday",
        start: String = "09:00",
        end: String = "11:00",
        courseCode: String = "CS101",
        courseName: String = "Algoritmalar"
    ) = ScheduleEntry(
        id = id,
        day = day,
        startTime = start,
        endTime = end,
        offerings = Offering(
            id = id,
            courseId = id,
            term = "Fall",
            classYear = 2,
            section = "A",
            courses = Course(id = id, code = courseCode, name = courseName)
        ),
        lecturers = Lecturer(id = id, firstName = "Ayşe", lastName = "Yılmaz"),
        classrooms = null
    )

    @Test
    fun `export wraps content in VCALENDAR envelope`() {
        val ics = IcsExporter.export(listOf(sampleEntry()))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("PRODID:-//UniScheduler//EN"))
    }

    @Test
    fun `every event has UID DTSTAMP DTSTART DTEND RRULE`() {
        val ics = IcsExporter.export(listOf(sampleEntry(), sampleEntry(id = 2, day = "Tuesday")))
        assertEquals(2, ics.split("BEGIN:VEVENT").size - 1)
        assertEquals(2, "UID:".toRegex().findAll(ics).count())
        assertEquals(2, "DTSTAMP:".toRegex().findAll(ics).count())
        assertEquals(2, "DTSTART:".toRegex().findAll(ics).count())
        assertEquals(2, "DTEND:".toRegex().findAll(ics).count())
        assertEquals(2, "RRULE:".toRegex().findAll(ics).count())
    }

    @Test
    fun `UID is deterministic and uses entry id`() {
        val ics = IcsExporter.export(listOf(sampleEntry(id = 42)))
        assertTrue(ics.contains("UID:uni-scheduler-42@unischeduler.app"))
    }

    @Test
    fun `RRULE BYDAY maps Monday to MO`() {
        val ics = IcsExporter.export(listOf(sampleEntry(day = "Monday")))
        assertTrue(ics.contains("BYDAY=MO"))
    }

    @Test
    fun `RRULE BYDAY maps Friday to FR`() {
        val ics = IcsExporter.export(listOf(sampleEntry(day = "Friday")))
        assertTrue(ics.contains("BYDAY=FR"))
    }

    @Test
    fun `unknown day is skipped silently`() {
        val ics = IcsExporter.export(listOf(sampleEntry(day = "Bayram")))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `escape handles RFC 5545 reserved characters`() {
        assertEquals("a\\,b", IcsExporter.escape("a,b"))
        assertEquals("a\\;b", IcsExporter.escape("a;b"))
        assertEquals("a\\\\b", IcsExporter.escape("a\\b"))
        assertEquals("line1\\nline2", IcsExporter.escape("line1\nline2"))
    }

    @Test
    fun `summary includes course code and name`() {
        val ics = IcsExporter.export(listOf(sampleEntry(courseCode = "CS101", courseName = "Algo")))
        assertTrue(ics.contains("SUMMARY:CS101 — Algo"))
    }

    @Test
    fun `lines are CRLF terminated per RFC 5545`() {
        val ics = IcsExporter.export(listOf(sampleEntry()))
        // \r\n exists; \n alone (without preceding \r) must not.
        assertTrue(ics.contains("\r\n"))
        // every \n in the output must be preceded by \r (we use crlf helper exclusively)
        for ((i, ch) in ics.withIndex()) {
            if (ch == '\n') {
                assertTrue("Bare LF at index $i", i > 0 && ics[i - 1] == '\r')
            }
        }
    }

    @Test
    fun `default term is 14 weeks`() {
        val ics = IcsExporter.export(listOf(sampleEntry()))
        assertTrue(ics.contains("COUNT=14"))
    }
}
