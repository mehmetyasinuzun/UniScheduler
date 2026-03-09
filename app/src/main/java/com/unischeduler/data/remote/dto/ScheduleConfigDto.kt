package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleConfigDto(
    val id: Int = 0,
    @SerialName("department_id") val departmentId: Int = 0,
    @SerialName("slot_duration_minutes") val slotDurationMinutes: Int = 60,
    @SerialName("day_start_time") val dayStartTime: String = "08:00",
    @SerialName("day_end_time") val dayEndTime: String = "17:00",
    @SerialName("active_days") val activeDays: List<Int> = listOf(1, 2, 3, 4, 5)
)

@Serializable
data class DepartmentDto(
    val id: Int = 0,
    val name: String = "",
    val code: String = "",
    @SerialName("dept_head_permission") val deptHeadPermission: String = "approval_required"
)

@Serializable
data class ImportLogDto(
    val id: Int = 0,
    @SerialName("admin_id") val adminId: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("row_count") val rowCount: Int = 0,
    val status: String = "",
    @SerialName("created_at") val createdAt: String = ""
)
