package com.unischeduler.domain.usecase.schedule

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleAssignment
import javax.inject.Inject

enum class ConflictType {
    LECTURER_CLASH,
    CLASSROOM_CLASH,
    AVAILABILITY_HARD,
    AVAILABILITY_SOFT,
    CROSS_DEPARTMENT
}

data class Conflict(
    val type: ConflictType,
    val message: String,
    val assignment1: ScheduleAssignment? = null,
    val assignment2: ScheduleAssignment? = null
)

class CheckConflictUseCase @Inject constructor() {

    operator fun invoke(
        assignment: ScheduleAssignment,
        existingAssignments: List<ScheduleAssignment>,
        availability: List<AvailabilitySlot>
    ): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        // Lecturer clash: same lecturer, same day, same slot
        existingAssignments.filter {
            it.id != assignment.id &&
                it.lecturerId == assignment.lecturerId &&
                it.dayOfWeek == assignment.dayOfWeek &&
                it.slotIndex == assignment.slotIndex
        }.forEach { clash ->
            conflicts.add(
                Conflict(
                    ConflictType.LECTURER_CLASH,
                    "Bu hoca aynı saatte başka bir ders veriyor",
                    assignment, clash
                )
            )
        }

        // Classroom clash: same classroom, same day, same slot
        if (assignment.classroom.isNotBlank()) {
            existingAssignments.filter {
                it.id != assignment.id &&
                    it.classroom == assignment.classroom &&
                    it.dayOfWeek == assignment.dayOfWeek &&
                    it.slotIndex == assignment.slotIndex
            }.forEach { clash ->
                conflicts.add(
                    Conflict(
                        ConflictType.CLASSROOM_CLASH,
                        "Bu sınıf aynı saatte kullanılıyor",
                        assignment, clash
                    )
                )
            }
        }

        // Availability check
        val slot = availability.find {
            it.lecturerId == assignment.lecturerId &&
                it.dayOfWeek == assignment.dayOfWeek &&
                it.slotIndex == assignment.slotIndex
        }
        if (slot != null && !slot.isAvailable) {
            conflicts.add(
                Conflict(
                    ConflictType.AVAILABILITY_HARD,
                    "Hoca bu saatte müsait değil"
                )
            )
        }

        return conflicts
    }
}
