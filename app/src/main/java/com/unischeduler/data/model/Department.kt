// Model: departments table
package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Department(
    val id: Int = 0,
    @SerialName("org_id") val orgId: Int = 0,
    val name: String = ""
)
