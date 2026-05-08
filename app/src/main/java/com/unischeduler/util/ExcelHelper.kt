package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * ExcelHelper — .xlsx import/export (Apache POI)
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
            org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val headerRow = sheet.getRow(0)
                    ?: return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))

                val headers = readRowAsStrings(headerRow).map { it.rootLower() }

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
                                "Bulunan sütunlar: ${headers.filter { it.isNotBlank() }.joinToString()}"
                        )
                    )
                }

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val cells = readRowAsStrings(row)
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
            }
        } catch (e: Exception) {
            errors.add("Dosya okuma hatası: ${e.message}")
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
            org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val headerRow = sheet.getRow(0)
                    ?: return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))

                val headers = readRowAsStrings(headerRow).map { it.rootLower() }

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
                                "Bulunan sütunlar: ${headers.filter { it.isNotBlank() }.joinToString()}"
                        )
                    )
                }

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val cells = readRowAsStrings(row)
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
            }
        } catch (e: Exception) {
            errors.add("Dosya okuma hatası: ${e.message}")
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
            org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val headerRow = sheet.getRow(0)
                    ?: return ImportResult(emptyList(), listOf("Dosya boş veya ilk satır okunamıyor."))

                val headers = readRowAsStrings(headerRow).map { it.rootLower() }

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
                                "Bulunan sütunlar: ${headers.filter { it.isNotBlank() }.joinToString()}"
                        )
                    )
                }

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val cells = readRowAsStrings(row)
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
            }
        } catch (e: Exception) {
            errors.add("Dosya okuma hatası: ${e.message}")
        }

        return ImportResult(rows, errors)
    }

    // ══════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════

    fun exportCourses(courses: List<Course>, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Courses")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
        val header = sheet.createRow(0)
        val headers = listOf("Code", "Name", "Theory Hours", "Lab Hours", "Credits", "Department")
        headers.forEachIndexed { idx, h ->
            header.createCell(idx).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        courses.forEachIndexed { i, c ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(c.code)
            row.createCell(1).setCellValue(c.name)
            row.createCell(2).setCellValue(c.theoryHours.toDouble())
            row.createCell(3).setCellValue(c.labHours.toDouble())
            row.createCell(4).setCellValue(c.credits.toDouble())
            row.createCell(5).setCellValue(c.departmentName)
        }

        headers.indices.forEach { sheet.setColumnWidth(it, 5000) }
        workbook.write(outputStream)
        workbook.close()
    }

    fun exportLecturers(lecturers: List<Lecturer>, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Lecturers")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
        val header = sheet.createRow(0)
        val headers = listOf("Title", "First Name", "Last Name", "Email", "Department", "Username")
        headers.forEachIndexed { idx, h ->
            header.createCell(idx).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        lecturers.forEachIndexed { i, l ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(l.title)
            row.createCell(1).setCellValue(l.firstName)
            row.createCell(2).setCellValue(l.lastName)
            row.createCell(3).setCellValue(l.email ?: "")
            row.createCell(4).setCellValue(l.departmentName)
            row.createCell(5).setCellValue(l.username)
        }

        headers.indices.forEach { sheet.setColumnWidth(it, 5000) }
        workbook.write(outputStream)
        workbook.close()
    }

    fun exportClassrooms(classrooms: List<Classroom>, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Classrooms")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
        val header = sheet.createRow(0)
        val headers = listOf("Room Code", "Capacity", "Type", "Department")
        headers.forEachIndexed { idx, h ->
            header.createCell(idx).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        classrooms.forEachIndexed { i, c ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(c.roomCode)
            row.createCell(1).setCellValue(c.capacity.toDouble())
            row.createCell(2).setCellValue(c.type)
            row.createCell(3).setCellValue(c.departmentName)
        }

        headers.indices.forEach { sheet.setColumnWidth(it, 5000) }
        workbook.write(outputStream)
        workbook.close()
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════

    private fun readRowAsStrings(row: Row): List<String> {
        val cells = mutableListOf<String>()
        for (i in 0 until row.lastCellNum.coerceAtLeast(0)) {
            val cell = row.getCell(i)
            val value = when {
                cell == null -> ""
                cell.cellType == CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue.toString()
                    } else {
                        val num = cell.numericCellValue
                        if (num == num.toLong().toDouble()) num.toLong().toString()
                        else num.toString()
                    }
                }
                cell.cellType == CellType.STRING -> cell.stringCellValue
                cell.cellType == CellType.BOOLEAN -> cell.booleanCellValue.toString()
                cell.cellType == CellType.FORMULA -> {
                    try { cell.stringCellValue } catch (_: Exception) {
                        try { cell.numericCellValue.toString() } catch (_: Exception) { "" }
                    }
                }
                else -> ""
            }
            cells.add(value)
        }
        return cells
    }

    private fun String.rootLower() = this.lowercase(Locale.ROOT).trim()

    private fun findExact(headers: List<String>, vararg candidates: String): Int? {
        for (candidate in candidates) {
            val idx = headers.indexOfFirst { it.isNotBlank() && it == candidate.rootLower() }
            if (idx != -1) return idx
        }
        return null
    }

    private fun findContains(headers: List<String>, vararg keywords: String): Int? {
        for (keyword in keywords) {
            val idx = headers.indexOfFirst { it.isNotBlank() && it.contains(keyword.rootLower()) }
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
}
