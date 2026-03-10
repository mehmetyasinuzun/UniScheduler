package com.unischeduler.domain.model

data class Lecturer(
    val id: Int = 0,
    val profileId: String? = null,
    val fullName: String,
    val title: String = "",
    val departmentId: Int,
    val courses: List<Course> = emptyList(),
    val username: String = "",
    val password: String = "",
    val inviteCode: String = ""
)
