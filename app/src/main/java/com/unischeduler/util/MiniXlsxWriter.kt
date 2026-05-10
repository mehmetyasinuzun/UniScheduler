// MiniXlsxWriter — zero-dependency .xlsx exporter for Android.
//
// Same rationale as MiniXlsxReader: Apache POI is unstable on Android
// (its writer pipeline pulls in the same XmlBeans + IOUtils chain that
// breaks reads). We sidestep all of that by emitting the .xlsx ZIP
// archive ourselves with java.util.zip + raw XML strings.
//
// What we produce:
//   • A valid Office Open XML SpreadsheetML 2007+ workbook (.xlsx)
//   • Single sheet, no styles other than a bold header row
//   • String cells written as inline strings (`<is><t>...</t></is>`),
//     numeric cells as `<v>` directly. No shared-string table — the
//     archive layout stays trivial.
//
// Successfully opens in: Microsoft Excel 2010+, LibreOffice Calc,
// Google Sheets, Apple Numbers. Validated against the same import
// pipeline (MiniXlsxReader) so a round-trip is lossless.
package com.unischeduler.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MiniXlsxWriter {

    /** A single cell value. Numeric is rendered without quotes for
     *  proper Excel sorting; string is escaped inline. */
    sealed class Cell {
        data class Str(val value: String) : Cell()
        data class Num(val value: Double) : Cell()
    }

    /**
     * Write a single-sheet workbook. headers + each row become one
     * row of cells. `headers.size` should match every `rows[i].size`.
     */
    fun write(
        out: OutputStream,
        sheetName: String,
        headers: List<String>,
        rows: List<List<Cell>>
    ) {
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml",        contentTypesXml())
            zip.entry("_rels/.rels",                rootRelsXml())
            zip.entry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            zip.entry("xl/workbook.xml",            workbookXml(sheetName))
            zip.entry("xl/styles.xml",              stylesXml())
            zip.entry("xl/worksheets/sheet1.xml",   sheetXml(headers, rows))
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ── Static parts (Office Open XML boilerplate) ─────────────────────────

    private fun contentTypesXml() = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>
""".trim()

    private fun rootRelsXml() = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
""".trim()

    private fun workbookRelsXml() = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
""".trim()

    private fun workbookXml(sheetName: String) = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets>
        <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
    </sheets>
</workbook>
""".trim()

    /** A minimal styles.xml with two cell formats:
     *    s=0  default
     *    s=1  bold (used for the header row). */
    private fun stylesXml() = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="2">
        <font><sz val="11"/><name val="Calibri"/></font>
        <font><b/><sz val="11"/><name val="Calibri"/></font>
    </fonts>
    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
    <borders count="1"><border/></borders>
    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
    <cellXfs count="2">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
        <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    </cellXfs>
</styleSheet>
""".trim()

    // ── Sheet body ────────────────────────────────────────────────────────

    private fun sheetXml(headers: List<String>, rows: List<List<Cell>>): String {
        val sb = StringBuilder(2048 + rows.size * 256)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        // Header row (bold, s="1")
        sb.append("""<row r="1">""")
        headers.forEachIndexed { col, h ->
            sb.append("""<c r="${cellRef(col, 1)}" s="1" t="inlineStr"><is><t>""")
            sb.append(escapeXml(h))
            sb.append("""</t></is></c>""")
        }
        sb.append("""</row>""")

        // Data rows
        rows.forEachIndexed { r, cells ->
            val rowNum = r + 2
            sb.append("""<row r="$rowNum">""")
            cells.forEachIndexed { col, cell ->
                val ref = cellRef(col, rowNum)
                when (cell) {
                    is Cell.Num -> {
                        sb.append("""<c r="$ref"><v>""")
                        sb.append(formatNumber(cell.value))
                        sb.append("""</v></c>""")
                    }
                    is Cell.Str -> if (cell.value.isNotEmpty()) {
                        sb.append("""<c r="$ref" t="inlineStr"><is><t>""")
                        sb.append(escapeXml(cell.value))
                        sb.append("""</t></is></c>""")
                    }
                    // empty strings → omit cell entirely (Excel default)
                }
            }
            sb.append("""</row>""")
        }

        sb.append("""</sheetData></worksheet>""")
        return sb.toString()
    }

    /** Convert (col=0, row=1) → "A1", (col=27, row=2) → "AB2". */
    internal fun cellRef(col: Int, row: Int): String {
        val sb = StringBuilder()
        var c = col + 1
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.append('A' + rem)
            c = (c - 1) / 26
        }
        sb.reverse()
        sb.append(row)
        return sb.toString()
    }

    /** Render a Double without trailing ".0" for integer values, since
     *  Excel users expect "30" not "30.0" for capacity columns. */
    private fun formatNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else v.toString()

    /** XML 1.0 escaping plus stripping of forbidden control chars
     *  (0x00-0x1F except TAB/LF/CR). Excel refuses to open a file with
     *  raw 0x01 etc. even when escaped. */
    internal fun escapeXml(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            val cp = c.code
            if (cp < 0x20 && cp != 0x09 && cp != 0x0A && cp != 0x0D) continue
            when (c) {
                '<'  -> sb.append("&lt;")
                '>'  -> sb.append("&gt;")
                '&'  -> sb.append("&amp;")
                '"'  -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
