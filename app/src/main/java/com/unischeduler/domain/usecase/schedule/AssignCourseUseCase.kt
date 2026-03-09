package com.unischeduler.domain.usecase.schedule

import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.repository.ScheduleRepository
import javax.inject.Inject

class AssignCourseUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(assignment: ScheduleAssignment): Result<ScheduleAssignment> {
        return runCatching { scheduleRepository.upsertAssignment(assignment) }
    }
}
