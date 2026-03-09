package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String = "",
    val name: String = "",
    val surname: String = "",
    val role: String = "student",
    @SerialName("department_id") val departmentId: Int? = null,
    @SerialName("created_at") val createdAt: String = ""
)
