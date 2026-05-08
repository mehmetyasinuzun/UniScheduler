// FileTypeDetector — robust spreadsheet vs CSV detection.
//
// Why this exists: Android's content URIs from arbitrary file pickers
// (Drive, Discord, "Recent files", DocumentsUI from a different provider)
// often hand back URIs whose `lastPathSegment` is an opaque ID rather than
// the original filename, so naïve `endsWith(".xlsx")` checks misclassify
// the file as CSV. The result was that XLSX archives were fed into the
// CSV parser, which then "saw" garbage from the ZIP container as rows
// (with no first_name / last_name columns) — every row was rejected.
//
// We now decide based on three layered signals:
//   1. ContentResolver MIME type (most reliable when the provider sets it)
//   2. File extension parsed out of either the URI path or the
//      OpenableColumns DISPLAY_NAME (DocumentsContract)
//   3. Magic bytes — read the first 8 bytes, then look for the ZIP local-
//      file-header (`PK\x03\x04`, xlsx) or the OLE compound file header
//      (`D0 CF 11 E0 A1 B1 1A E1`, legacy xls)
//
// Magic-byte detection is the source of truth — extensions and MIME types
// can be wrong, the file itself cannot lie about its container format.
package com.unischeduler.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileTypeDetector {

    enum class Kind { XLSX, XLS, CSV, UNKNOWN }

    /**
     * Classify the file behind [uri]. Reads up to 8 bytes from the stream so
     * the caller MUST re-open the InputStream after this call (the byte
     * offset is consumed and Android InputStreams are typically not
     * resettable across content providers).
     */
    fun detect(context: Context, uri: Uri): Kind {
        val byMagic = detectByMagicBytes(context, uri)
        if (byMagic != Kind.UNKNOWN) return byMagic

        val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        when {
            "spreadsheetml" in mime || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> return Kind.XLSX
            "ms-excel" in mime || mime == "application/vnd.ms-excel" -> return Kind.XLS
            mime.startsWith("text/") || mime == "application/csv" || "csv" in mime -> return Kind.CSV
        }

        val name = displayName(context, uri).lowercase()
        return when {
            name.endsWith(".xlsx") -> Kind.XLSX
            name.endsWith(".xls")  -> Kind.XLS
            name.endsWith(".csv")  -> Kind.CSV
            name.endsWith(".tsv")  -> Kind.CSV
            else -> Kind.UNKNOWN
        }
    }

    private fun detectByMagicBytes(context: Context, uri: Uri): Kind {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(8)
                val n = input.read(header)
                if (n < 4) return Kind.UNKNOWN
                when {
                    // ZIP local file header — every .xlsx is a ZIP.
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte() -> Kind.XLSX
                    // OLE compound file (BIFF / legacy .xls).
                    n >= 8 &&
                        header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                        header[2] == 0x11.toByte() && header[3] == 0xE0.toByte() &&
                        header[4] == 0xA1.toByte() && header[5] == 0xB1.toByte() &&
                        header[6] == 0x1A.toByte() && header[7] == 0xE1.toByte() -> Kind.XLS
                    else -> Kind.UNKNOWN
                }
            } ?: Kind.UNKNOWN
        } catch (_: Exception) {
            Kind.UNKNOWN
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        // Try DocumentsContract DISPLAY_NAME first.
        val cursor = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } catch (_: Exception) { null }
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        // Fall back to URI segment.
        return uri.lastPathSegment.orEmpty()
    }
}
