package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * ExcelHelper — .xlsx import/export (zero external Excel deps)
 *
 * Both directions go through our own MiniXlsxReader / MiniXlsxWriter
 * (raw ZIP + XML pull parser). Apache POI was removed in Nov 2026
 * because its Android compatibility was unworkable (IOUtils overrides,
 * R8 ServiceLoader churn, 100 MB allocation requests for 5 KB files).
 *
 * Kabul edilen header isimleri:
 *
 * COURSES (zorunlu: code/kod, name/ad/ders):
 *   Kod  : "code", "kod"
 *   Ad   : "name", "course name", "ders adı", "ders", "ad"
 *   Teori: "theory hours", "theory", "teori"
 *   Lab  : "lab hours", "lab"
 *   Kredi: "credits", "credit", "kredi", "akts"
 *
 * LECTURERS (zorunlu: first name / ad, last name / soyad):
 *   Unvan  : "title", "unvan"
 *   Ad     : "first name", "firstname", "first_name", "ad", "adı"
 *   Soyad  : "last name", "lastname", "last_name", "soyad", "soyadı"
 *   E-posta: "email", "e-mail", "e-posta"
 *
 * CLASSROOMS (zorunlu: room code / derslik kodu):
 *   Kod    : "room code", "room_code", "roomcode", "oda", "derslik", "sınıf", "code", "kod"
 *   Kapasite: "capacity", "kapasite", "kontenjan"
 *   Tür    : "type", "tür", "tip"
 */
object ExcelHelper {

    data class ImportResult<T>(
        val valid: List<T>,
        val errors: List<String>
    )

    // ══════════════════════════════════════════════════════════════════
    //  IMPORT — COURSES
    // ══════════════════════════════════════════════════════════════════

    fun importCourses(inputStream: InputStream): ImportResult<CsvImporter.CourseRow> {
        val rows = mutableListOf<CsvImporter.CourseRow>()
        val errors = mutableListOf<String>()

        try {
            // Switched away from Apache POI for import in Nov 2026 —
            // POI's IOUtils kept demanding 100 MB array allocations for
            // 5 KB sample files even with byteArrayMaxOverride bumped.
            // MiniXlsxReader is a self-contained ZIP+XmlPullParser parser
            // that delivers identical row data with zero failure modes.
            val sheet = MiniXlsxReader.read(inputStream)
            if (sheet.isEmpty()) {
                return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))
            }
            val headers = sheet[0].map { it.rootLower() }

            val codeIdx = findExact(headers, "code", "kod")
                ?: findContains(headers, "code", "kod")
            val nameIdx = findExact(headers, "name", "course name", "ders adı", "ders", "ad")
                ?: findContains(headers, "name", "ders", "ad")
            val theoryIdx = findExact(headers, "theory hours", "theory", "teori")
                ?: findContains(headers, "theory", "teori")
            val labIdx = findExact(headers, "lab hours", "lab")
                ?: findContains(headers, "lab")
            val creditsIdx = findExact(headers, "credits", "credit", "kredi", "akts")
                ?: findContains(headers, "credit", "kredi", "akts")
            val deptIdx = findExact(headers, "department", "bölüm", "bolum")
                ?: findContains(headers, "department", "bölüm", "bolum")

            if (codeIdx == null || nameIdx == null) {
                return ImportResult(
                    emptyList(),
                    listOf(
                        "Zorunlu sütunlar eksik. 'Code' veya 'Kod' ve 'Name' veya 'Ad'/'Ders' sütunları bulunmalı. " +
                            "Bulunan sütunlar: ${sheet[0].filter { it.isNotBlank() }.joinToString()}"
                    )
                )
            }

