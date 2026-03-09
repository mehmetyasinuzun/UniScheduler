package com.unischeduler.domain.model

enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class ApprovalMode {
    DUAL_APPROVAL,
    ADMIN_ONLY,
    DEPT_HEAD_ONLY
}
