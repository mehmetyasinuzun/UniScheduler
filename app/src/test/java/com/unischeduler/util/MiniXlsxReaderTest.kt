package com.unischeduler.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MiniXlsxReaderTest {

    @Test
    fun `colIndexFromRef converts A1-style to zero-based`() {
        assertEquals(0,  MiniXlsxReader.colIndexFromRef("A1"))
        assertEquals(1,  MiniXlsxReader.colIndexFromRef("B12"))
        assertEquals(25, MiniXlsxReader.colIndexFromRef("Z9"))
        assertEquals(26, MiniXlsxReader.colIndexFromRef("AA1"))
        assertEquals(27, MiniXlsxReader.colIndexFromRef("AB100"))
        assertEquals(51, MiniXlsxReader.colIndexFromRef("AZ1"))
        assertEquals(52, MiniXlsxReader.colIndexFromRef("BA1"))
    }

    @Test
    fun `colIndexFromRef returns -1 for empty input`() {
        assertEquals(-1, MiniXlsxReader.colIndexFromRef(""))
        assertEquals(-1, MiniXlsxReader.colIndexFromRef(null))
    }

    @Test
    fun `parses sample courses xlsx from project assets`() {
        val sampleFile = java.io.File("src/main/assets/samples/courses.xlsx")
        if (!sampleFile.exists()) return  // assets may not be on test classpath; skip
        val rows = sampleFile.inputStream().use { MiniXlsxReader.read(it) }
        assertTrue("Expected at least header + 1 data row", rows.size >= 2)
        // Header row
        val headers = rows[0].map { it.lowercase() }
        assertTrue("Code column missing in $headers", headers.any { it.contains("code") })
        assertTrue("Name column missing", headers.any { it.contains("name") })
        // First data row should have a non-blank code
        assertTrue("First data row has empty code: ${rows[1]}", rows[1].first().isNotBlank())
    }

    @Test
    fun `parses sample lecturers xlsx`() {
        val f = java.io.File("src/main/assets/samples/lecturers.xlsx")
        if (!f.exists()) return
        val rows = f.inputStream().use { MiniXlsxReader.read(it) }
        assertTrue(rows.size >= 2)
        val headers = rows[0].map { it.lowercase() }
        assertTrue(headers.any { it.contains("first") || it.contains("ad") })
        assertTrue(headers.any { it.contains("last") || it.contains("soyad") })
    }

    @Test
    fun `parses sample classrooms xlsx`() {
        val f = java.io.File("src/main/assets/samples/classrooms.xlsx")
        if (!f.exists()) return
        val rows = f.inputStream().use { MiniXlsxReader.read(it) }
        assertTrue(rows.size >= 2)
        // Room codes should be on column 0
        val firstRoom = rows[1].first()
        assertNotNull(firstRoom)
        assertTrue("Expected non-blank room code, got '$firstRoom'", firstRoom.isNotBlank())
    }
}
