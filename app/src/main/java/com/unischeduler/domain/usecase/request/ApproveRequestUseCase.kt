package com.unischeduler.domain.usecase.request

import com.unischeduler.domain.model.RequestStatus
import com.unischeduler.domain.repository.RequestRepository
import javax.inject.Inject

class ApproveRequestUseCase @Inject constructor(
    private val requestRepository: RequestRepository
) {
    suspend fun approveAsAdmin(requestId: Int, note: String?, reviewedBy: String): Result<Unit> {
        return runCatching {
            requestRepository.updateAdminReview(requestId, RequestStatus.APPROVED, note, reviewedBy)
        }
    }

    suspend fun approveAsDeptHead(requestId: Int, note: String?, reviewedBy: String): Result<Unit> {
        return runCatching {
            requestRepository.updateDeptHeadReview(requestId, RequestStatus.APPROVED, note, reviewedBy)
        }
    }
}
