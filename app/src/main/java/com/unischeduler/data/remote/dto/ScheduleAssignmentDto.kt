package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleAssignmentDto(
    val id: Int = 0,
    @SerialName("course_id") val courseId: Int = 0,
    @SerialName("course_code") val courseCode: String = "",
    @SerialName("course_name") val courseName: String = "",
    @SerialName("lecturer_id") val lecturerId: Int = 0,
    @SerialName("lecturer_name") val lecturerName: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 0,
    @SerialName("slot_index") val slotIndex: Int = 0,
    val classroom: String = "",
    val semester: String = "",
    @SerialName("is_locked") val isLocked: Boolean = false
)
