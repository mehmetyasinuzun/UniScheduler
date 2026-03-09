package com.unischeduler.domain.usecase.draft

import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.domain.repository.DraftRepository
import com.unischeduler.domain.repository.ScheduleRepository
import javax.inject.Inject

class ApproveDraftUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(
        draftId: Int,
        adminNote: String?,
        reviewedBy: String
    ): Result<Unit> = runCatching {
        val draft = draftRepository.getDraftById(draftId)
            ?: throw IllegalArgumentException("Draft bulunamadı")

        // Apply draft assignments to live schedule
        scheduleRepository.upsertAssignments(draft.assignments)

        // Update draft status
        draftRepository.updateDraftStatus(draftId, DraftStatus.APPROVED, adminNote, reviewedBy)
    }
}
