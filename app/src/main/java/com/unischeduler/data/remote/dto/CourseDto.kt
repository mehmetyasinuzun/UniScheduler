package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourseDto(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    @SerialName("department_id") val departmentId: Int = 0,
    val credit: Int = 0,
    @SerialName("color_hex") val colorHex: String = "#4285F4"
)
