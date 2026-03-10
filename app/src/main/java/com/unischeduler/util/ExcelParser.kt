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
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("Dosya boş")
        }

        val rows = mutableListOf<ImportRow>()
        val workbook = WorkbookFactory.create(ByteArrayInputStream(bytes))
        workbook.use {
            if (it.numberOfSheets <= 0) {
                throw IllegalArgumentException("Excel içinde sayfa bulunamadı")
            }
            val sheet = it.getSheetAt(0)

            // Find header row
            val headerRow = sheet.getRow(0) ?: throw IllegalArgumentException("Başlık satırı bulunamadı")
            val headers = (0 until headerRow.lastCellNum).map { idx ->
                getCellStringValue(headerRow, idx).trim().lowercase()
            }

            val codeIdx = headers.indexOfFirst { it.contains("kod") || it.contains("code") }
            val nameIdx = headers.indexOfFirst { (it.contains("ders") && it.contains("ad")) || it.contains("name") }
            val lecturerIdx = headers.indexOfFirst { it.contains("hoca") || it.contains("öğretim") || it.contains("lecturer") }

            if (codeIdx < 0 || nameIdx < 0 || lecturerIdx < 0) {
                throw IllegalArgumentException(
                    "Başlıklar bulunamadı. Beklenen kolonlar: Ders Kodu, Ders Adı, Öğretim Üyesi"
                )
            }

            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val code = getCellStringValue(row, codeIdx)
                val name = getCellStringValue(row, nameIdx)
                val lecturer = getCellStringValue(row, lecturerIdx)
                if (code.isNotBlank() || name.isNotBlank()) {
                    rows.add(ImportRow(code.trim(), name.trim(), lecturer.trim()))
                }
            }
        }

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
