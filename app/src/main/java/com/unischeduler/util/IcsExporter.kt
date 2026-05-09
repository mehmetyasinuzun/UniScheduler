// IcsExporter — produces an RFC 5545 compliant iCalendar (.ics) feed of a
// weekly schedule, suitable for importing into Google Calendar, Apple
// Calendar, Outlook, etc.
//
// Design notes:
//  • Each schedule_entries row becomes a recurring weekly event (RRULE) for
//    a configurable number of weeks (default 14 — typical academic term).
//  • Times are emitted as floating local time (no Z suffix, no TZID
//    parameter) per RFC 5545 §3.3.5. This is the safest cross-app option:
//    target calendar app picks up the user's device timezone instead of
//    forcing a hardcoded TZID like "Europe/Istanbul" that downstream apps
//    sometimes don't recognise.
//  • UID is deterministic: `uni-scheduler-<entryId>@unischeduler.app`. This
//    means re-importing the same .ics replaces the previous events instead
//    of duplicating — matches Google / Outlook behaviour.
//  • Lines are CRLF terminated and folded at 75 octets per §3.1. We don't
//    fold field values (most aren't long enough) but DTSTART / DTEND lines
//    are short by construction.
package com.unischeduler.util

import com.unischeduler.data.model.ScheduleEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object IcsExporter {

    private const val UID_DOMAIN = "unischeduler.app"

    /** Default term length: 14 academic weeks. */
    const val DEFAULT_WEEKS = 14

    /**
     * Produce an RFC 5545 .ics document covering [entries] as weekly
     * recurring events for [weeks] weeks, starting on the Monday of the
     * current week (the calendar week containing today).
     */
    fun export(
        entries: List<ScheduleEntry>,
        calendarName: String = "UniScheduler",
        weeks: Int = DEFAULT_WEEKS
    ): String {
        val now = nowStamp()
        val weekStart = mondayOfThisWeek()

        val sb = StringBuilder()
        sb.crlf("BEGIN:VCALENDAR")
        sb.crlf("VERSION:2.0")
        sb.crlf("PRODID:-//UniScheduler//EN")
        sb.crlf("CALSCALE:GREGORIAN")
        sb.crlf("METHOD:PUBLISH")
        sb.crlf("X-WR-CALNAME:${escape(calendarName)}")

        for (entry in entries) {
            val day = dayIndex(entry.day) ?: continue
            val (sH, sM) = entry.startTime.split(":").let { (it[0].toInt() to it[1].toInt()) }
            val (eH, eM) = entry.endTime.split(":").let { (it[0].toInt() to it[1].toInt()) }

            val occurrence = Calendar.getInstance(TimeZone.getDefault()).apply {
                time = weekStart
                add(Calendar.DAY_OF_MONTH, day)  // 0=Mon … 6=Sun
            }
            val startTime = (occurrence.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, sH); set(Calendar.MINUTE, sM); set(Calendar.SECOND, 0)
            }
            val endTime = (occurrence.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, eH); set(Calendar.MINUTE, eM); set(Calendar.SECOND, 0)
            }

            sb.crlf("BEGIN:VEVENT")
            sb.crlf("UID:uni-scheduler-${entry.id}@$UID_DOMAIN")
            sb.crlf("DTSTAMP:$now")
            sb.crlf("SUMMARY:${escape(buildSummary(entry))}")
            sb.crlf("LOCATION:${escape(entry.classroomCode)}")
            sb.crlf("DESCRIPTION:${escape(buildDescription(entry))}")
            sb.crlf("DTSTART:${floatingStamp(startTime.time)}")
            sb.crlf("DTEND:${floatingStamp(endTime.time)}")
            // RRULE counts the first occurrence, so COUNT=14 = 14 weeks total
            sb.crlf("RRULE:FREQ=WEEKLY;COUNT=$weeks;BYDAY=${rfcDay(day)}")
            sb.crlf("END:VEVENT")
        }

        sb.crlf("END:VCALENDAR")
        return sb.toString()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun buildSummary(entry: ScheduleEntry): String {
        val code = entry.courseCode
        val name = entry.courseName
        return when {
            code.isNotBlank() && name.isNotBlank() -> "$code — $name"
            code.isNotBlank() -> code
            else -> name.ifBlank { "Ders" }
        }
    }

    private fun buildDescription(entry: ScheduleEntry): String = buildList {
        if (entry.lecturerName.isNotBlank()) add("Hoca: ${entry.lecturerName}")
        if (entry.classroomCode.isNotBlank()) add("Derslik: ${entry.classroomCode}")
        val secInfo = entry.offerings?.let {
            val parts = listOfNotNull(
                it.term.takeIf { t -> t.isNotBlank() },
                "Yıl ${it.classYear}".takeIf { _ -> it.classYear > 0 },
                "Şube ${it.section}".takeIf { _ -> it.section.isNotBlank() }
            )
            if (parts.isNotEmpty()) parts.joinToString(" • ") else null
        }
        if (secInfo != null) add(secInfo)
        add("UniScheduler")
    }.joinToString("\\n")

    /** Mon=0 … Fri=4. Returns null for unknown day strings. */
    private fun dayIndex(day: String): Int? = when (day) {
        "Monday" -> 0; "Tuesday" -> 1; "Wednesday" -> 2
        "Thursday" -> 3; "Friday" -> 4; "Saturday" -> 5; "Sunday" -> 6
        else -> null
    }

    /** RFC 5545 BYDAY token: MO/TU/WE/TH/FR/SA/SU. */
    private fun rfcDay(idx: Int): String = arrayOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[idx]

    /** RFC 5545 §3.8.7 DTSTAMP — UTC, fully-qualified. */
    private fun nowStamp(): String =
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    /** Floating local time per §3.3.5 — no TZ suffix. */
    private fun floatingStamp(d: Date): String =
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(d)

    /** Escape per §3.3.11: backslash, comma, semicolon, newline. */
    internal fun escape(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")

    private fun StringBuilder.crlf(line: String) { append(line).append("\r\n") }

    /** Calendar day for the Monday of the calendar week containing today.
     *  Returned at midnight in the device timezone. */
    private fun mondayOfThisWeek(): Date {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
