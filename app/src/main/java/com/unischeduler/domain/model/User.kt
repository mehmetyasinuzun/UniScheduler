package com.unischeduler.domain.model

data class User(
    val id: String,
    val name: String,
    val surname: String,
    val role: UserRole,
    val departmentId: Int? = null,
    val email: String? = null
)
