// Model: schedule_entries table with joined offering/lecturer/classroom data
// day: Monday|Tuesday|Wednesday|Thursday|Friday
// startTime / endTime: "HH:MM" free-form time (no fixed slots)
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleEntry(
    val id: Int = 0,
    @SerialName("org_id")       val orgId: Int = 0,
    @SerialName("offering_id")  val offeringId: Int = 0,
    @SerialName("lecturer_id")  val lecturerId: Int? = null,
    @SerialName("classroom_id") val classroomId: Int = 0,
    val day: String = "",
    @SerialName("start_time")   val startTime: String = "",
    @SerialName("end_time")     val endTime: String = "",
    val offerings: Offering? = null,
    val lecturers: Lecturer? = null,
    val classrooms: Classroom? = null
) {
    val courseName: String     get() = offerings?.courses?.name ?: ""
    val courseCode: String     get() = offerings?.courses?.code ?: ""
    val lecturerName: String   get() = lecturers?.fullName ?: "Atanmadı"
    val classroomCode: String  get() = classrooms?.roomCode ?: ""
    val timeRange: String      get() = if (startTime.isNotBlank() && endTime.isNotBlank()) "$startTime-$endTime" else ""
}

val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
