package com.unischeduler.domain.usecase.schedule

import com.unischeduler.domain.algorithm.CSPSolver
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.ScheduleSolution
import javax.inject.Inject

class GenerateScheduleUseCase @Inject constructor(
    private val cspSolver: CSPSolver
) {
    operator fun invoke(
        courses: List<Course>,
        lecturers: List<Lecturer>,
        courseLecturerMap: Map<Int, Int>,
        availability: Map<Int, List<AvailabilitySlot>>,
        config: ScheduleConfig,
        lockedAssignments: List<ScheduleAssignment>,
        existingCrossDeptAssignments: List<ScheduleAssignment> = emptyList(),
        alternativeCount: Int = 3
    ): List<ScheduleSolution> {
        return cspSolver.solve(
            courses = courses,
            lecturers = lecturers,
            courseLecturerMap = courseLecturerMap,
            availability = availability,
            config = config,
            lockedAssignments = lockedAssignments,
            existingCrossDeptAssignments = existingCrossDeptAssignments,
            alternativeCount = alternativeCount
        )
    }
}
