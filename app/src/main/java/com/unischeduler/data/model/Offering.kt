// Model: offerings table (opened sections) with joined course data
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Offering(
    val id: Int = 0,
    @SerialName("org_id")        val orgId: Int = 0,
    @SerialName("course_id")     val courseId: Int = 0,
    @SerialName("lecturer_id")   val lecturerId: Int? = null,
    @SerialName("academic_year") val academicYear: String = "",
    val term: String = "",
    @SerialName("class_year")    val classYear: Int = 1,
    val section: String = "",
    val capacity: Int = 0,
    val courses: Course? = null,
    val lecturers: Lecturer? = null
) {
    val courseName: String get() = courses?.let { "${it.code} — ${it.name}" } ?: "Unknown"
    val lecturerName: String get() = lecturers?.fullName ?: "Atanmadı"
    val displayName: String get() {
        val code = courses?.code ?: ""
        val name = courses?.name ?: ""
        val title = if (code.isNotBlank()) "$code — $name" else name
        return "$title ($term $academicYear) ${classYear}${section}"
    }
}
