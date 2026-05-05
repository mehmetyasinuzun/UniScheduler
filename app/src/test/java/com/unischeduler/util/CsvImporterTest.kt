package com.unischeduler.util

import org.junit.Assert.*
import org.junit.Test

class CsvImporterTest {

    // ── Course Parsing ───────────────────────────────────────────────────────

    @Test
    fun `parseCourses handles basic CSV with header`() {
        val csv = """
            code,name,theory_hours,lab_hours,credits
            KM101,Genel Kimya I,4,2,6
            KM102,Genel Kimya II,4,2,6
        """.trimIndent()
        val result = CsvImporter.parseCourses(csv)
        assertEquals(2, result.valid.size)
        assertEquals("KM101", result.valid[0].code)
        assertEquals("Genel Kimya I", result.valid[0].name)
        assertEquals(4, result.valid[0].theoryHours)
        assertEquals(2, result.valid[0].labHours)
        assertEquals(6, result.valid[0].credits)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `parseCourses auto-uppercases course code`() {
        val csv = "code,name\ncs101,Intro to CS"
        val result = CsvImporter.parseCourses(csv)
        assertEquals("CS101", result.valid[0].code)
    }

    @Test
    fun `parseCourses defaults hours to 0 when missing`() {
        val csv = "code,name\nKM101,Kimya"
        val result = CsvImporter.parseCourses(csv)
        assertEquals(0, result.valid[0].theoryHours)
        assertEquals(0, result.valid[0].labHours)
        assertEquals(0, result.valid[0].credits)
    }

    @Test
    fun `parseCourses reports error for row with missing code`() {
        val csv = "code,name\n,Only Name"
        val result = CsvImporter.parseCourses(csv)
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parseCourses reports error for row with missing name`() {
        val csv = "code,name\nKM101,"
        val result = CsvImporter.parseCourses(csv)
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parseCourses handles semicolon separator`() {
        val csv = "code;name;theory_hours\nKM101;Kimya;4"
        val result = CsvImporter.parseCourses(csv)
        assertEquals(1, result.valid.size)
        assertEquals("KM101", result.valid[0].code)
        assertEquals("Kimya", result.valid[0].name)
        assertEquals(4, result.valid[0].theoryHours)
    }

    @Test
    fun `parseCourses handles Turkish characters`() {
        val csv = "code,name\nKM201,Organik Kimya Öğretimi"
        val result = CsvImporter.parseCourses(csv)
        assertEquals("Organik Kimya Öğretimi", result.valid[0].name)
    }

    @Test
    fun `parseCourses handles quoted fields with commas`() {
        val csv = """code,name
KM101,"Kimya, Genel"
"""
        val result = CsvImporter.parseCourses(csv)
        assertEquals("Kimya, Genel", result.valid[0].name)
    }

    @Test
    fun `parseCourses returns error for empty file`() {
        val result = CsvImporter.parseCourses("")
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("empty"))
    }

    // ── Lecturer Parsing ─────────────────────────────────────────────────────

    @Test
    fun `parseLecturers handles basic CSV`() {
        val csv = """
            title,first_name,last_name,email
            Prof.,Ahmet,Yilmaz,ahmet@uni.edu
            Dr.,Elif,Kaya,
        """.trimIndent()
        val result = CsvImporter.parseLecturers(csv)
        assertEquals(2, result.valid.size)
        assertEquals("Prof.", result.valid[0].title)
        assertEquals("Ahmet", result.valid[0].firstName)
        assertEquals("Yilmaz", result.valid[0].lastName)
        assertEquals("ahmet@uni.edu", result.valid[0].email)
        assertNull(result.valid[1].email)
    }

    @Test
    fun `parseLecturers defaults title to Lect`() {
        val csv = "title,first_name,last_name\n,Hasan,Kurt"
        val result = CsvImporter.parseLecturers(csv)
        assertEquals("Lect.", result.valid[0].title)
    }

    @Test
    fun `parseLecturers reports error for missing first name`() {
        val csv = "title,first_name,last_name\nDr.,,Kaya"
        val result = CsvImporter.parseLecturers(csv)
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parseLecturers handles Turkish names correctly`() {
        val csv = "title,first_name,last_name,email\nÖğr. Gör.,Hüseyin,Şahin,hsahin@uni.edu"
        val result = CsvImporter.parseLecturers(csv)
        assertEquals("Öğr. Gör.", result.valid[0].title)
        assertEquals("Hüseyin", result.valid[0].firstName)
        assertEquals("Şahin", result.valid[0].lastName)
    }

    // ── Classroom Parsing ────────────────────────────────────────────────────

    @Test
    fun `parseClassrooms handles basic CSV`() {
        val csv = """
            room_code,capacity,type
            A101,60,theory
            B201,30,lab
        """.trimIndent()
        val result = CsvImporter.parseClassrooms(csv)
        assertEquals(2, result.valid.size)
        assertEquals("A101", result.valid[0].roomCode)
        assertEquals(60, result.valid[0].capacity)
        assertEquals("theory", result.valid[0].type)
        assertEquals("lab", result.valid[1].type)
    }

    @Test
    fun `parseClassrooms defaults type to theory`() {
        val csv = "room_code,capacity\nA101,60"
        val result = CsvImporter.parseClassrooms(csv)
        assertEquals("theory", result.valid[0].type)
    }

    @Test
    fun `parseClassrooms reports error for missing capacity`() {
        val csv = "room_code,capacity,type\nA101,,theory"
        val result = CsvImporter.parseClassrooms(csv)
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parseClassrooms reports error for zero capacity`() {
        val csv = "room_code,capacity,type\nA101,0,theory"
        val result = CsvImporter.parseClassrooms(csv)
        assertTrue(result.valid.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `parseClassrooms normalizes lab type`() {
        val csv = "room_code,capacity,type\nB201,30,Lab"
        val result = CsvImporter.parseClassrooms(csv)
        assertEquals("lab", result.valid[0].type)
    }

    @Test
    fun `parseClassrooms treats unknown type as theory`() {
        val csv = "room_code,capacity,type\nA101,40,other"
        val result = CsvImporter.parseClassrooms(csv)
        assertEquals("theory", result.valid[0].type)
    }

    // ── Edge Cases ───────────────────────────────────────────────────────────

    @Test
    fun `parseCourses skips blank lines`() {
        val csv = "code,name\n\nKM101,Kimya\n\nKM102,Fizik\n"
        val result = CsvImporter.parseCourses(csv)
        assertEquals(2, result.valid.size)
    }

    @Test
    fun `ParseResult isEmpty returns true when no valid rows`() {
        val result = CsvImporter.ParseResult<String>(emptyList(), listOf("error"))
        assertTrue(result.isEmpty)
    }

    @Test
    fun `ParseResult isEmpty returns false when has valid rows`() {
        val result = CsvImporter.ParseResult(listOf("data"), emptyList())
        assertFalse(result.isEmpty)
    }
}
