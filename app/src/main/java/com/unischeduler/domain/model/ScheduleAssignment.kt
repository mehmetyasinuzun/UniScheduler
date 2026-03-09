package com.unischeduler.domain.model

data class ScheduleAssignment(
    val id: Int = 0,
    val courseId: Int,
    val courseCode: String = "",
    val courseName: String = "",
    val lecturerId: Int,
    val lecturerName: String = "",
    val dayOfWeek: Int,
    val slotIndex: Int,
    val classroom: String = "",
    val semester: String = "",
    val isLocked: Boolean = false
)
