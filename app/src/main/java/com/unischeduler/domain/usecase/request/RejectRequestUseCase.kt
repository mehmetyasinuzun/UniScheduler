package com.unischeduler.domain.usecase.request

import com.unischeduler.domain.model.RequestStatus
import com.unischeduler.domain.repository.RequestRepository
import javax.inject.Inject

class RejectRequestUseCase @Inject constructor(
    private val requestRepository: RequestRepository
) {
    suspend fun rejectAsAdmin(requestId: Int, note: String, reviewedBy: String): Result<Unit> {
        return runCatching {
            requestRepository.updateAdminReview(requestId, RequestStatus.REJECTED, note, reviewedBy)
        }
    }

    suspend fun rejectAsDeptHead(requestId: Int, note: String, reviewedBy: String): Result<Unit> {
        return runCatching {
            requestRepository.updateDeptHeadReview(requestId, RequestStatus.REJECTED, note, reviewedBy)
        }
    }
}
