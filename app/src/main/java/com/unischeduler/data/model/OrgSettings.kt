// Model: org_settings table
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrgSettings(
    @SerialName("org_id") val orgId: Int = 0,
    @SerialName("time_step_minutes") val timeStepMinutes: Int = 10,
    @SerialName("active_days") val activeDays: List<String> = listOf(),
    @SerialName("day_start") val dayStart: String = "08:00",
    @SerialName("day_end") val dayEnd: String = "18:00"
)
