// ExcelHelper — .xlsx import/export using Apache POI
// Handles Turkish characters natively (xlsx is UTF-8 by default)
package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Lecturer
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream

object ExcelHelper {

    // ══════════════════════════════════════════════════════════════════
    //  IMPORT
    // ══════════════════════════════════════════════════════════════════

    data class ImportResult<T>(
        val valid: List<T>,
        val errors: List<String>
    )

    fun importCourses(inputStream: InputStream): ImportResult<CsvImporter.CourseRow> {
        val rows = mutableListOf<CsvImporter.CourseRow>()
        val errors = mutableListOf<String>()

        try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(0) ?: return ImportResult(emptyList(), listOf("Empty file"))

            // Find column indices from header
            val headers = readRowAsStrings(headerRow).map { it.lowercase().trim() }
            val codeIdx = headers.indexOfFirst { it.contains("code") || it.contains("kod") }
            val nameIdx = headers.indexOfFirst { it.contains("name") || it.contains("ad") || it.contains("ders") }
            val theoryIdx = headers.indexOfFirst { it.contains("theory") || it.contains("teori") }
            val labIdx = headers.indexOfFirst { it.contains("lab") }
            val creditsIdx = headers.indexOfFirst { it.contains("credit") || it.contains("kredi") || it.contains("akts") }

            if (codeIdx == -1 || nameIdx == -1) {
                return ImportResult(emptyList(), listOf("Missing required columns: code, name"))
            }

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val cells = readRowAsStrings(row)
                val code = cells.getOrNull(codeIdx)?.trim().orEmpty()
                val name = cells.getOrNull(nameIdx)?.trim().orEmpty()
                if (code.isBlank() || name.isBlank()) {
                    if (code.isNotBlank() || name.isNotBlank()) errors.add("Row ${i + 1}: missing code or name")
                    continue
                }
                rows.add(
                    CsvImporter.CourseRow(
                        code = code,
                        name = name,
                        theoryHours = cells.getOrNull(theoryIdx)?.toIntOrNull() ?: 0,
                        labHours = cells.getOrNull(labIdx)?.toIntOrNull() ?: 0,
                        credits = cells.getOrNull(creditsIdx)?.toIntOrNull() ?: 0
                    )
                )
            }
            workbook.close()
        } catch (e: Exception) {
            errors.add("Failed to read file: ${e.message}")
        }

        return ImportResult(rows, errors)
    }

    fun importLecturers(inputStream: InputStream): ImportResult<CsvImporter.LecturerRow> {
        val rows = mutableListOf<CsvImporter.LecturerRow>()
        val errors = mutableListOf<String>()

        try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(0) ?: return ImportResult(emptyList(), listOf("Empty file"))

            val headers = readRowAsStrings(headerRow).map { it.lowercase().trim() }
            val titleIdx = headers.indexOfFirst { it.contains("title") || it.contains("unvan") }
            val firstIdx = headers.indexOfFirst { it.contains("first") || it.contains("ad") && !it.contains("soyad") }
            val lastIdx = headers.indexOfFirst { it.contains("last") || it.contains("soyad") }
            val emailIdx = headers.indexOfFirst { it.contains("email") || it.contains("e-posta") }

            if (firstIdx == -1 || lastIdx == -1) {
                return ImportResult(emptyList(), listOf("Missing required columns: first_name, last_name"))
            }

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val cells = readRowAsStrings(row)
                val firstName = cells.getOrNull(firstIdx)?.trim().orEmpty()
                val lastName = cells.getOrNull(lastIdx)?.trim().orEmpty()
                if (firstName.isBlank() || lastName.isBlank()) {
                    if (firstName.isNotBlank() || lastName.isNotBlank()) errors.add("Row ${i + 1}: missing name")
                    continue
                }
                rows.add(
                    CsvImporter.LecturerRow(
                        title = cells.getOrNull(titleIdx)?.trim().orEmpty(),
                        firstName = firstName,
                        lastName = lastName,
                        email = cells.getOrNull(emailIdx)?.trim().orEmpty()
                    )
                )
            }
            workbook.close()
        } catch (e: Exception) {
            errors.add("Failed to read file: ${e.message}")
        }

        return ImportResult(rows, errors)
    }

    fun importClassrooms(inputStream: InputStream): ImportResult<CsvImporter.ClassroomRow> {
        val rows = mutableListOf<CsvImporter.ClassroomRow>()
        val errors = mutableListOf<String>()

        try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(0) ?: return ImportResult(emptyList(), listOf("Empty file"))

            val headers = readRowAsStrings(headerRow).map { it.lowercase().trim() }
            val roomIdx = headers.indexOfFirst { it.contains("room") || it.contains("oda") || it.contains("sınıf") || it.contains("code") || it.contains("kod") }
            val capIdx = headers.indexOfFirst { it.contains("capacity") || it.contains("kapasite") }
            val typeIdx = headers.indexOfFirst { it.contains("type") || it.contains("tür") || it.contains("tip") }

            if (roomIdx == -1) {
                return ImportResult(emptyList(), listOf("Missing required column: room_code"))
            }

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val cells = readRowAsStrings(row)
                val roomCode = cells.getOrNull(roomIdx)?.trim().orEmpty()
                if (roomCode.isBlank()) continue
                rows.add(
                    CsvImporter.ClassroomRow(
                        roomCode = roomCode,
                        capacity = cells.getOrNull(capIdx)?.toIntOrNull() ?: 30,
                        type = cells.getOrNull(typeIdx)?.trim()?.lowercase() ?: "theory"
                    )
                )
            }
            workbook.close()
        } catch (e: Exception) {
            errors.add("Failed to read file: ${e.message}")
        }

        return ImportResult(rows, errors)
    }

    // ══════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════

    fun exportCourses(courses: List<Course>, outputStream: OutputStream) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Courses")

        // Header
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

        // Data
        courses.forEachIndexed { i, c ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(c.code)
            row.createCell(1).setCellValue(c.name)
            row.createCell(2).setCellValue(c.theoryHours.toDouble())
            row.createCell(3).setCellValue(c.labHours.toDouble())
            row.createCell(4).setCellValue(c.credits.toDouble())
            row.createCell(5).setCellValue(c.departmentName)
        }

        // Auto-size columns
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
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) num.toLong().toString()
                    else num.toString()
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

    private fun String.toIntOrNull(): Int? {
        return this.trim().toDoubleOrNull()?.toInt()
    }
}
