package com.unischeduler.domain.usecase.request

import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.repository.RequestRepository
import javax.inject.Inject

class CreateRequestUseCase @Inject constructor(
    private val requestRepository: RequestRepository
) {
    suspend operator fun invoke(request: ChangeRequest): Result<ChangeRequest> {
        return runCatching { requestRepository.createRequest(request) }
    }
}
