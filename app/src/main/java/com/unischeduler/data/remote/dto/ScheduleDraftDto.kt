package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDraftDto(
    val id: Int = 0,
    @SerialName("department_id") val departmentId: Int = 0,
    @SerialName("created_by") val createdBy: String = "",
    val title: String = "",
    val assignments: String = "[]",
    @SerialName("soft_score") val softScore: Float = 0f,
    val status: String = "DRAFT",   // FIX: DB CHECK constraint expects UPPERCASE
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("reviewed_at") val reviewedAt: String? = null
)
