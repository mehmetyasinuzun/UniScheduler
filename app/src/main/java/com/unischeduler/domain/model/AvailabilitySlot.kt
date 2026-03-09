package com.unischeduler.domain.model

data class AvailabilitySlot(
    val id: Int = 0,
    val lecturerId: Int,
    val dayOfWeek: Int,
    val slotIndex: Int,
    val isAvailable: Boolean = true
)
