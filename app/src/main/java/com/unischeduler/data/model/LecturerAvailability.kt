// Model: lecturer_availability table — stores time blocks when a lecturer is free.
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LecturerAvailability(
    val id: Int = 0,
    @SerialName("org_id")       val orgId: Int = 0,
    @SerialName("lecturer_id")  val lecturerId: Int = 0,
    val day: String = "",
    @SerialName("start_time")   val startTime: String = "",
    @SerialName("end_time")     val endTime: String = "",
) {
    val timeRange: String get() = "$startTime - $endTime"
}
