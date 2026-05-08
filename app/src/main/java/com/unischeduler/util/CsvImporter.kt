// CsvImporter — parses CSV text into typed row objects.
// Accepts both comma and semicolon separators (common in Turkish Excel exports).
// Header row detection: if first cell of row 1 is non-numeric, it's treated as a header.
// All fields are trimmed; optional numeric fields default to 0.
package com.unischeduler.util

object CsvImporter {

    // ── Result container ──────────────────────────────────────────────────────

    data class ParseResult<T>(
        val valid: List<T>,
        val errors: List<String>
    ) {
        val isEmpty: Boolean get() = valid.isEmpty()
    }

    // ── Row types ─────────────────────────────────────────────────────────────

    data class CourseRow(
        val code: String,
        val name: String,
        val theoryHours: Int,   // default 0
        val labHours: Int,      // default 0
        val credits: Int,       // default 0
        val departmentName: String? = null  // optional — lookup against existing depts
    )

    data class LecturerRow(
        val title: String,      // default "Lect."
        val firstName: String,
        val lastName: String,
        val email: String?,     // optional
        val departmentName: String? = null,  // optional
        val username: String? = null         // optional — if present, use as-is
    )

    data class ClassroomRow(
        val roomCode: String,
        val capacity: Int,
        val type: String,       // "theory" | "lab", default "theory"
        val departmentName: String? = null   // optional
    )

    // ── Parsers ───────────────────────────────────────────────────────────────

    /**
     * Expected columns (order matters, all optional after name):
     *   code, name, theory_hours, lab_hours, credits
     */
    fun parseCourses(csv: String): ParseResult<CourseRow> {
        val lines = sanitize(csv)
        if (lines.isEmpty()) return ParseResult(emptyList(), listOf("File is empty."))

        val dataLines = skipHeader(lines, "code")
        val valid  = mutableListOf<CourseRow>()
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val cols = split(line)
            val code = cols.getOrNull(0)?.trim()?.uppercase()
            val name = cols.getOrNull(1)?.trim()
            if (code.isNullOrBlank() || name.isNullOrBlank()) {
                errors.add("Row ${idx + 2}: 'code' and 'name' are required.")
                return@forEachIndexed
            }
            valid.add(CourseRow(
                code        = code,
                name        = name,
                theoryHours = cols.getOrNull(2)?.trim()?.toIntOrNull() ?: 0,
                labHours    = cols.getOrNull(3)?.trim()?.toIntOrNull() ?: 0,
                credits     = cols.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
            ))
        }
        return ParseResult(valid, errors)
    }

    /**
     * Expected columns:
     *   title, first_name, last_name, email
     * title and email are optional.
     */
    fun parseLecturers(csv: String): ParseResult<LecturerRow> {
        val lines = sanitize(csv)
        if (lines.isEmpty()) return ParseResult(emptyList(), listOf("File is empty."))

        val dataLines = skipHeader(lines, "first", "title", "ad")
        val valid  = mutableListOf<LecturerRow>()
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val cols      = split(line)
            val title     = cols.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: "Lect."
            val firstName = cols.getOrNull(1)?.trim()
            val lastName  = cols.getOrNull(2)?.trim()
            if (firstName.isNullOrBlank() || lastName.isNullOrBlank()) {
                errors.add("Row ${idx + 2}: 'first_name' and 'last_name' are required.")
                return@forEachIndexed
            }
            valid.add(LecturerRow(
                title     = title,
                firstName = firstName,
                lastName  = lastName,
                email     = cols.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
            ))
        }
        return ParseResult(valid, errors)
    }

    /**
     * Expected columns:
     *   room_code, capacity, type
     * type is optional (defaults to "theory").
     */
    fun parseClassrooms(csv: String): ParseResult<ClassroomRow> {
        val lines = sanitize(csv)
        if (lines.isEmpty()) return ParseResult(emptyList(), listOf("File is empty."))

        val dataLines = skipHeader(lines, "room", "code", "oda")
        val valid  = mutableListOf<ClassroomRow>()
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { idx, line ->
            val cols     = split(line)
            val roomCode = cols.getOrNull(0)?.trim()
            val capacity = cols.getOrNull(1)?.trim()?.toIntOrNull()
            if (roomCode.isNullOrBlank() || capacity == null || capacity <= 0) {
                errors.add("Row ${idx + 2}: 'room_code' and positive 'capacity' are required.")
                return@forEachIndexed
            }
            val rawType = cols.getOrNull(2)?.trim()?.lowercase() ?: ""
            val type    = if (rawType == "lab") "lab" else "theory"
            valid.add(ClassroomRow(roomCode, capacity, type))
        }
        return ParseResult(valid, errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sanitize(csv: String): List<String> =
        csv.lines().map { it.trim() }.filter { it.isNotBlank() }

    /** If the first line matches any keyword, treat it as a header and skip it. */
    private fun skipHeader(lines: List<String>, vararg keywords: String): List<String> {
        val first = lines.firstOrNull()?.lowercase() ?: return lines
        return if (keywords.any { first.contains(it) }) lines.drop(1) else lines
    }

    /** Splits a CSV line respecting quoted fields; auto-detects comma vs semicolon. */
    private fun split(line: String): List<String> {
        val sep = if (line.contains(';') && !line.contains(',')) ';' else ','
        val result  = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"'             -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> { result.add(current.toString()); current.clear() }
                else                  -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
