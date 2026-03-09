package com.unischeduler.domain.model

data class ScheduleSolution(
    val assignments: List<ScheduleAssignment>,
    val softScore: Float,
    val totalAssigned: Int,
    val totalUnassigned: Int,
    val conflictCount: Int
)