            for (i in 1 until sheet.size) {
                val cells = sheet[i]
                val code = cells.getOrNull(codeIdx)?.trim().orEmpty()
                val name = cells.getOrNull(nameIdx)?.trim().orEmpty()
                if (code.isBlank() || name.isBlank()) {
                    if (code.isNotBlank() || name.isNotBlank())
                        errors.add("Satır ${i + 1}: kod veya ad eksik")
                    continue
                }
                rows.add(
                    CsvImporter.CourseRow(
                        code = code,
                        name = name,
                        theoryHours = cells.getOrNull(theoryIdx ?: -1)?.toIntOrNull() ?: 0,
                        labHours = cells.getOrNull(labIdx ?: -1)?.toIntOrNull() ?: 0,
                        credits = cells.getOrNull(creditsIdx ?: -1)?.toIntOrNull() ?: 0,
                        departmentName = cells.getOrNull(deptIdx ?: -1)?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("ExcelHelper", "Excel course parse failed", e)
            errors.add(formatExcelError(e))
        }

        return ImportResult(rows, errors)
    }

    // ══════════════════════════════════════════════════════════════════
    //  IMPORT — LECTURERS
    // ══════════════════════════════════════════════════════════════════

    fun importLecturers(inputStream: InputStream): ImportResult<CsvImporter.LecturerRow> {
        val rows = mutableListOf<CsvImporter.LecturerRow>()
        val errors = mutableListOf<String>()

        try {
            val sheet = MiniXlsxReader.read(inputStream)
            if (sheet.isEmpty()) {
                return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))
            }
            val headers = sheet[0].map { it.rootLower() }

            val titleIdx = findExact(headers, "title", "unvan")
                ?: findContains(headers, "title", "unvan")
            val firstIdx = findExact(headers, "first name", "firstname", "first_name", "ad", "adı")
                ?: findContainsExcluding(headers, listOf("first", "ad"), excludes = listOf("soyad", "last"))
            val lastIdx = findExact(headers, "last name", "lastname", "last_name", "soyad", "soyadı")
                ?: findContains(headers, "last", "soyad")
            val emailIdx = findExact(headers, "email", "e-mail", "e-posta")
                ?: findContains(headers, "email", "e-posta")
            val deptIdx = findExact(headers, "department", "bölüm", "bolum")
                ?: findContains(headers, "department", "bölüm", "bolum")
            val usernameIdx = findExact(headers, "username", "kullanıcı adı", "kullanici_adi", "kullanici adi")
                ?: findContains(headers, "username", "kullanıcı", "kullanici")

            if (firstIdx == null || lastIdx == null) {
                return ImportResult(
                    emptyList(),
                    listOf(
                        "Zorunlu sütunlar eksik. 'First Name'/'Ad' ve 'Last Name'/'Soyad' sütunları bulunmalı. " +
                            "Bulunan sütunlar: ${sheet[0].filter { it.isNotBlank() }.joinToString()}"
                    )
                )
            }

            for (i in 1 until sheet.size) {
                val cells = sheet[i]
                val firstName = cells.getOrNull(firstIdx)?.trim().orEmpty()
                val lastName = cells.getOrNull(lastIdx)?.trim().orEmpty()
                if (firstName.isBlank() || lastName.isBlank()) {
                    if (firstName.isNotBlank() || lastName.isNotBlank())
                        errors.add("Satır ${i + 1}: ad veya soyad eksik")
                    continue
                }
                rows.add(
                    CsvImporter.LecturerRow(
                        title = cells.getOrNull(titleIdx ?: -1)?.trim().orEmpty(),
                        firstName = firstName,
                        lastName = lastName,
                        email = cells.getOrNull(emailIdx ?: -1)?.trim()?.takeIf { it.isNotBlank() },
                        departmentName = cells.getOrNull(deptIdx ?: -1)?.trim()?.takeIf { it.isNotBlank() },
                        username = cells.getOrNull(usernameIdx ?: -1)?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("ExcelHelper", "Excel lecturer parse failed", e)
            errors.add(formatExcelError(e))
        }

        return ImportResult(rows, errors)
    }

    // ══════════════════════════════════════════════════════════════════
    //  IMPORT — CLASSROOMS
    // ══════════════════════════════════════════════════════════════════

    fun importClassrooms(inputStream: InputStream): ImportResult<CsvImporter.ClassroomRow> {
        val rows = mutableListOf<CsvImporter.ClassroomRow>()
        val errors = mutableListOf<String>()

        try {
            val sheet = MiniXlsxReader.read(inputStream)
            if (sheet.isEmpty()) {
                return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))
            }
            val headers = sheet[0].map { it.rootLower() }

            val roomIdx = findExact(headers, "room code", "room_code", "roomcode", "oda", "derslik", "sınıf", "code", "kod")
                ?: findContains(headers, "room", "oda", "derslik", "sınıf", "kod")
            val capIdx = findExact(headers, "capacity", "kapasite", "kontenjan")
                ?: findContains(headers, "capacity", "kapasite", "kontenjan")
            val typeIdx = findExact(headers, "type", "tür", "tip")
                ?: findContains(headers, "type", "tür", "tip")
            val deptIdx = findExact(headers, "department", "bölüm", "bolum")
                ?: findContains(headers, "department", "bölüm", "bolum")

            if (roomIdx == null) {
                return ImportResult(
                    emptyList(),
                    listOf(
                        "Zorunlu sütun eksik. 'Room Code'/'Derslik'/'Kod' sütunu bulunmalı. " +
                            "Bulunan sütunlar: ${sheet[0].filter { it.isNotBlank() }.joinToString()}"
                    )
                )
            }

            for (i in 1 until sheet.size) {
                val cells = sheet[i]
                val roomCode = cells.getOrNull(roomIdx)?.trim().orEmpty()
                if (roomCode.isBlank()) continue
                rows.add(
                    CsvImporter.ClassroomRow(
                        roomCode = roomCode,
                        capacity = cells.getOrNull(capIdx ?: -1)?.toIntOrNull() ?: 30,
                        type = cells.getOrNull(typeIdx ?: -1)?.trim()?.lowercase(Locale.ROOT) ?: "theory",
                        departmentName = cells.getOrNull(deptIdx ?: -1)?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Throwable) {
            // Throwable so we also catch NoClassDefFoundError /
            // ExceptionInInitializerError — POI on Android sometimes
            // fails to load IOUtils' static initializer and surfaces
            // those errors instead of plain IOException.
            android.util.Log.e("ExcelHelper", "Excel parse failed", e)
            errors.add(formatExcelError(e))
        }

        return ImportResult(rows, errors)
    }

    // ══════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════

    fun exportCourses(courses: List<Course>, outputStream: OutputStream) {
        val headers = listOf("Code", "Name", "Theory Hours", "Lab Hours", "Credits", "Department")
        val rows = courses.map { c ->
            listOf<MiniXlsxWriter.Cell>(
                MiniXlsxWriter.Cell.Str(c.code),
                MiniXlsxWriter.Cell.Str(c.name),
                MiniXlsxWriter.Cell.Num(c.theoryHours.toDouble()),
                MiniXlsxWriter.Cell.Num(c.labHours.toDouble()),
                MiniXlsxWriter.Cell.Num(c.credits.toDouble()),
                MiniXlsxWriter.Cell.Str(c.departmentName)
            )
        }
        MiniXlsxWriter.write(outputStream, "Courses", headers, rows)
    }

    fun exportLecturers(lecturers: List<Lecturer>, outputStream: OutputStream) {
        val headers = listOf("Title", "First Name", "Last Name", "Email", "Department", "Username")
        val rows = lecturers.map { l ->
            listOf<MiniXlsxWriter.Cell>(
                MiniXlsxWriter.Cell.Str(l.title),
                MiniXlsxWriter.Cell.Str(l.firstName),
                MiniXlsxWriter.Cell.Str(l.lastName),
                MiniXlsxWriter.Cell.Str(l.email ?: ""),
                MiniXlsxWriter.Cell.Str(l.departmentName),
                MiniXlsxWriter.Cell.Str(l.username)
            )
        }
        MiniXlsxWriter.write(outputStream, "Lecturers", headers, rows)
    }

    fun exportClassrooms(classrooms: List<Classroom>, outputStream: OutputStream) {
        val headers = listOf("Room Code", "Capacity", "Type", "Department")
        val rows = classrooms.map { c ->
            listOf<MiniXlsxWriter.Cell>(
                MiniXlsxWriter.Cell.Str(c.roomCode),
                MiniXlsxWriter.Cell.Num(c.capacity.toDouble()),
                MiniXlsxWriter.Cell.Str(c.type),
                MiniXlsxWriter.Cell.Str(c.departmentName)
            )
        }
        MiniXlsxWriter.write(outputStream, "Classrooms", headers, rows)
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Aggressive normalisation so header matching tolerates real-world
     * input. "First Name", "first_name", "FIRST-NAME", "  First.Name  ",
     * "Ad ı" all collapse to the same canonical form before comparison.
     *
     * Steps:
     *   1. Turkish-fold (ş→s, ç→c, ğ→g, ü→u, ö→o, ı→i, İ→i)
     *   2. lowercase (en_US locale to avoid Turkish "i" oddities)
     *   3. strip everything that isn't a letter or digit (spaces,
     *      punctuation, separators all gone)
     */
    private fun String.rootLower(): String {
        if (isEmpty()) return ""
        val sb = StringBuilder(length)
        for (raw in this) {
            val c = TURKISH_HEADER_FOLD[raw] ?: raw
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    private val TURKISH_HEADER_FOLD = mapOf(
        'ş' to 's', 'Ş' to 's',
        'ç' to 'c', 'Ç' to 'c',
        'ğ' to 'g', 'Ğ' to 'g',
        'ü' to 'u', 'Ü' to 'u',
        'ö' to 'o', 'Ö' to 'o',
        'ı' to 'i', 'İ' to 'i'
    )

    private fun findExact(headers: List<String>, vararg candidates: String): Int? {
        for (candidate in candidates) {
            val target = candidate.rootLower()
            val idx = headers.indexOfFirst { it.isNotBlank() && it.rootLower() == target }
            if (idx != -1) return idx
        }
        return null
    }

    private fun findContains(headers: List<String>, vararg keywords: String): Int? {
        for (keyword in keywords) {
            val target = keyword.rootLower()
            val idx = headers.indexOfFirst { it.isNotBlank() && it.rootLower().contains(target) }
            if (idx != -1) return idx
        }
        return null
    }

    private fun findContainsExcluding(
        headers: List<String>,
        keywords: List<String>,
        excludes: List<String>
    ): Int? {
        for (keyword in keywords) {
            val idx = headers.indexOfFirst { h ->
                h.isNotBlank() &&
                    h.contains(keyword.rootLower()) &&
                    excludes.none { h.contains(it.rootLower()) }
            }
            if (idx != -1) return idx
        }
        return null
    }

    private fun String.toIntOrNull(): Int? = this.trim().toDoubleOrNull()?.toInt()

    // ──────────────────────────────────────────────────────────────────
    //  POI initialization & error formatting
    // ──────────────────────────────────────────────────────────────────
    //
    // formatExcelError() produces a human-friendly Turkish message
    // *and* keeps the raw class+message in Logcat for forensic use.

    private fun formatExcelError(t: Throwable): String {
        val rootMessage = generateSequence<Throwable>(t) { it.cause }
            .lastOrNull { !it.message.isNullOrBlank() }
            ?.message
            ?.take(200)
            ?: t.message
            ?: t::class.java.simpleName

        // Senior pratiği: never tell the user "your tool is broken, use
        // a worse one instead". The fix is on our side — surface the
        // real cause clearly so the user (or support) knows what to
        // attach when reporting, but don't push them off the path they
        // wanted to take. CSV remains an option in the file picker.
        return when (t) {
            is NoClassDefFoundError, is ExceptionInInitializerError ->
                "Excel okuma motoru başlatılamadı. " +
                "Uygulamayı kapatıp tekrar açmayı deneyin; sorun sürerse " +
                "yöneticinize bu bilgiyi iletin → $rootMessage"
            is java.io.IOException ->
                "Dosya bozuk ya da desteklenmeyen biçimde: $rootMessage"
            else ->
                "Excel dosyası okunamadı: $rootMessage"
        }
    }
}
