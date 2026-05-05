// CsvExporter — generates UTF-8 BOM CSV text from domain lists.
// BOM ensures Excel opens with correct encoding for Turkish characters.
package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer

object CsvExporter {

    private const val BOM = "﻿"

    fun exportCourses(courses: List<Course>): String = buildString {
        append(BOM)
        appendLine("code,name,theory_hours,lab_hours,credits,department")
        courses.forEach { c ->
            appendLine("${esc(c.code)},${esc(c.name)},${c.theoryHours},${c.labHours},${c.credits},${esc(c.departmentName)}")
        }
    }

    fun exportLecturers(lecturers: List<Lecturer>): String = buildString {
        append(BOM)
        appendLine("title,first_name,last_name,email,department,username")
        lecturers.forEach { l ->
            appendLine("${esc(l.title)},${esc(l.firstName)},${esc(l.lastName)},${esc(l.email ?: "")},${esc(l.departmentName)},${esc(l.username)}")
        }
    }

    fun exportClassrooms(classrooms: List<Classroom>): String = buildString {
        append(BOM)
        appendLine("room_code,capacity,type,department")
        classrooms.forEach { c ->
            appendLine("${esc(c.roomCode)},${c.capacity},${c.type},${esc(c.departmentName)}")
        }
    }

    /** Escape a CSV field: wrap in quotes if it contains comma/quote/newline. */
    private fun esc(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
