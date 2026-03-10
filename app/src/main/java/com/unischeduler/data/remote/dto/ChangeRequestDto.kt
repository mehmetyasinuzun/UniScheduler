package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangeRequestDto(
    val id: Int = 0,
    @SerialName("lecturer_id") val lecturerId: Int = 0,
    @SerialName("request_type") val requestType: String = "",
    @SerialName("current_data") val currentData: String = "",
    @SerialName("requested_data") val requestedData: String = "",
    val reason: String = "",
    val status: String = "PENDING",
    @SerialName("approval_mode") val approvalMode: String = "DUAL_APPROVAL",
    @SerialName("dept_head_status") val deptHeadStatus: String = "PENDING",
    @SerialName("dept_head_reviewed_by") val deptHeadReviewedBy: String? = null,
    @SerialName("dept_head_note") val deptHeadNote: String? = null,
    @SerialName("dept_head_reviewed_at") val deptHeadReviewedAt: String? = null,
    @SerialName("admin_status") val adminStatus: String = "PENDING",
    @SerialName("admin_reviewed_by") val adminReviewedBy: String? = null,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("admin_reviewed_at") val adminReviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
