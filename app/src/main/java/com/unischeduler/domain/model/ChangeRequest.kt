package com.unischeduler.domain.model

data class ChangeRequest(
    val id: Int = 0,
    val lecturerId: Int,
    val requestType: String,
    val currentData: String,
    val requestedData: String,
    val reason: String,
    val status: RequestStatus = RequestStatus.PENDING,
    val approvalMode: ApprovalMode = ApprovalMode.DUAL_APPROVAL,
    val deptHeadStatus: RequestStatus = RequestStatus.PENDING,
    val deptHeadReviewedBy: String? = null,
    val deptHeadNote: String? = null,
    val deptHeadReviewedAt: String? = null,
    val adminStatus: RequestStatus = RequestStatus.PENDING,
    val adminReviewedBy: String? = null,
    val adminNote: String? = null,
    val adminReviewedAt: String? = null,
    val createdAt: String = ""
)
