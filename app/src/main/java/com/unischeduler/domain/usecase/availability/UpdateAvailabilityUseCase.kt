package com.unischeduler.domain.usecase.availability

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.repository.ScheduleRepository
import javax.inject.Inject

class UpdateAvailabilityUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(slots: List<AvailabilitySlot>): Result<Unit> {
        return runCatching { scheduleRepository.upsertAvailability(slots) }
    }

    suspend fun getAvailability(lecturerId: Int): List<AvailabilitySlot> {
        return scheduleRepository.getAvailability(lecturerId)
    }
}
