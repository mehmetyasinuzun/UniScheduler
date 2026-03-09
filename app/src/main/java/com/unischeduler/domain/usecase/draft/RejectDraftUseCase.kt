package com.unischeduler.domain.usecase.draft

import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.repository.DraftRepository
import javax.inject.Inject

class RejectDraftUseCase @Inject constructor(
    private val draftRepository: DraftRepository
) {
    suspend operator fun invoke(
        draftId: Int,
        adminNote: String,
        reviewedBy: String
    ): Result<Unit> = runCatching {
        draftRepository.updateDraftStatus(draftId, DraftStatus.REJECTED, adminNote, reviewedBy)
    }
}
