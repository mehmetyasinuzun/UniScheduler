package com.unischeduler.domain.algorithm

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleAssignment

/**
 * Soft constraint scorer.
 * Higher score = better schedule. Range: 0.0 to 100.0
 */
class SoftScorer {

    fun score(
        assignments: List<ScheduleAssignment>,
        availability: Map<Int, List<AvailabilitySlot>>
    ): Float {
        if (assignments.isEmpty()) return 0f

        var totalScore = 0f
        var maxPossible = 0f

        // SC1: Avoid lecturer's soft-preference slots (weight: 30)
        val preferenceScore = scorePreferences(assignments, availability)
        totalScore += preferenceScore * 30f
        maxPossible += 30f

        // SC2: Balanced distribution across days per lecturer (weight: 25)
        totalScore += scoreBalance(assignments) * 25f
        maxPossible += 25f

        // SC3: Minimize gaps between consecutive classes per lecturer (weight: 25)
        totalScore += scoreGapMinimization(assignments) * 25f
        maxPossible += 25f

        // SC4: Limit consecutive courses per day per lecturer (weight: 20)
        totalScore += scoreConsecutiveLimit(assignments) * 20f
        maxPossible += 20f

        return if (maxPossible > 0) (totalScore / maxPossible) * 100f else 0f
    }

    private fun scorePreferences(
        assignments: List<ScheduleAssignment>,
        availability: Map<Int, List<AvailabilitySlot>>
    ): Float {
        if (assignments.isEmpty()) return 1f
        var total = 0
        var preferred = 0
        for (a in assignments) {
            total++
            val slots = availability[a.lecturerId] ?: continue
            val slot = slots.find { it.dayOfWeek == a.dayOfWeek && it.slotIndex == a.slotIndex }
            if (slot == null || slot.isAvailable) preferred++
        }
        return if (total > 0) preferred.toFloat() / total else 1f
    }

    private fun scoreBalance(assignments: List<ScheduleAssignment>): Float {
        val byLecturer = assignments.groupBy { it.lecturerId }
        if (byLecturer.isEmpty()) return 1f

        var totalScore = 0f
        for ((_, lecturerAssignments) in byLecturer) {
            val dayCount = lecturerAssignments.groupBy { it.dayOfWeek }.mapValues { it.value.size }
            if (dayCount.isEmpty()) continue
            val avg = lecturerAssignments.size.toFloat() / dayCount.size
            val variance = dayCount.values.map { (it - avg) * (it - avg) }.average().toFloat()
            // Lower variance = better balance = higher score
            totalScore += 1f / (1f + variance)
        }
        return totalScore / byLecturer.size
    }

    private fun scoreGapMinimization(assignments: List<ScheduleAssignment>): Float {
        val byLecturerDay = assignments.groupBy { Pair(it.lecturerId, it.dayOfWeek) }
        if (byLecturerDay.isEmpty()) return 1f

        var totalGaps = 0
        var totalPairs = 0

        for ((_, dayAssignments) in byLecturerDay) {
            if (dayAssignments.size < 2) continue
            val sorted = dayAssignments.sortedBy { it.slotIndex }
            for (i in 1 until sorted.size) {
                val gap = sorted[i].slotIndex - sorted[i - 1].slotIndex - 1
                totalGaps += gap
                totalPairs++
            }
        }

        return if (totalPairs > 0) 1f / (1f + totalGaps.toFloat() / totalPairs) else 1f
    }

    private fun scoreConsecutiveLimit(assignments: List<ScheduleAssignment>): Float {
        val maxConsecutive = 4
        val byLecturerDay = assignments.groupBy { Pair(it.lecturerId, it.dayOfWeek) }
        if (byLecturerDay.isEmpty()) return 1f

        var penalties = 0
        for ((_, dayAssignments) in byLecturerDay) {
            val sorted = dayAssignments.sortedBy { it.slotIndex }
            var consecutive = 1
            for (i in 1 until sorted.size) {
                if (sorted[i].slotIndex == sorted[i - 1].slotIndex + 1) {
                    consecutive++
                    if (consecutive > maxConsecutive) penalties++
                } else {
                    consecutive = 1
                }
            }
        }

        return 1f / (1f + penalties)
    }
}
