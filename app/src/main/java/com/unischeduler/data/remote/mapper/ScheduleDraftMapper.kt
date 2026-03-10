package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.ScheduleDraftDto
import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.model.ScheduleDraft

object ScheduleDraftMapper {
    fun toDomain(dto: ScheduleDraftDto): ScheduleDraft = ScheduleDraft(
        id = dto.id,
        departmentId = dto.departmentId,
        createdBy = dto.createdBy,
        title = dto.title,
        assignments = emptyList(), // assignments ayrıca yüklenir
        softScore = dto.softScore ?: 0f,
        status = runCatching { DraftStatus.valueOf(dto.status) }.getOrDefault(DraftStatus.DRAFT),
        adminNote = dto.adminNote,
        reviewedBy = dto.reviewedBy,
        createdAt = dto.createdAt ?: "",
        reviewedAt = dto.reviewedAt
    )

    fun toDto(domain: ScheduleDraft): ScheduleDraftDto = ScheduleDraftDto(
        id = domain.id,
        departmentId = domain.departmentId,
        createdBy = domain.createdBy,
        title = domain.title,
        softScore = domain.softScore,
        status = domain.status.name,
        adminNote = domain.adminNote,
        reviewedBy = domain.reviewedBy,
        createdAt = domain.createdAt,
        reviewedAt = domain.reviewedAt
    )
}
