// Model: courses table
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: Int = 0,
    @SerialName("org_id")        val orgId: Int = 0,
    val code: String = "",
    val name: String = "",
    @SerialName("theory_hours")  val theoryHours: Int = 0,
    @SerialName("lab_hours")     val labHours: Int = 0,
    @SerialName("credits")       val credits: Int = 0,
    @SerialName("department_id") val departmentId: Int? = null,
    val departments: Department? = null
) {
    val departmentName: String get() = departments?.name ?: ""
    val displayName: String get() = "$code — $name"
}
