package com.unischeduler.domain.usecase.import_data

import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.CourseRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.util.CredentialGenerator
import com.unischeduler.util.ExcelParser
import javax.inject.Inject

data class ImportRow(
    val courseCode: String,
    val courseName: String,
    val lecturerName: String
)

data class ImportResult(
    val courses: List<Course>,
    val lecturers: List<Lecturer>,
    val errors: List<String>
)

class ImportExcelUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val lecturerRepository: LecturerRepository,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val excelParser: ExcelParser,
    private val credentialGenerator: CredentialGenerator
) {
    fun parseExcel(bytes: ByteArray): List<ImportRow> {
        return excelParser.parse(bytes)
    }

    suspend operator fun invoke(
        rows: List<ImportRow>,
        departmentId: Int,
        fileBytes: ByteArray,
        fileName: String
    ): Result<ImportResult> = runCatching {
        val errors = mutableListOf<String>()
        val createdCourses = mutableListOf<Course>()
        val createdLecturers = mutableListOf<Lecturer>()
        val lecturerMap = mutableMapOf<String, Lecturer>()

        // Group rows by lecturer
        val grouped = rows.groupBy { it.lecturerName.trim() }

        for ((lecturerName, lecturerRows) in grouped) {
            if (lecturerName.isBlank()) {
                errors.add("Boş hoca adı bulundu, satırlar atlandı")
                continue
            }

            val credentials = credentialGenerator.generate(lecturerName)

            // Create auth account
            val profileId = try {
                authRepository.createLecturerAccount(
                    email = "${credentials.username}@unischeduler.local",
                    password = credentials.password
                )
            } catch (e: Exception) {
                // Account may already exist
                errors.add("${lecturerName}: Hesap zaten mevcut olabilir")
                null
            }

            val lecturer = lecturerRepository.upsertLecturer(
                Lecturer(
                    fullName = lecturerName,
                    title = extractTitle(lecturerName),
                    departmentId = departmentId,
                    profileId = profileId,
                    username = credentials.username,
                    password = credentials.password
                )
            )
            lecturerMap[lecturerName] = lecturer
            createdLecturers.add(lecturer)

            // Create courses for this lecturer
            for (row in lecturerRows) {
                val course = courseRepository.upsertCourse(
                    Course(
                        code = row.courseCode.trim(),
                        name = row.courseName.trim(),
                        departmentId = departmentId
                    )
                )
                courseRepository.assignLecturerToCourse(course.id, lecturer.id)
                createdCourses.add(course)
            }
        }

        // Upload original file to Storage
        try {
            scheduleRepository.uploadExcelFile(fileName, fileBytes)
        } catch (_: Exception) {
            errors.add("Dosya Storage'a yüklenemedi")
        }

        ImportResult(createdCourses, createdLecturers, errors)
    }

    private fun extractTitle(fullName: String): String {
        val titlePrefixes = listOf(
            "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi", "Arş. Gör. Dr.",
            "Arş. Gör.", "Öğr. Gör. Dr.", "Öğr. Gör.",
            "Assoc. Prof.", "Assist. Prof.", "Prof."
        )
        return titlePrefixes.find { fullName.startsWith(it, ignoreCase = true) } ?: ""
    }
}
