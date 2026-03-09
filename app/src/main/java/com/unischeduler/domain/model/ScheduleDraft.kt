package com.unischeduler.domain.model

data class ScheduleDraft(
    val id: Int = 0,
    val departmentId: Int,
    val createdBy: String,
    val title: String,
    val assignments: List<ScheduleAssignment> = emptyList(),
    val softScore: Float = 0f,
    val status: DraftStatus = DraftStatus.DRAFT,
    val adminNote: String? = null,
    val reviewedBy: String? = null,
    val createdAt: String = "",
    val reviewedAt: String? = null
)
