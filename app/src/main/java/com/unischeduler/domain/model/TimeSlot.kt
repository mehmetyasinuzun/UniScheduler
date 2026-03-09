package com.unischeduler.domain.model

data class TimeSlot(
    val dayOfWeek: Int,
    val slotIndex: Int,
    val startTime: String = "",
    val endTime: String = ""
)
