package com.unischeduler.scheduler

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.Course
import com.unischeduler.data.model.Department
import com.unischeduler.data.model.Lecturer
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import org.junit.Assert.*
import org.junit.Test

class ScheduleGeneratorTest {

    private val defaultSettings = OrgSettings(
        orgId = 1,
        timeStepMinutes = 30,
        activeDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
        dayStart = "09:00",
        dayEnd = "17:00"
    )

    private val dept1 = Department(id = 1, orgId = 1, name = "Bilgisayar Müh.")

    private fun makeCourse(id: Int, code: String, name: String, theory: Int = 3, lab: Int = 0, deptId: Int = 1) =
        Course(id = id, orgId = 1, code = code, name = name, theoryHours = theory, labHours = lab, departmentId = deptId, departments = dept1)

    private fun makeLecturer(id: Int, firstName: String, lastName: String) =
        Lecturer(id = id, orgId = 1, userId = "u$id", title = "Dr.", firstName = firstName, lastName = lastName, departmentId = 1, departments = dept1)

    private fun makeClassroom(id: Int, code: String, capacity: Int = 60, type: String = "theory") =
        Classroom(id = id, orgId = 1, roomCode = code, capacity = capacity, type = type, departmentId = 1)

    private fun makeOffering(
        id: Int, course: Course, lecturer: Lecturer?, classYear: Int = 2, section: String = "A", capacity: Int = 40
    ) = Offering(
        id = id, orgId = 1, courseId = course.id, lecturerId = lecturer?.id,
        academicYear = "2025-2026", term = "Güz", classYear = classYear, section = section,
        capacity = capacity, courses = course, lecturers = lecturer
    )

    private fun makeGenerator(
        classrooms: List<Classroom> = listOf(makeClassroom(1, "Z-05"), makeClassroom(2, "Z-06")),
        busySlots: Map<Int, List<LecturerAvailability>> = emptyMap(),
        existingEntries: List<ScheduleEntry> = emptyList(),
        settings: OrgSettings = defaultSettings,
        preferences: SchedulePreferences = SchedulePreferences()
    ) = ScheduleGenerator(settings, classrooms, busySlots, existingEntries, preferences)


    // ══════════ TEMEL TESTLER ══════════

    @Test
    fun `bos offering listesi - bos sonuc doner`() {
        val gen = makeGenerator()
        val result = gen.generate(emptyList())
        assertEquals(0, result.assigned.size)
        assertEquals(0, result.unassigned.size)
    }

