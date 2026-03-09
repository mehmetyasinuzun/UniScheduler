package com.unischeduler.domain.algorithm

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleAssignment

/**
 * Hard constraint validator.
 * Returns true if the assignment does NOT violate any hard constraint.
 */
class ConstraintChecker {

    fun isFeasible(
        assignment: ScheduleAssignment,
        currentAssignments: List<ScheduleAssignment>,
        availability: Map<Int, List<AvailabilitySlot>>
    ): Boolean {
        // HC1: Same lecturer cannot teach two courses at the same time
        val lecturerClash = currentAssignments.any {
            it.lecturerId == assignment.lecturerId &&
                it.dayOfWeek == assignment.dayOfWeek &&
                it.slotIndex == assignment.slotIndex
        }
        if (lecturerClash) return false

        // HC2: Same classroom cannot host two courses at the same time
        if (assignment.classroom.isNotBlank()) {
            val classroomClash = currentAssignments.any {
                it.classroom == assignment.classroom &&
                    it.dayOfWeek == assignment.dayOfWeek &&
                    it.slotIndex == assignment.slotIndex
            }
            if (classroomClash) return false
        }

        // HC3: Lecturer must be available (hard constraint)
        val lecturerAvailability = availability[assignment.lecturerId] ?: emptyList()
        val slot = lecturerAvailability.find {
            it.dayOfWeek == assignment.dayOfWeek && it.slotIndex == assignment.slotIndex
        }
        if (slot != null && !slot.isAvailable) return false

        return true
    }

    fun checkCrossDepartmentConflict(
        assignment: ScheduleAssignment,
        otherDeptAssignments: List<ScheduleAssignment>
    ): Boolean {
        // HC5: Shared lecturer cannot teach in different departments at the same time
        return otherDeptAssignments.none {
            it.lecturerId == assignment.lecturerId &&
                it.dayOfWeek == assignment.dayOfWeek &&
                it.slotIndex == assignment.slotIndex
        }
    }
}
