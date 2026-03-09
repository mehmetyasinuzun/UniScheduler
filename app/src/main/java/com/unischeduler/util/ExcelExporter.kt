package com.unischeduler.util

import android.content.Context
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.ScheduleDraft
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelExporter @Inject constructor() {

    fun exportSchedule(
        context: Context,
        assignments: List<ScheduleAssignment>,
        config: ScheduleConfig,
        courses: List<Course>,
        lecturers: List<Lecturer>,
        fileName: String
    ): String {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Ders Programı")

        val headerRow = sheet.createRow(0)
        ExcelTemplates.SCHEDULE_HEADERS.forEachIndexed { idx, header ->
            headerRow.createCell(idx).setCellValue(header)
        }

        assignments.forEachIndexed { rowIdx, a ->
            val row = sheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(a.courseCode)
            row.createCell(1).setCellValue(a.courseName)
            row.createCell(2).setCellValue(a.lecturerName)
            row.createCell(3).setCellValue(ExcelTemplates.DAY_NAMES[a.dayOfWeek] ?: "${a.dayOfWeek}")
            row.createCell(4).setCellValue(config.slotStartTime(a.slotIndex))
            row.createCell(5).setCellValue(a.classroom)
            row.createCell(6).setCellValue(a.semester)
        }

        return writeToFile(context, workbook, fileName)
    }

    fun exportAvailability(
        context: Context,
        slots: List<AvailabilitySlot>,
        config: ScheduleConfig,
        fileName: String
    ): String {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Müsaitlik")

        val headerRow = sheet.createRow(0)
        ExcelTemplates.AVAILABILITY_HEADERS.forEachIndexed { idx, header ->
            headerRow.createCell(idx).setCellValue(header)
        }

        slots.forEachIndexed { rowIdx, s ->
            val row = sheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(s.lecturerId.toString())
            row.createCell(1).setCellValue(ExcelTemplates.DAY_NAMES[s.dayOfWeek] ?: "${s.dayOfWeek}")
            row.createCell(2).setCellValue(config.slotStartTime(s.slotIndex))
            row.createCell(3).setCellValue(if (s.isAvailable) "Evet" else "Hayır")
        }

        return writeToFile(context, workbook, fileName)
    }

    fun exportCredentials(
        context: Context,
        lecturers: List<Lecturer>,
        fileName: String
    ): String {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Hesap Bilgileri")

        val headerRow = sheet.createRow(0)
        ExcelTemplates.CREDENTIALS_HEADERS.forEachIndexed { idx, header ->
            headerRow.createCell(idx).setCellValue(header)
        }

        lecturers.forEachIndexed { rowIdx, l ->
            val row = sheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(l.fullName)
            row.createCell(1).setCellValue(l.title)
            row.createCell(2).setCellValue(l.username)
            row.createCell(3).setCellValue("${l.username}@uni.edu.tr")
            row.createCell(4).setCellValue(l.password)
        }

        return writeToFile(context, workbook, fileName)
    }

    fun exportDraft(
        context: Context,
        draft: ScheduleDraft,
        currentAssignments: List<ScheduleAssignment>,
        config: ScheduleConfig,
        fileName: String
    ): String {
        val workbook = XSSFWorkbook()

        // Draft sheet
        val draftSheet = workbook.createSheet("Taslak")
        val dh = draftSheet.createRow(0)
        ExcelTemplates.DRAFT_HEADERS.forEachIndexed { idx, header -> dh.createCell(idx).setCellValue(header) }
        draft.assignments.forEachIndexed { rowIdx, a ->
            val row = draftSheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(a.courseCode)
            row.createCell(1).setCellValue(a.courseName)
            row.createCell(2).setCellValue(a.lecturerName)
            row.createCell(3).setCellValue(ExcelTemplates.DAY_NAMES[a.dayOfWeek] ?: "${a.dayOfWeek}")
            row.createCell(4).setCellValue(config.slotStartTime(a.slotIndex))
            row.createCell(5).setCellValue(a.classroom)
        }

        // Current schedule sheet for comparison
        val currentSheet = workbook.createSheet("Mevcut Program")
        val ch = currentSheet.createRow(0)
        ExcelTemplates.DRAFT_HEADERS.forEachIndexed { idx, header -> ch.createCell(idx).setCellValue(header) }
        currentAssignments.forEachIndexed { rowIdx, a ->
            val row = currentSheet.createRow(rowIdx + 1)
            row.createCell(0).setCellValue(a.courseCode)
            row.createCell(1).setCellValue(a.courseName)
            row.createCell(2).setCellValue(a.lecturerName)
            row.createCell(3).setCellValue(ExcelTemplates.DAY_NAMES[a.dayOfWeek] ?: "${a.dayOfWeek}")
            row.createCell(4).setCellValue(config.slotStartTime(a.slotIndex))
            row.createCell(5).setCellValue(a.classroom)
        }

        return writeToFile(context, workbook, fileName)
    }

    private fun writeToFile(context: Context, workbook: XSSFWorkbook, fileName: String): String {
        val dir = File(context.getExternalFilesDir(null), "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file.absolutePath
    }
}
