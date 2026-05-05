// Model: classrooms table
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Classroom(
    val id: Int = 0,
    @SerialName("org_id")        val orgId: Int = 0,
    @SerialName("room_code")     val roomCode: String = "",
    val capacity: Int = 0,
    val type: String = "theory",
    @SerialName("department_id") val departmentId: Int? = null,
    val departments: Department? = null
) {
    val departmentName: String get() = departments?.name ?: ""
}
