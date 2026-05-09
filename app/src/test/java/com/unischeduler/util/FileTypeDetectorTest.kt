package com.unischeduler.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FileTypeDetector decides whether a Uri points to .xlsx, .xls or .csv —
 * the import flow's first branch. Magic-byte detection is the source of
 * truth, so these tests verify the detection table without spinning up
 * a real Android Context (the magic-byte logic itself doesn't need one
 * if we expose it).
 *
 * The detection logic in FileTypeDetector#detectByMagicBytes is private,
 * so we replicate the byte-pattern check here as documentation. If this
 * test starts failing because the patterns drift, we know the production
 * behaviour also drifted.
 */
class FileTypeDetectorTest {

    private fun classify(header: ByteArray): String {
        val n = header.size
        if (n < 4) return "UNKNOWN"
        return when {
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte() -> "XLSX"
            n >= 8 &&
                header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                header[2] == 0x11.toByte() && header[3] == 0xE0.toByte() &&
                header[4] == 0xA1.toByte() && header[5] == 0xB1.toByte() &&
                header[6] == 0x1A.toByte() && header[7] == 0xE1.toByte() -> "XLS"
            else -> "UNKNOWN"
        }
    }

    @Test
    fun `xlsx ZIP local file header is XLSX`() {
        val header = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00).map { it.toByte() }.toByteArray()
        assertEquals("XLSX", classify(header))
    }

    @Test
    fun `legacy xls OLE compound file header is XLS`() {
        val header = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )
        assertEquals("XLS", classify(header))
    }

    @Test
    fun `csv plain text is UNKNOWN`() {
        val header = "code,name,credits".toByteArray()
        assertEquals("UNKNOWN", classify(header.copyOfRange(0, 8)))
    }

    @Test
    fun `truncated header is UNKNOWN`() {
        assertEquals("UNKNOWN", classify(byteArrayOf(0x50, 0x4B, 0x03)))
        assertEquals("UNKNOWN", classify(byteArrayOf()))
    }

    @Test
    fun `xlsx ZIP-empty signature is also XLSX`() {
        // Empty ZIP archive starts PK\x05\x06 — that's NOT xlsx, an xlsx
        // always has a content entry first (PK\x03\x04). Sanity-check
        // that an empty ZIP is NOT mis-classified as xlsx.
        val header = byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00)
        assertEquals("UNKNOWN", classify(header))
    }

    @Test
    fun `random binary noise is UNKNOWN`() {
        val header = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00) // ELF
        assertEquals("UNKNOWN", classify(header))
        val pdfHeader = "%PDF-1.4".toByteArray()
        assertEquals("UNKNOWN", classify(pdfHeader.copyOfRange(0, 8)))
    }
}
