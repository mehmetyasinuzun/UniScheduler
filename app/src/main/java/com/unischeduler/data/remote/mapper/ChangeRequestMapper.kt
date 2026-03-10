package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.ChangeRequestDto
import com.unischeduler.domain.model.ApprovalMode
import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.model.RequestStatus

object ChangeRequestMapper {
    fun toDomain(dto: ChangeRequestDto): ChangeRequest = ChangeRequest(
        id = dto.id,
        lecturerId = dto.lecturerId,
        requestType = dto.requestType,
        currentData = dto.currentData,
        requestedData = dto.requestedData,
        reason = dto.reason,
        status = runCatching { RequestStatus.valueOf(dto.status) }.getOrDefault(RequestStatus.PENDING),
        approvalMode = runCatching { ApprovalMode.valueOf(dto.approvalMode ?: "DUAL_APPROVAL") }.getOrDefault(ApprovalMode.DUAL_APPROVAL),
        deptHeadStatus = runCatching { RequestStatus.valueOf(dto.deptHeadStatus ?: "PENDING") }.getOrDefault(RequestStatus.PENDING),
        deptHeadReviewedBy = dto.deptHeadReviewedBy,
        deptHeadNote = dto.deptHeadNote,
        deptHeadReviewedAt = dto.deptHeadReviewedAt,
        adminStatus = runCatching { RequestStatus.valueOf(dto.adminStatus ?: "PENDING") }.getOrDefault(RequestStatus.PENDING),
        adminReviewedBy = dto.adminReviewedBy,
        adminNote = dto.adminNote,
        adminReviewedAt = dto.adminReviewedAt,
        createdAt = dto.createdAt ?: ""
    )

    fun toDto(domain: ChangeRequest): ChangeRequestDto = ChangeRequestDto(
        id = domain.id,
        lecturerId = domain.lecturerId,
        requestType = domain.requestType,
        currentData = domain.currentData,
        requestedData = domain.requestedData,
        reason = domain.reason,
        status = domain.status.name,
        approvalMode = domain.approvalMode.name,
        deptHeadStatus = domain.deptHeadStatus.name,
        deptHeadReviewedBy = domain.deptHeadReviewedBy,
        deptHeadNote = domain.deptHeadNote,
        deptHeadReviewedAt = domain.deptHeadReviewedAt,
        adminStatus = domain.adminStatus.name,
        adminReviewedBy = domain.adminReviewedBy,
        adminNote = domain.adminNote,
        adminReviewedAt = domain.adminReviewedAt,
        createdAt = domain.createdAt
    )
}
