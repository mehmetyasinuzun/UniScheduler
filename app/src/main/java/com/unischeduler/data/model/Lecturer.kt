// Model: lecturers table joined with users and departments
// departmentName and username are populated via joined queries
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Lecturer(
    val id: Int = 0,
    @SerialName("org_id")        val orgId: Int = 0,
    @SerialName("user_id")       val userId: String = "",
    val title: String = "",
    @SerialName("first_name")    val firstName: String = "",
    @SerialName("last_name")     val lastName: String = "",
    val email: String? = null,
    @SerialName("department_id") val departmentId: Int? = null,
    // Populated by joining departments table
    val departments: Department? = null,
    // Populated by joining users table
    val users: User? = null
) {
    val fullName: String get() = "$title $firstName $lastName"
    val displayName: String get() = "$firstName $lastName"
    val departmentName: String get() = departments?.name ?: ""
    val username: String get() = users?.username ?: ""
    /** true → henüz ilk girişini yapmamış / şifre sıfırlanmış (Geçici durum). */
    val mustChangePassword: Boolean get() = users?.mustChangePassword ?: false
}
