package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.AvailabilitySlotDto
import com.unischeduler.domain.model.AvailabilitySlot

object AvailabilitySlotMapper {
    fun toDomain(dto: AvailabilitySlotDto): AvailabilitySlot = AvailabilitySlot(
        id = dto.id,
        lecturerId = dto.lecturerId,
        dayOfWeek = dto.dayOfWeek,
        slotIndex = dto.slotIndex,
        isAvailable = dto.isAvailable ?: true
    )

    fun toDto(domain: AvailabilitySlot): AvailabilitySlotDto = AvailabilitySlotDto(
        id = domain.id,
        lecturerId = domain.lecturerId,
        dayOfWeek = domain.dayOfWeek,
        slotIndex = domain.slotIndex,
        isAvailable = domain.isAvailable
    )
}
