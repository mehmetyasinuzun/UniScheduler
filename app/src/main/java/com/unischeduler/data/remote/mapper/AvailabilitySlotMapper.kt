package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.AvailabilitySlotDto
import com.unischeduler.domain.model.AvailabilitySlot

object AvailabilitySlotMapper {
    fun toDomain(dto: AvailabilitySlotDto): AvailabilitySlot = AvailabilitySlot(
        id = dto.id ?: 0,
        lecturerId = dto.lecturerId,
        dayOfWeek = dto.dayOfWeek,
        slotIndex = dto.slotIndex,
        isAvailable = dto.isAvailable
    )

    fun toDto(domain: AvailabilitySlot): AvailabilitySlotDto = AvailabilitySlotDto(
        id = domain.id.takeIf { it > 0 },
        lecturerId = domain.lecturerId,
        dayOfWeek = domain.dayOfWeek,
        slotIndex = domain.slotIndex,
        isAvailable = domain.isAvailable
    )
}
