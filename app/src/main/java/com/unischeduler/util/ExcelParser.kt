package com.unischeduler.util

import com.unischeduler.domain.usecase.import_data.ImportRow
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelParser @Inject constructor() {

    fun parse(bytes: ByteArray): List<ImportRow> {
        val rows = mutableListOf<ImportRow>()
        val workbook = WorkbookFactory.create(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(0)

        // Find header row
        val headerRow = sheet.getRow(0) ?: return emptyList()
        val headers = (0 until headerRow.lastCellNum).map { idx ->
            getCellStringValue(headerRow, idx).trim().lowercase()
        }

        val codeIdx = headers.indexOfFirst { it.contains("kod") || it.contains("code") }
        val nameIdx = headers.indexOfFirst { it.contains("ders") && it.contains("ad") || it.contains("name") }
        val lecturerIdx = headers.indexOfFirst { it.contains("hoca") || it.contains("öğretim") || it.contains("lecturer") }

        if (codeIdx < 0 || nameIdx < 0 || lecturerIdx < 0) {
            // Fallback: assume columns are in order (code, name, lecturer)
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val code = getCellStringValue(row, 0)
                val name = getCellStringValue(row, 1)
                val lecturer = getCellStringValue(row, 2)
                if (code.isNotBlank() || name.isNotBlank()) {
                    rows.add(ImportRow(code, name, lecturer))
                }
            }
        } else {
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val code = getCellStringValue(row, codeIdx)
                val name = getCellStringValue(row, nameIdx)
                val lecturer = getCellStringValue(row, lecturerIdx)
                if (code.isNotBlank() || name.isNotBlank()) {
                    rows.add(ImportRow(code, name, lecturer))
                }
            }
        }

        workbook.close()
        return rows
    }

    private fun getCellStringValue(row: org.apache.poi.ss.usermodel.Row, idx: Int): String {
        val cell = row.getCell(idx) ?: return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue ?: ""
            CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try { cell.stringCellValue } catch (_: Exception) { "" }
            else -> ""
        }
    }
}
