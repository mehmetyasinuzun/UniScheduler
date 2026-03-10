package com.unischeduler.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: Int,
    val name: String,
    val code: String
)
