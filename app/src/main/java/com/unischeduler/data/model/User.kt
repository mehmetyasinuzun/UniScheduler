// Model: users table — auth record for admin and lecturers
// role: "admin" | "lecturer"
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    @SerialName("org_id")               val orgId: Int = 0,
    val username: String = "",
    val role: String = "",
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
    @SerialName("created_at")           val createdAt: String = ""
)