    @Test
    fun `tek offering - basariyla yerlesir`() {
        val course = makeCourse(1, "CNG342", "Data Com", theory = 3)
        val lecturer = makeLecturer(1, "Rezan", "Bakır")
        val offering = makeOffering(1, course, lecturer)

        val gen = makeGenerator()
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.assigned.size)
        assertEquals(0, result.unassigned.size)
        val entry = result.assigned[0]
        assertEquals("CNG342", entry.offering.courses?.code)
        assertTrue(entry.startTime < entry.endTime)
    }

    @Test
    fun `birden fazla offering - hepsi yerlesir`() {
        val c1 = makeCourse(1, "CNG342", "Data Com")
        val c2 = makeCourse(2, "CNG344", "Info Sec")
        val c3 = makeCourse(3, "CNG352", "OS")
        val l1 = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")
        val l3 = makeLecturer(3, "Ayşe", "Kara")

        val offerings = listOf(
            makeOffering(1, c1, l1),
            makeOffering(2, c2, l2),
            makeOffering(3, c3, l3)
        )
        val classrooms = listOf(makeClassroom(1, "Z-05"), makeClassroom(2, "Z-06"), makeClassroom(3, "Z-07"))
        val gen = makeGenerator(classrooms = classrooms)
        val result = gen.generate(offerings)

        assertEquals(3, result.assigned.size)
        assertEquals(0, result.unassigned.size)
    }


    // ══════════ HARD CONSTRAINT TESTLER ══════════

    @Test
    fun `hoca cakismasi - ayni hoca ayni anda iki farkli ders alamaz`() {
        val c1 = makeCourse(1, "CNG342", "Data Com")
        val c2 = makeCourse(2, "CNG344", "Info Sec")
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")

        val offerings = listOf(
            makeOffering(1, c1, lecturer, classYear = 2),
            makeOffering(2, c2, lecturer, classYear = 3)
        )
        val gen = makeGenerator()
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
        val e1 = result.assigned[0]
        val e2 = result.assigned[1]
        assertFalse(
            "Aynı hoca aynı anda iki ders almamalı",
            e1.day == e2.day && overlaps(e1.startTime, e1.endTime, e2.startTime, e2.endTime)
        )
    }

    @Test
    fun `sinif cakismasi - ayni sinif ayni anda iki ders alamaz`() {
        val c1 = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val c2 = makeCourse(2, "CNG344", "Info Sec", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")

        val offerings = listOf(
            makeOffering(1, c1, l1), makeOffering(2, c2, l2)
        )
        val singleRoom = listOf(makeClassroom(1, "Z-05"))
        val gen = makeGenerator(classrooms = singleRoom)
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
        val e1 = result.assigned[0]
        val e2 = result.assigned[1]
        if (e1.day == e2.day) {
            assertFalse(
                "Aynı derslik aynı anda iki ders almamalı",
                overlaps(e1.startTime, e1.endTime, e2.startTime, e2.endTime)
            )
        }
    }

    @Test
    fun `ogrenci cakismasi - ayni sinif-sube ayni anda iki ders alamaz`() {
        val c1 = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val c2 = makeCourse(2, "CNG344", "Info Sec", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")

        val offerings = listOf(
            makeOffering(1, c1, l1, classYear = 2, section = "A"),
            makeOffering(2, c2, l2, classYear = 2, section = "A")
        )
        val gen = makeGenerator()
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
        val e1 = result.assigned[0]
        val e2 = result.assigned[1]
        if (e1.day == e2.day) {
            assertFalse(
                "Aynı sınıf-şube öğrencileri aynı anda iki derste olamaz",
                overlaps(e1.startTime, e1.endTime, e2.startTime, e2.endTime)
            )
        }
    }

    @Test
    fun `hoca musaitligi - mesgul saatte ders konmaz`() {
        val course = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val busyAllWeek = defaultSettings.activeDays.map { day ->
            LecturerAvailability(
                id = 0, orgId = 1, lecturerId = 1, day = day,
                startTime = "09:00", endTime = "16:00"
            )
        }
        val gen = makeGenerator(busySlots = mapOf(1 to busyAllWeek))
        val result = gen.generate(listOf(offering))

        if (result.assigned.isNotEmpty()) {
            val entry = result.assigned[0]
            val startMin = toMinutes(entry.startTime)
            assertTrue("Ders meşgul saatin dışında olmalı", startMin >= toMinutes("16:00"))
        }
    }

    @Test
    fun `tamamen mesgul hoca - ders yerlesmez`() {
        val course = makeCourse(1, "CNG342", "Data Com")
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val busyAllWeek = defaultSettings.activeDays.map { day ->
            LecturerAvailability(
                id = 0, orgId = 1, lecturerId = 1, day = day,
                startTime = "09:00", endTime = "17:00"
            )
        }
        val gen = makeGenerator(busySlots = mapOf(1 to busyAllWeek))
        val result = gen.generate(listOf(offering))

        assertEquals("Tamamen meşgul hocanın dersi yerleşmemeli", 0, result.assigned.size)
        assertEquals(1, result.unassigned.size)
    }

    @Test
    fun `kapasite yetersizligi - kucuk sinif yeterli degilse buyugune atanir`() {
        val course = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer, capacity = 80)

        val classrooms = listOf(
            makeClassroom(1, "Z-01", capacity = 30),
            makeClassroom(2, "Z-02", capacity = 100)
        )
        val gen = makeGenerator(classrooms = classrooms)
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.assigned.size)
        assertEquals("Z-02", result.assigned[0].classroom.roomCode)
    }

    @Test
    fun `lab dersi lab sinifina atanir`() {
        val course = makeCourse(1, "CNG342", "Data Com Lab", theory = 0, lab = 2)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val classrooms = listOf(
            makeClassroom(1, "Z-05", type = "theory"),
            makeClassroom(2, "LAB-1", type = "lab")
        )
        val gen = makeGenerator(classrooms = classrooms)
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.assigned.size)
        assertEquals("lab", result.assigned[0].classroom.type)
    }

    @Test
    fun `mevcut entry ile cakismaz`() {
        val c1 = makeCourse(1, "CNG342", "Data Com", theory = 3)
        val c2 = makeCourse(2, "CNG344", "Info Sec", theory = 3)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")

        val existingEntry = ScheduleEntry(
            id = 99, orgId = 1, offeringId = 99, lecturerId = 1, classroomId = 1,
            day = "Monday", startTime = "09:00", endTime = "12:00",
            offerings = Offering(id = 99, courses = c1, classYear = 2, section = "A", lecturers = lecturer),
            classrooms = makeClassroom(1, "Z-05")
        )

        val offering = makeOffering(2, c2, lecturer)
        val gen = makeGenerator(existingEntries = listOf(existingEntry))
        val result = gen.generate(listOf(offering))

        if (result.assigned.isNotEmpty()) {
            val entry = result.assigned[0]
            if (entry.day == "Monday" && entry.lecturerId == 1) {
                assertFalse(
                    "Mevcut entry ile çakışmamalı",
                    overlaps(entry.startTime, entry.endTime, "09:00", "12:00")
                )
            }
        }
    }


    // ══════════ DARBOĞAZ RAPORU ══════════

    @Test
    fun `tamamen mesgul hoca - neden mesgul oldugu aciklanir`() {
        val course = makeCourse(1, "CNG342", "Data Com")
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val busyAllWeek = defaultSettings.activeDays.map { day ->
            LecturerAvailability(id = 0, orgId = 1, lecturerId = 1, day = day, startTime = "09:00", endTime = "17:00")
        }
        val gen = makeGenerator(busySlots = mapOf(1 to busyAllWeek))
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.failures.size)
        val failure = result.failures[0]
        assertTrue("Failure reasons should mention lecturer availability",
            failure.reasons.any { it.contains("müsait değil") || it.contains("meşgul") })
    }

    @Test
    fun `kapasite yetersizligi - derslik sorunu aciklanir`() {
        val course = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer, capacity = 200)

        val smallRooms = listOf(makeClassroom(1, "Z-01", capacity = 30), makeClassroom(2, "Z-02", capacity = 50))
        val gen = makeGenerator(classrooms = smallRooms)
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.failures.size)
        assertTrue("Failure should mention capacity",
            result.failures[0].reasons.any { it.contains("kapasite") || it.contains("derslik") })
    }

    @Test
    fun `lab yok - lab eksikligi aciklanir`() {
        val course = makeCourse(1, "CNG342L", "Lab", theory = 0, lab = 2)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val theoryOnly = listOf(makeClassroom(1, "Z-05", type = "theory"))
        val gen = makeGenerator(classrooms = theoryOnly)
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.failures.size)
        assertTrue("Failure should mention lab",
            result.failures[0].reasons.any { it.contains("lab") || it.contains("Lab") })
    }

    @Test
    fun `basarili yerlestirme - failure yok`() {
        val course = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Yılmaz")
        val offering = makeOffering(1, course, lecturer)

        val gen = makeGenerator()
        val result = gen.generate(listOf(offering))

        assertEquals(0, result.failures.size)
        assertEquals(1, result.assigned.size)
    }


    // ══════════ SOFT CONSTRAINT: ÖĞRENCİ KOMPAKTLIK ══════════

    @Test
    fun `ogrenci grubu dersleri ayni gunde kompakt yerlesir`() {
        val c1 = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val c2 = makeCourse(2, "CNG344", "Info Sec", theory = 1)
        val c3 = makeCourse(3, "CNG352", "OS", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")
        val l3 = makeLecturer(3, "Ayşe", "Kara")

        val offerings = listOf(
            makeOffering(1, c1, l1, classYear = 2, section = "A"),
            makeOffering(2, c2, l2, classYear = 2, section = "A"),
            makeOffering(3, c3, l3, classYear = 2, section = "A")
        )
        val classrooms = listOf(makeClassroom(1, "Z-05"), makeClassroom(2, "Z-06"), makeClassroom(3, "Z-07"))
        val gen = makeGenerator(classrooms = classrooms)
        val result = gen.generate(offerings)

        assertEquals(3, result.assigned.size)

        val byDay = result.assigned.groupBy { it.day }
        for ((_, entries) in byDay) {
            if (entries.size >= 2) {
                val sorted = entries.sortedBy { toMinutes(it.startTime) }
                for (i in 0 until sorted.size - 1) {
                    val gap = toMinutes(sorted[i + 1].startTime) - toMinutes(sorted[i].endTime)
                    assertTrue(
                        "Aynı gündeki dersler arası boşluk 2 saatten az olmalı (bulundu: ${gap} dk)",
                        gap <= 120
                    )
                }
            }
        }
    }

    @Test
    fun `farkli sinif-sube ogrencileri birbirini etkilemez`() {
        val c1 = makeCourse(1, "CNG342", "Data Com", theory = 1)
        val c2 = makeCourse(2, "MAT201", "Calculus", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Yılmaz")
        val l2 = makeLecturer(2, "Veli", "Demir")

        val offerings = listOf(
            makeOffering(1, c1, l1, classYear = 2, section = "A"),
            makeOffering(2, c2, l2, classYear = 3, section = "B")
        )
        val gen = makeGenerator()
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
    }


    // ══════════ ÇOKLU ALTERNATİF ══════════

    @Test
    fun `generateAlternatives - birden fazla farkli sonuc uretir`() {
        val courses = (1..5).map { makeCourse(it, "CNG${340 + it}", "Ders $it", theory = 1) }
        val lecturers = (1..5).map { makeLecturer(it, "Hoca$it", "Soy$it") }
        val offerings = (0..4).map { makeOffering(it + 1, courses[it], lecturers[it], classYear = 2, section = "A") }
        val classrooms = (1..3).map { makeClassroom(it, "Z-0$it") }

        val results = ScheduleGenerator.generateAlternatives(
            count = 5,
            settings = defaultSettings,
            classrooms = classrooms,
            busySlots = emptyMap(),
            existingEntries = emptyList(),
            offerings = offerings
        )

        assertEquals(5, results.size)

        results.forEach { result ->
            assertEquals("Her alternatif tüm dersleri yerleştirmeli", 5, result.assigned.size)
        }

        val uniqueFingerprints = results.map { result ->
            result.assigned.sortedBy { it.offering.id }.map { "${it.day}|${it.startTime}" }.joinToString(",")
        }.toSet()
        assertTrue("En az 2 farklı alternatif olmalı (bulundu: ${uniqueFingerprints.size})", uniqueFingerprints.size >= 2)
    }

    @Test
    fun `generateAlternatives - en iyi sonuc basta`() {
        val c1 = makeCourse(1, "CNG342", "DC", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Y")
        val offerings = listOf(makeOffering(1, c1, l1))

        val results = ScheduleGenerator.generateAlternatives(
            count = 3,
            settings = defaultSettings,
            classrooms = listOf(makeClassroom(1, "Z-05")),
            busySlots = emptyMap(),
            existingEntries = emptyList(),
            offerings = offerings
        )

        assertTrue("En az 1 sonuç olmalı", results.isNotEmpty())
        assertTrue("İlk sonuç en iyi olmalı",
            results.first().assigned.size >= results.last().assigned.size)
    }


    // ══════════ TERCİHLER (PREFERENCES) TESTLERİ ══════════

    @Test
    fun `SPREAD modu - ayni grup dersleri farkli gunlere dagitir`() {
        val prefs = SchedulePreferences(studentCompactness = SchedulePreferences.CompactnessMode.SPREAD)
        val courses = (1..4).map { makeCourse(it, "CNG${340 + it}", "Ders $it", theory = 1) }
        val lecturers = (1..4).map { makeLecturer(it, "Hoca$it", "Soy$it") }
        val offerings = (0..3).map {
            makeOffering(it + 1, courses[it], lecturers[it], classYear = 2, section = "A")
        }
        val classrooms = (1..4).map { makeClassroom(it, "Z-0$it") }
        val gen = makeGenerator(classrooms = classrooms, preferences = prefs)
        val result = gen.generate(offerings)

        assertEquals(4, result.assigned.size)
        val uniqueDays = result.assigned.map { it.day }.distinct().size
        assertTrue("SPREAD modunda 4 ders en az 2 farklı güne dağılmalı (bulundu: $uniqueDays)", uniqueDays >= 2)
    }

    @Test
    fun `hoca gunluk ders limiti - limit asilmamaya calisilir`() {
        val prefs = SchedulePreferences(lecturerMaxDailySlots = 1)
        val c1 = makeCourse(1, "CNG342", "DC", theory = 1)
        val c2 = makeCourse(2, "CNG344", "IS", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Y")

        val offerings = listOf(
            makeOffering(1, c1, lecturer, classYear = 2),
            makeOffering(2, c2, lecturer, classYear = 3)
        )
        val gen = makeGenerator(preferences = prefs)
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
        val byDay = result.assigned.groupBy { it.day }
        val maxPerDay = byDay.values.maxOf { it.size }
        assertTrue("Günlük limit 1 iken hoca günde en fazla 1 ders almalı (bulundu: $maxPerDay)", maxPerDay <= 1)
    }

    @Test
    fun `gun dengeleme acik - dersler gunlere esit dagilir`() {
        val prefs = SchedulePreferences(dayBalancing = true, studentCompactness = SchedulePreferences.CompactnessMode.NONE)
        val courses = (1..5).map { makeCourse(it, "CNG${340 + it}", "Ders $it", theory = 1) }
        val lecturers = (1..5).map { makeLecturer(it, "Hoca$it", "Soy$it") }
        val offerings = (0..4).map {
            makeOffering(it + 1, courses[it], lecturers[it], classYear = it % 3 + 1, section = "A")
        }
        val classrooms = (1..3).map { makeClassroom(it, "Z-0$it") }
        val gen = makeGenerator(classrooms = classrooms, preferences = prefs)
        val result = gen.generate(offerings)

        assertEquals(5, result.assigned.size)
        val byDay = result.assigned.groupBy { it.day }
        val maxPerDay = byDay.values.maxOfOrNull { it.size } ?: 0
        assertTrue("Dengeleme açıkken günde en fazla 3 ders olmalı (bulundu: $maxPerDay)", maxPerDay <= 3)
    }

    @Test
    fun `saat tercihi - tercih edilen aralikta yerlesir`() {
        val prefs = SchedulePreferences(
            preferredStartTime = "09:00",
            preferredEndTime = "12:00",
            studentCompactness = SchedulePreferences.CompactnessMode.NONE
        )
        val c1 = makeCourse(1, "CNG342", "DC", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Y")
        val offering = makeOffering(1, c1, l1)

        val gen = makeGenerator(preferences = prefs)
        val result = gen.generate(listOf(offering))

        assertEquals(1, result.assigned.size)
        val entry = result.assigned[0]
        val startMin = toMinutes(entry.startTime)
        val endMin = toMinutes(entry.endTime)
        assertTrue(
            "Tercih edilen aralıkta (09:00-12:00) yerleşmeli, bulundu: ${entry.startTime}-${entry.endTime}",
            startMin >= toMinutes("09:00") && endMin <= toMinutes("12:00")
        )
    }

    @Test
    fun `NONE modu - dersler yine de yerlesir hard constraintler korunur`() {
        val prefs = SchedulePreferences(studentCompactness = SchedulePreferences.CompactnessMode.NONE)
        val c1 = makeCourse(1, "CNG342", "DC", theory = 1)
        val c2 = makeCourse(2, "CNG344", "IS", theory = 1)
        val lecturer = makeLecturer(1, "Ali", "Y")

        val offerings = listOf(
            makeOffering(1, c1, lecturer, classYear = 2),
            makeOffering(2, c2, lecturer, classYear = 3)
        )
        val gen = makeGenerator(preferences = prefs)
        val result = gen.generate(offerings)

        assertEquals(2, result.assigned.size)
        val e1 = result.assigned[0]
        val e2 = result.assigned[1]
        if (e1.day == e2.day) {
            assertFalse(
                "NONE modunda da hoca çakışması olmamalı",
                overlaps(e1.startTime, e1.endTime, e2.startTime, e2.endTime)
            )
        }
    }

    @Test
    fun `generateAlternatives tercihleri iletiyor`() {
        val prefs = SchedulePreferences(
            studentCompactness = SchedulePreferences.CompactnessMode.SPREAD,
            alternativeCount = 3
        )
        val c1 = makeCourse(1, "CNG342", "DC", theory = 1)
        val l1 = makeLecturer(1, "Ali", "Y")
        val offerings = listOf(makeOffering(1, c1, l1))

        val results = ScheduleGenerator.generateAlternatives(
            count = 3,
            settings = defaultSettings,
            classrooms = listOf(makeClassroom(1, "Z-05")),
            busySlots = emptyMap(),
            existingEntries = emptyList(),
            offerings = offerings,
            preferences = prefs
        )

        assertTrue("En az 1 sonuç olmalı", results.isNotEmpty())
        results.forEach { assertEquals(1, it.assigned.size) }
    }

    // ══════════ YARDIMCI METODLAR ══════════

    private fun overlaps(s1: String, e1: String, s2: String, e2: String): Boolean {
        val a0 = toMinutes(s1); val a1 = toMinutes(e1)
        val b0 = toMinutes(s2); val b1 = toMinutes(e2)
        return a0 < b1 && b0 < a1
    }

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
