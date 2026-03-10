package com.unischeduler.domain.usecase.import_data

import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Department
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.repository.CourseRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.util.AppLogger
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
    private val scheduleRepository: ScheduleRepository,
    private val excelParser: ExcelParser
) {
    fun parseExcel(bytes: ByteArray): List<ImportRow> {
        AppLogger.i("Import", "Excel parse başlatılıyor (${bytes.size} byte)")
        val rows = excelParser.parse(bytes)
        AppLogger.i("Import", "Excel parse tamamlandı: ${rows.size} satır bulundu")
        return rows
    }

    suspend operator fun invoke(
        rows: List<ImportRow>,
        departmentId: Int,
        fileBytes: ByteArray,
        fileName: String
    ): Result<ImportResult> = runCatching {
        AppLogger.i("Import", "İçe aktarma başlatılıyor: ${rows.size} satır, departmentId=$departmentId")
        val errors = mutableListOf<String>()
        val createdCourses = mutableListOf<Course>()
        val createdLecturers = mutableListOf<Lecturer>()

        // 1. Bölümlerin var olduğundan emin ol
        val actualDeptId = ensureDepartmentExists(rows, departmentId, errors)
        AppLogger.i("Import", "Kullanılacak department_id: $actualDeptId")

        // 2. Hocaları grupla
        val grouped = rows.groupBy { it.lecturerName.trim() }
        AppLogger.i("Import", "${grouped.size} farklı öğretim üyesi tespit edildi")

        val existingLecturersByName = lecturerRepository
            .getLecturersByDepartment(actualDeptId)
            .associateBy { normalizeLecturerName(it.fullName) }
            .toMutableMap()

        for ((lecturerName, lecturerRows) in grouped) {
            if (lecturerName.isBlank()) {
                val msg = "Boş hoca adı bulundu, ${lecturerRows.size} satır atlandı"
                AppLogger.w("Import", msg)
                errors.add(msg)
                continue
            }

            val normalizedLecturerName = normalizeLecturerName(lecturerName)
            AppLogger.d("Import", "Hoca işleniyor: $lecturerName (${lecturerRows.size} ders)")

            // Sadece lecturers tablosuna ekle - auth hesabı AÇMA
            // DB trigger otomatik olarak davet kodu üretecek
            try {
                val lecturer = existingLecturersByName[normalizedLecturerName] ?: lecturerRepository.upsertLecturer(
                    Lecturer(
                        fullName = lecturerName,
                        title = extractTitle(lecturerName),
                        departmentId = actualDeptId,
                        username = "",
                        password = ""
                    )
                ).also {
                    existingLecturersByName[normalizedLecturerName] = it
                }
                createdLecturers.add(lecturer)
                AppLogger.i("Import", "Hoca eklendi: $lecturerName (id=${lecturer.id}, davet kodu=${lecturer.inviteCode})")

                // Dersleri ekle
                for (row in lecturerRows) {
                    try {
                        val course = courseRepository.upsertCourse(
                            Course(
                                code = row.courseCode.trim(),
                                name = row.courseName.trim(),
                                departmentId = actualDeptId
                            )
                        )
                        courseRepository.assignLecturerToCourse(course.id, lecturer.id)
                        createdCourses.add(course)
                        AppLogger.d("Import", "Ders eklendi: ${row.courseCode} -> ${lecturer.fullName}")
                    } catch (e: Exception) {
                        val msg = "Ders eklenemedi (${row.courseCode}): ${sanitizeError(e.message)}"
                        AppLogger.e("Import", msg, e)
                        errors.add(msg)
                    }
                }
            } catch (e: Exception) {
                val msg = "Hoca eklenemedi ($lecturerName): ${sanitizeError(e.message)}"
                AppLogger.e("Import", msg, e)
                errors.add(msg)
            }
        }

        // Orijinal dosyayı Storage'a yükle
        try {
            scheduleRepository.uploadExcelFile(fileName, fileBytes)
            AppLogger.i("Import", "Excel dosyası Storage'a yüklendi: $fileName")
        } catch (e: Exception) {
            AppLogger.w("Import", "Dosya Storage'a yüklenemedi: ${e.message}")
        }

        AppLogger.i("Import", "İçe aktarma tamamlandı: ${createdCourses.size} ders, ${createdLecturers.size} hoca, ${errors.size} hata")
        ImportResult(createdCourses, createdLecturers, errors)
    }

    private suspend fun ensureDepartmentExists(
        rows: List<ImportRow>,
        fallbackDeptId: Int,
        errors: MutableList<String>
    ): Int {
        if (fallbackDeptId > 0) {
            // Admin'in aktif bölüm bağlamını koru; import sonrası veri aynı bölümde görünür kalır.
            return fallbackDeptId
        }

        val deptCodes = rows.mapNotNull { row ->
            val code = row.courseCode.trim()
            val parts = code.split(Regex("\\s+"))
            if (parts.isNotEmpty() && parts[0].all { it.isLetter() }) parts[0].uppercase() else null
        }.distinct()

        AppLogger.d("Import", "Excel'den çıkarılan bölüm kodları: $deptCodes")

        val existingDepts = lecturerRepository.getDepartments()
        val existingCodes = existingDepts.map { it.code.uppercase() }.toSet()

        var firstDeptId = fallbackDeptId
        for (code in deptCodes) {
            if (code.uppercase() !in existingCodes) {
                try {
                    val dept = lecturerRepository.upsertDepartment(
                        Department(
                            id = 0,
                            name = getDepartmentName(code),
                            code = code
                        )
                    )
                    AppLogger.i("Import", "Bölüm oluşturuldu: ${dept.code} - ${dept.name} (id=${dept.id})")
                    if (firstDeptId == fallbackDeptId || firstDeptId == 0) {
                        firstDeptId = dept.id
                    }
                } catch (e: Exception) {
                    val msg = "Bölüm oluşturulamadı ($code): ${sanitizeError(e.message)}"
                    AppLogger.e("Import", msg, e)
                    errors.add(msg)
                }
            } else {
                val existing = existingDepts.find { it.code.equals(code, ignoreCase = true) }
                if (existing != null && (firstDeptId == fallbackDeptId || firstDeptId == 0)) {
                    firstDeptId = existing.id
                }
            }
        }

        if (firstDeptId == fallbackDeptId) {
            val refreshed = lecturerRepository.getDepartments()
            if (refreshed.isNotEmpty()) {
                firstDeptId = refreshed.first().id
            }
        }

        return firstDeptId
    }

    private fun getDepartmentName(code: String): String {
        return when (code.uppercase()) {
            "CNG", "CENG", "CSE", "CS" -> "Computer Engineering"
            "EEE", "EE", "ECE" -> "Electrical & Electronics Engineering"
            "ME", "MAK" -> "Mechanical Engineering"
            "CE", "INS" -> "Civil Engineering"
            "IE", "END" -> "Industrial Engineering"
            "MATH", "MAT" -> "Mathematics"
            "PHYS", "FIZ" -> "Physics"
            "CHEM", "KIM" -> "Chemistry"
            "BIO", "BIY" -> "Biology"
            else -> "$code Department"
        }
    }

    private fun extractTitle(fullName: String): String {
        val titlePrefixes = listOf(
            "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi", "Arş. Gör. Dr.",
            "Arş. Gör.", "Öğr. Gör. Dr.", "Öğr. Gör.",
            "Assoc. Prof.", "Assist. Prof.", "Prof."
        )
        return titlePrefixes.find { fullName.startsWith(it, ignoreCase = true) } ?: ""
    }

    private fun normalizeLecturerName(fullName: String): String {
        return fullName.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /** Hata mesajlarından hassas bilgileri (JWT token, URL) temizle */
    private fun sanitizeError(message: String?): String {
        if (message == null) return "Bilinmeyen hata"
        return message
            .replace(Regex("Bearer [A-Za-z0-9._-]+"), "Bearer ***")
            .replace(Regex("https?://[^\\s]+"), "[URL gizlendi]")
            .replace(Regex("Headers: \\[.*?]"), "")
            .trim()
    }
}
