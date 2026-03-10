package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AvailabilitySlotDto(
    val id: Int? = null,
    @SerialName("lecturer_id") val lecturerId: Int = 0,
    @SerialName("day_of_week") val dayOfWeek: Int = 0,
    @SerialName("slot_index") val slotIndex: Int = 0,
    @SerialName("is_available") val isAvailable: Boolean = true
)
