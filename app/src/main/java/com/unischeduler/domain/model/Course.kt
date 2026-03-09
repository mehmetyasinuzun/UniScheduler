package com.unischeduler.domain.model

data class Course(
    val id: Int = 0,
    val code: String,
    val name: String,
    val departmentId: Int,
    val credit: Int = 0,
    val colorHex: String = "#4285F4"
)
