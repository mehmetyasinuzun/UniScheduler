package com.unischeduler.domain.algorithm

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.TimeSlot

/**
 * MRV (Minimum Remaining Values) Heuristic.
 * Orders variables (courses) by how many valid slots remain in their domain.
 * The most constrained course is scheduled first.
 */
class MRVHeuristic {

    data class Variable(
        val course: Course,
        val lecturerId: Int,
        val domain: MutableList<TimeSlot>
    )

    fun createVariables(
        courses: List<Course>,
        courseLecturerMap: Map<Int, Int>,
        config: ScheduleConfig,
        availability: Map<Int, List<AvailabilitySlot>>
    ): List<Variable> {
        val allSlots = buildAllSlots(config)

        return courses.mapNotNull { course ->
            val lecturerId = courseLecturerMap[course.id] ?: return@mapNotNull null
            val lecturerAvail = availability[lecturerId] ?: emptyList()

            // Initial domain: all slots where lecturer is available
            val domain = allSlots.filter { slot ->
                val avail = lecturerAvail.find {
                    it.dayOfWeek == slot.dayOfWeek && it.slotIndex == slot.slotIndex
                }
                avail == null || avail.isAvailable  // null = no data = assume available
            }.toMutableList()

            Variable(course = course, lecturerId = lecturerId, domain = domain)
        }
    }

    fun orderByMRV(variables: List<Variable>): List<Variable> {
        return variables.sortedBy { it.domain.size }
    }

    private fun buildAllSlots(config: ScheduleConfig): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        for (day in config.activeDays) {
            for (slot in 0 until config.totalSlotsPerDay) {
                slots.add(
                    TimeSlot(
                        dayOfWeek = day,
                        slotIndex = slot,
                        startTime = config.slotStartTime(slot),
                        endTime = config.slotEndTime(slot)
                    )
                )
            }
        }
        return slots
    }
}
