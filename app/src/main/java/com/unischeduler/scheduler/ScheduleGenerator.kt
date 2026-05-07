package com.unischeduler.scheduler

import com.unischeduler.data.model.Classroom
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.Offering
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry

data class TimeSlot(
    val day: String,
    val startTime: String,
    val endTime: String
)

data class ProposedEntry(
    val offering: Offering,
    val lecturerId: Int?,
    val classroom: Classroom,
    val day: String,
    val startTime: String,
    val endTime: String
)

data class FailureReason(
    val offering: Offering,
    val reasons: List<String>
)

data class ScheduleResult(
    val assigned: List<ProposedEntry>,
    val unassigned: List<Offering>,
    val failures: List<FailureReason>,
    val score: Int
)

class ScheduleGenerator(
    private val settings: OrgSettings,
    private val classrooms: List<Classroom>,
    private val busySlots: Map<Int, List<LecturerAvailability>>,
    private val existingEntries: List<ScheduleEntry>,
    private val preferences: SchedulePreferences = SchedulePreferences(),
    private val seed: Long = 0L
) {

    private val activeDays = settings.activeDays.ifEmpty {
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    }
    private val dayStartMin = toMinutes(settings.dayStart)
    private val dayEndMin = toMinutes(settings.dayEnd)
    private val step = settings.timeStepMinutes.coerceAtLeast(5)
    private val random = if (seed != 0L) java.util.Random(seed) else null

    private val prefStartMin = if (preferences.preferredStartTime.isNotBlank()) toMinutes(preferences.preferredStartTime) else -1
    private val prefEndMin = if (preferences.preferredEndTime.isNotBlank()) toMinutes(preferences.preferredEndTime) else -1

    private val assigned = mutableListOf<ProposedEntry>()
    private val usedSlots = mutableListOf<OccupiedSlot>()

    companion object {
        fun generateAlternatives(
            count: Int,
            settings: OrgSettings,
            classrooms: List<Classroom>,
            busySlots: Map<Int, List<LecturerAvailability>>,
            existingEntries: List<ScheduleEntry>,
            offerings: List<Offering>,
            preferences: SchedulePreferences = SchedulePreferences()
        ): List<ScheduleResult> {
            val results = mutableListOf<ScheduleResult>()

            val base = ScheduleGenerator(settings, classrooms, busySlots, existingEntries, preferences, seed = 0L)
            results.add(base.generate(offerings))

            for (i in 1 until count) {
                val gen = ScheduleGenerator(settings, classrooms, busySlots, existingEntries, preferences, seed = i.toLong() * 31 + 7)
                results.add(gen.generate(offerings))
            }

            return results.sortedByDescending { it.assigned.size * 1000 + it.score }
        }
    }

    private data class OccupiedSlot(
        val day: String,
        val startMin: Int,
        val endMin: Int,
        val lecturerId: Int?,
        val classroomId: Int,
        val classYear: Int,
        val section: String,
        val departmentId: Int?
    )

    init {
        existingEntries.forEach { entry ->
            usedSlots.add(
                OccupiedSlot(
                    day = entry.day,
                    startMin = toMinutes(entry.startTime),
                    endMin = toMinutes(entry.endTime),
                    lecturerId = entry.lecturerId,
                    classroomId = entry.classroomId,
                    classYear = entry.offerings?.classYear ?: 0,
                    section = entry.offerings?.section ?: "",
                    departmentId = entry.offerings?.courses?.departmentId
                )
            )
        }
    }

    fun generate(offerings: List<Offering>): ScheduleResult {
        val sorted = sortByConstraintLevel(offerings)
        val unassigned = mutableListOf<Offering>()
        val failures = mutableListOf<FailureReason>()

        for (offering in sorted) {
            val result = tryPlace(offering)
            if (!result.first) {
                unassigned.add(offering)
                failures.add(FailureReason(offering, result.second))
            }
        }

        return ScheduleResult(
            assigned = assigned.toList(),
            unassigned = unassigned,
            failures = failures,
            score = calculateScore()
        )
    }

    private fun sortByConstraintLevel(offerings: List<Offering>): List<Offering> {
        return offerings.sortedBy { offering ->
            val lecturerId = offering.lecturerId
            val base = if (lecturerId == null) {
                Int.MAX_VALUE / 2
            } else {
                val busy = busySlots[lecturerId] ?: emptyList()
                countFreeSlots(lecturerId, busy, offering)
            }
            base + (random?.nextInt(5) ?: 0)
        }
    }

    private fun countFreeSlots(lecturerId: Int, busy: List<LecturerAvailability>, offering: Offering): Int {
        val durationMin = getSlotDuration(offering)
        var count = 0
        for (day in activeDays) {
            val slots = generateTimeSlots(day, durationMin)
            for (slot in slots) {
                if (!isLecturerBusy(lecturerId, day, toMinutes(slot.startTime), toMinutes(slot.endTime), busy)) {
                    count++
                }
            }
        }
        return count
    }

    private fun tryPlace(offering: Offering): Pair<Boolean, List<String>> {
        val lecturerId = offering.lecturerId
        val durationMin = getSlotDuration(offering)
        val candidateClassrooms = findSuitableClassrooms(offering)

        if (candidateClassrooms.isEmpty()) {
            val reasons = mutableListOf<String>()
            val needLab = (offering.courses?.labHours ?: 0) > 0
            if (needLab) {
                val labRooms = classrooms.filter { it.type == "lab" }
                if (labRooms.isEmpty()) {
                    reasons.add("Sistemde hiç lab dersliği tanımlı değil")
                } else {
                    reasons.add("Lab derslikleri kapasite yetersiz (gerekli: ${offering.capacity}, mevcut en büyük: ${labRooms.maxOf { it.capacity }})")
                }
            } else {
                reasons.add("Uygun kapasitede derslik yok (gerekli: ${offering.capacity}, mevcut en büyük: ${classrooms.maxOfOrNull { it.capacity } ?: 0})")
            }
            return false to reasons
        }

        var lecturerBusyCount = 0
        var lecturerOccupiedCount = 0
        var studentConflictCount = 0
        var classroomFullCount = 0
        val totalSlots: Int

        val daySlotPairs = mutableListOf<Pair<String, TimeSlot>>()
        for (day in activeDays) {
            val slots = generateTimeSlots(day, durationMin)
            for (slot in slots) {
                daySlotPairs.add(day to slot)
            }
        }
        totalSlots = daySlotPairs.size

        val scored = daySlotPairs.map { (day, slot) ->
            val s = scorePlacement(day, toMinutes(slot.startTime), toMinutes(slot.endTime), offering) +
                (random?.nextInt(8) ?: 0)
            Triple(day, slot, s)
        }.sortedBy { it.third }
        val sortedPairs = scored.map { it.first to it.second }

        for ((day, slot) in sortedPairs) {
            val startMin = toMinutes(slot.startTime)
            val endMin = toMinutes(slot.endTime)

            if (lecturerId != null) {
                val busy = busySlots[lecturerId] ?: emptyList()
                if (isLecturerBusy(lecturerId, day, startMin, endMin, busy)) {
                    lecturerBusyCount++
                    continue
                }
                if (isLecturerOccupied(lecturerId, day, startMin, endMin)) {
                    lecturerOccupiedCount++
                    continue
                }
            }

            if (hasStudentConflict(offering, day, startMin, endMin)) {
                studentConflictCount++
                continue
            }

            val classroom = candidateClassrooms.firstOrNull { cr ->
                !isClassroomOccupied(cr.id, day, startMin, endMin)
            }
            if (classroom == null) {
                classroomFullCount++
                continue
            }

            val entry = ProposedEntry(
                offering = offering,
                lecturerId = lecturerId,
                classroom = classroom,
                day = day,
                startTime = slot.startTime,
                endTime = slot.endTime
            )
            assigned.add(entry)
            usedSlots.add(
                OccupiedSlot(
                    day = day,
                    startMin = startMin,
                    endMin = endMin,
                    lecturerId = lecturerId,
                    classroomId = classroom.id,
                    classYear = offering.classYear,
                    section = offering.section,
                    departmentId = offering.courses?.departmentId
                )
            )
            return true to emptyList()
        }

        val reasons = mutableListOf<String>()
        val lecturerName = offering.lecturers?.fullName ?: "Hoca #$lecturerId"
        if (lecturerBusyCount > 0) {
            val busyDays = busySlots[lecturerId]?.map { it.day }?.distinct()?.joinToString(", ") ?: ""
            reasons.add("$lecturerName müsait değil ($lecturerBusyCount/$totalSlots slot meşgul saate denk geliyor — meşgul günler: $busyDays)")
        }
        if (lecturerOccupiedCount > 0) {
            reasons.add("$lecturerName zaten başka dersle dolu ($lecturerOccupiedCount/$totalSlots slot çakışıyor)")
        }
        if (studentConflictCount > 0) {
            reasons.add("${offering.classYear}. sınıf ${offering.section} şubesi öğrencileri başka derslerde ($studentConflictCount/$totalSlots slot öğrenci çakışması)")
        }
        if (classroomFullCount > 0) {
            reasons.add("Uygun derslikler dolu ($classroomFullCount/$totalSlots slot derslik çakışması — ${candidateClassrooms.size} uygun derslik var)")
        }
        if (reasons.isEmpty()) {
            reasons.add("Tüm zaman slotları ($totalSlots adet) tükenmiş — daha fazla gün/saat veya derslik gerekebilir")
        }

        return false to reasons
    }

    private fun getSlotDuration(offering: Offering): Int {
        val theory = offering.courses?.theoryHours ?: 0
        val lab = offering.courses?.labHours ?: 0
        val totalHours = theory + lab
        return if (totalHours > 0) totalHours * 60 else 60
    }

    private fun findSuitableClassrooms(offering: Offering): List<Classroom> {
        val needLab = (offering.courses?.labHours ?: 0) > 0
        return classrooms
            .filter { cr ->
                cr.capacity >= offering.capacity &&
                (if (needLab) cr.type == "lab" else true)
            }
            .sortedBy { it.capacity }
    }

    private fun generateTimeSlots(day: String, durationMin: Int): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        var start = dayStartMin
        while (start + durationMin <= dayEndMin) {
            slots.add(
                TimeSlot(
                    day = day,
                    startTime = formatTime(start),
                    endTime = formatTime(start + durationMin)
                )
            )
            start += step
        }
        return slots
    }

    private fun isLecturerBusy(
        lecturerId: Int, day: String, startMin: Int, endMin: Int,
        busy: List<LecturerAvailability>
    ): Boolean {
        return busy.any { slot ->
            slot.day == day &&
            toMinutes(slot.startTime) < endMin &&
            startMin < toMinutes(slot.endTime)
        }
    }

    private fun isLecturerOccupied(lecturerId: Int, day: String, startMin: Int, endMin: Int): Boolean {
        return usedSlots.any {
            it.lecturerId == lecturerId && it.day == day &&
            it.startMin < endMin && startMin < it.endMin
        }
    }

    private fun isClassroomOccupied(classroomId: Int, day: String, startMin: Int, endMin: Int): Boolean {
        return usedSlots.any {
            it.classroomId == classroomId && it.day == day &&
            it.startMin < endMin && startMin < it.endMin
        }
    }

    private fun hasStudentConflict(offering: Offering, day: String, startMin: Int, endMin: Int): Boolean {
        return usedSlots.any {
            it.classYear == offering.classYear &&
            it.section == offering.section &&
            it.departmentId == offering.courses?.departmentId &&
            it.day == day &&
            it.startMin < endMin && startMin < it.endMin
        }
    }

    private fun scorePlacement(day: String, startMin: Int, endMin: Int, offering: Offering): Int {
        var score = 0

        val studentSlots = usedSlots.filter {
            it.classYear == offering.classYear &&
            it.section == offering.section &&
            it.departmentId == offering.courses?.departmentId &&
            it.day == day
        }

        when (preferences.studentCompactness) {
            SchedulePreferences.CompactnessMode.COMPACT -> {
                if (studentSlots.isNotEmpty()) {
                    val gap = nearestGap(studentSlots, startMin, endMin)
                    when {
                        gap == 0              -> score -= 30
                        gap in 1..30          -> score -= 25
                        gap in 31..60         -> score -= 15
                        gap in 61..120        -> score += 10
                        gap != Int.MAX_VALUE  -> score += 40
                    }
                } else {
                    val studentDays = studentDaysForGroup(offering)
                    if (studentDays.isNotEmpty() && day !in studentDays) score += 15
                }
            }
            SchedulePreferences.CompactnessMode.SPREAD -> {
                if (studentSlots.isNotEmpty()) {
                    val gap = nearestGap(studentSlots, startMin, endMin)
                    when {
                        gap == 0              -> score += 20
                        gap in 1..30          -> score += 15
                        gap in 31..90         -> score -= 10
                        gap in 91..180        -> score -= 15
                        gap != Int.MAX_VALUE  -> score -= 5
                    }
                    score += studentSlots.size * 5
                } else {
                    val studentDays = studentDaysForGroup(offering)
                    if (studentDays.isNotEmpty() && day !in studentDays) score -= 20
                }
            }
            SchedulePreferences.CompactnessMode.NONE -> { }
        }

        val lecturerId = offering.lecturerId
        if (lecturerId != null) {
            val lecturerDaySlots = usedSlots.filter { it.lecturerId == lecturerId && it.day == day }
            if (lecturerDaySlots.isNotEmpty()) {
                val maxDaily = preferences.lecturerMaxDailySlots
                if (maxDaily > 0 && lecturerDaySlots.size >= maxDaily) {
                    score += 25
                }

                val lastEnd = lecturerDaySlots.maxOf { it.endMin }
                if (startMin > lastEnd) {
                    val gap = startMin - lastEnd
                    if (gap in 1..30) score -= 5
                    if (gap > 120) score += 10
                }
            }
        }

        if (preferences.dayBalancing) {
            val dayLoad = usedSlots.count { it.day == day }
            score += dayLoad * 3
        }

        if (prefStartMin > 0 && prefEndMin > 0) {
            if (startMin >= prefStartMin && endMin <= prefEndMin) {
                score -= 5
            } else if (startMin < prefStartMin || endMin > prefEndMin) {
                val outside = maxOf(0, prefStartMin - startMin) + maxOf(0, endMin - prefEndMin)
                score += (outside / 30).coerceAtMost(10)
            }
        }

        return score
    }

    private fun nearestGap(slots: List<OccupiedSlot>, startMin: Int, endMin: Int): Int {
        val lastEnd = slots.maxOf { it.endMin }
        val firstStart = slots.minOf { it.startMin }
        val gapAfter = if (startMin >= lastEnd) startMin - lastEnd else Int.MAX_VALUE
        val gapBefore = if (endMin <= firstStart) firstStart - endMin else Int.MAX_VALUE
        return minOf(gapAfter, gapBefore)
    }

    private fun studentDaysForGroup(offering: Offering): Set<String> =
        usedSlots
            .filter {
                it.classYear == offering.classYear &&
                it.section == offering.section &&
                it.departmentId == offering.courses?.departmentId
            }
            .map { it.day }.toSet()

    private fun calculateScore(): Int {
        var score = 100

        if (preferences.studentCompactness == SchedulePreferences.CompactnessMode.COMPACT) {
            val studentGroups = usedSlots
                .filter { it.classYear > 0 && it.departmentId != null }
                .groupBy { Triple(it.classYear, it.section, it.departmentId) }
            for ((_, slots) in studentGroups) {
                val byDay = slots.groupBy { it.day }
                for ((_, daySlots) in byDay) {
                    if (daySlots.size < 2) continue
                    val sorted = daySlots.sortedBy { it.startMin }
                    for (i in 0 until sorted.size - 1) {
                        val gap = sorted[i + 1].startMin - sorted[i].endMin
                        if (gap > 120) score -= 8
                        else if (gap > 60) score -= 3
                    }
                }
            }
        }

        val maxDaily = preferences.lecturerMaxDailySlots
        if (maxDaily > 0) {
            val lecturerDayLoads = usedSlots
                .filter { it.lecturerId != null }
                .groupBy { it.lecturerId!! to it.day }
            for ((_, slots) in lecturerDayLoads) {
                if (slots.size > maxDaily) score -= (slots.size - maxDaily) * 5
            }
        }

        if (preferences.dayBalancing) {
            val dayLoads = usedSlots.groupBy { it.day }
            if (dayLoads.isNotEmpty()) {
                val avgLoad = dayLoads.values.sumOf { it.size } / dayLoads.size.toFloat()
                for ((_, slots) in dayLoads) {
                    val deviation = kotlin.math.abs(slots.size - avgLoad)
                    if (deviation > 2) score -= (deviation * 3).toInt()
                }
            }
        }

        return score.coerceAtLeast(0)
    }

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }
}
