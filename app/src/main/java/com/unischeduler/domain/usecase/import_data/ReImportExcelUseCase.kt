package com.unischeduler.domain.usecase.import_data

import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.util.ExcelParser
import javax.inject.Inject

data class DiffResult(
    val newCourses: List<ImportRow>,
    val modifiedCourses: List<ImportRow>,
    val removedCourses: List<Course>,
    val newLecturers: List<String>,
    val unchanged: Int
)

class ReImportExcelUseCase @Inject constructor(
    private val excelParser: ExcelParser
) {
    fun computeDiff(
        newRows: List<ImportRow>,
        existingCourses: List<Course>,
        existingLecturers: List<Lecturer>
    ): DiffResult {
        val existingCodes = existingCourses.map { it.code }.toSet()
        val newCodes = newRows.map { it.courseCode.trim() }.toSet()
        val existingLecturerNames = existingLecturers.map { it.fullName }.toSet()

        val newItems = newRows.filter { it.courseCode.trim() !in existingCodes }
        val modified = newRows.filter { row ->
            val existing = existingCourses.find { it.code == row.courseCode.trim() }
            existing != null && existing.name != row.courseName.trim()
        }
        val removed = existingCourses.filter { it.code !in newCodes }
        val newLecturerNames = newRows.map { it.lecturerName.trim() }
            .distinct()
            .filter { it !in existingLecturerNames }

        val unchanged = newRows.size - newItems.size - modified.size

        return DiffResult(
            newCourses = newItems,
            modifiedCourses = modified,
            removedCourses = removed,
            newLecturers = newLecturerNames,
            unchanged = unchanged
        )
    }
}
