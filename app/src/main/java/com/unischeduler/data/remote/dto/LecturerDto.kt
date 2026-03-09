package com.unischeduler.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LecturerDto(
    val id: Int = 0,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("full_name") val fullName: String = "",
    val title: String = "",
    @SerialName("department_id") val departmentId: Int = 0
)

@Serializable
data class CourseLecturerDto(
    @SerialName("course_id") val courseId: Int,
    @SerialName("lecturer_id") val lecturerId: Int
)
