// MiniXlsxReader — zero-dependency .xlsx parser for Android.
//
// Why we wrote our own:
//   Apache POI 5.x has well-known stability issues on Android. Even with
//   all META-INF/services + ProGuard rules in place, IOUtils' static
//   byteArrayMaxOverride keeps surfacing absurd allocation requests
//   ("100 MB for a 5 KB file") that prevent it from reading even the
//   simplest worksheets. POI is officially unsupported on Android by
//   the Apache team — every workaround is fighting upstream.
//
// What this class does instead:
//   • Treats .xlsx as what it physically is — a ZIP archive containing
//     xl/sharedStrings.xml + xl/worksheets/sheet1.xml.
//   • Reads both via android.util.Xml (the platform's pull parser, no
//     dependency).
//   • Returns rows as List<List<String>>, first row = headers.
//
// What it intentionally doesn't do:
//   • Formulas (we read the cached `<v>` value POI would have computed
//     last save — same value the user sees in Excel anyway).
//   • Styles, formatting, merged cells, charts, images. None of those
//     matter for our import use case (rectangular data tables).
//   • .xls (the legacy 1997 binary format). Stick to .xlsx.
//
// Limits:
//   • In-memory: pulls the whole sheet into a 2-D string list before
//     returning. Fine for the < 10 MB sheets we actually import; would
//     need a streaming variant for 100 k-row enterprise feeds.
package com.unischeduler.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object MiniXlsxReader {

    /**
     * Read sheet 0 from the given .xlsx stream and return rows as
     * List<List<String>>. The first row is the header. Empty cells are
     * preserved as empty strings so column indices line up.
     */
    fun read(input: InputStream): List<List<String>> {
        // We need two passes over the ZIP (sharedStrings before sheet1),
        // but Android's ZipInputStream is single-pass. Buffer the whole
        // archive once. Sample / typical input is a few KB; even a
        // worst-case "import 5000 lecturers" file stays well under 1 MB.
        val archive = input.readBytes()
        val sharedStrings = readSharedStrings(archive)
        return readSheet(archive, sharedStrings)
    }

    // ── Shared strings (xl/sharedStrings.xml) ──────────────────────────────

    private fun readSharedStrings(archive: ByteArray): List<String> {
        val xml = entryBytes(archive, "xl/sharedStrings.xml") ?: return emptyList()
        val out = mutableListOf<String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xml), "UTF-8")
        }
        // <sst><si><t>...</t><t>...</t></si><si>...</si></sst>
        // A <si> element may contain multiple <t> runs (when the cell has
        // mixed formatting); concatenate them into one logical string.
        var inSi = false
        var siText = StringBuilder()
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; siText = StringBuilder() }
                    "t"  -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) siText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t"  -> inT = false
                    "si" -> { out.add(siText.toString()); inSi = false }
                }
            }
            event = parser.next()
        }
        return out
    }

    // ── Worksheet (xl/worksheets/sheet1.xml) ───────────────────────────────

    private fun readSheet(archive: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val xml = entryBytes(archive, "xl/worksheets/sheet1.xml")
            ?: return emptyList()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(xml), "UTF-8")
        }

        val rows = mutableListOf<List<String>>()
        var currentRow = mutableMapOf<Int, String>()  // colIndex -> value
        var maxColInRow = -1

        // Cell parsing state
        var inCell = false
        var cellType: String? = null      // "s"=shared, "str"=inline str, "b"=bool, otherwise number
        var cellColIndex = -1
        var inV = false
        var vBuf = StringBuilder()
        var inInlineStr = false
        var inlineBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableMapOf()
                        maxColInRow = -1
                    }
                    "c" -> {
                        inCell = true
                        cellType = parser.getAttributeValue(null, "t")
                        val ref = parser.getAttributeValue(null, "r")  // e.g. "B12"
                        cellColIndex = colIndexFromRef(ref)
                    }
                    "v" -> if (inCell) { inV = true; vBuf = StringBuilder() }
                    "is", "t" -> if (inCell && cellType == "inlineStr") {
                        // inline string  (uncommon but legal)
                        inInlineStr = true; inlineBuf = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV)        vBuf.append(parser.text)
                    if (inInlineStr) inlineBuf.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> if (inInlineStr) inInlineStr = false
                    "c" -> {
                        if (inCell && cellColIndex >= 0) {
                            val raw = if (cellType == "inlineStr") inlineBuf.toString() else vBuf.toString()
                            val resolved = when (cellType) {
                                "s" -> raw.toIntOrNull()
                                    ?.let { sharedStrings.getOrNull(it) } ?: ""
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                "str", "inlineStr" -> raw
                                // else: numeric / date / general — leave the
                                // raw textual form. Excel stores numbers as
                                // strings like "40" or "1.5" already.
                                else -> raw
                            }
                            currentRow[cellColIndex] = resolved
                            if (cellColIndex > maxColInRow) maxColInRow = cellColIndex
                        }
                        inCell = false
                        cellType = null
                        cellColIndex = -1
                        vBuf = StringBuilder()
                        inlineBuf = StringBuilder()
                    }
                    "row" -> {
                        // Materialise the row as a List, padding empty
                        // columns. Excel skips empty cells from the XML,
                        // so without the explicit pad downstream code
                        // would shift columns left.
                        val list = ArrayList<String>(maxColInRow + 1)
                        for (i in 0..maxColInRow) list.add(currentRow[i].orEmpty())
                        rows.add(list)
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Find a single named entry in the .xlsx ZIP and return its bytes. */
    private fun entryBytes(archive: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    return zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Convert an Excel cell reference like "A1", "B12", "AA3" into the
     * 0-based column index. The row number is ignored (we already track
     * row boundaries via <row> elements).
     */
    internal fun colIndexFromRef(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var col = 0
        for (c in ref) {
            if (c in 'A'..'Z') col = col * 26 + (c - 'A' + 1)
            else if (c in 'a'..'z') col = col * 26 + (c - 'a' + 1)
            else break  // hit the digits — column part finished
        }
        return col - 1  // back to 0-based
    }
}
