package com.unischeduler.util

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.User
import org.junit.Assert.*
import org.junit.Test

class CsvExporterTest {

    private val sampleDept = Department(id = 1, name = "Kimya Mühendisliği")

    @Test
    fun `exportCourses generates header row`() {
        val csv = CsvExporter.exportCourses(emptyList())
        assertTrue(csv.contains("code,name,theory_hours,lab_hours,credits,department"))
    }

    @Test
    fun `exportCourses starts with UTF-8 BOM`() {
        val csv = CsvExporter.exportCourses(emptyList())
        assertTrue(csv.startsWith("﻿"))
    }

    @Test
    fun `exportCourses includes all course fields`() {
        val courses = listOf(
            Course(id = 1, code = "KM101", name = "Genel Kimya", theoryHours = 4, labHours = 2, credits = 6, departments = sampleDept)
        )
        val csv = CsvExporter.exportCourses(courses)
        val lines = csv.lines()
        assertTrue(lines.size >= 2)
        val dataLine = lines[1]
        assertTrue(dataLine.contains("KM101"))
        assertTrue(dataLine.contains("Genel Kimya"))
        assertTrue(dataLine.contains("4"))
        assertTrue(dataLine.contains("2"))
        assertTrue(dataLine.contains("6"))
        assertTrue(dataLine.contains("Kimya Mühendisliği"))
    }

    @Test
    fun `exportCourses escapes fields containing commas`() {
        val courses = listOf(
            Course(id = 1, code = "KM101", name = "Kimya, Genel", departments = sampleDept)
        )
        val csv = CsvExporter.exportCourses(courses)
        assertTrue(csv.contains("\"Kimya, Genel\""))
    }

    @Test
    fun `exportLecturers generates correct header`() {
        val csv = CsvExporter.exportLecturers(emptyList())
        assertTrue(csv.contains("title,first_name,last_name,email,department,username"))
    }

    @Test
    fun `exportLecturers includes lecturer data`() {
        val lecturers = listOf(
            Lecturer(
                id = 1, title = "Prof.", firstName = "Ahmet", lastName = "Yılmaz",
                email = "ahmet@uni.edu", departments = sampleDept,
                users = User(username = "ahmet_yilmaz")
            )
        )
        val csv = CsvExporter.exportLecturers(lecturers)
        assertTrue(csv.contains("Prof."))
        assertTrue(csv.contains("Ahmet"))
        assertTrue(csv.contains("Yılmaz"))
        assertTrue(csv.contains("ahmet@uni.edu"))
        assertTrue(csv.contains("ahmet_yilmaz"))
    }

    @Test
    fun `exportClassrooms generates correct header`() {
        val csv = CsvExporter.exportClassrooms(emptyList())
        assertTrue(csv.contains("room_code,capacity,type,department"))
    }

    @Test
    fun `exportClassrooms includes classroom data`() {
        val classrooms = listOf(
            Classroom(id = 1, roomCode = "A101", capacity = 60, type = "theory", departments = sampleDept)
        )
        val csv = CsvExporter.exportClassrooms(classrooms)
        assertTrue(csv.contains("A101"))
        assertTrue(csv.contains("60"))
        assertTrue(csv.contains("theory"))
        assertTrue(csv.contains("Kimya Mühendisliği"))
    }

    @Test
    fun `exportCourses handles Turkish characters`() {
        val courses = listOf(
            Course(id = 1, code = "KM201", name = "Organik Kimya Öğretimi", departments = sampleDept)
        )
        val csv = CsvExporter.exportCourses(courses)
        assertTrue(csv.contains("Organik Kimya Öğretimi"))
    }

    @Test
    fun `exportCourses handles quotes in field values`() {
        val courses = listOf(
            Course(id = 1, code = "KM101", name = "Kimya \"Genel\"", departments = sampleDept)
        )
        val csv = CsvExporter.exportCourses(courses)
        // Quotes should be escaped as ""
        assertTrue(csv.contains("\"\""))
    }
}
