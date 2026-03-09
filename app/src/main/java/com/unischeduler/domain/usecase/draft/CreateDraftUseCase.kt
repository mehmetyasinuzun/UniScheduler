package com.unischeduler.domain.usecase.draft

import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.domain.repository.DraftRepository
import javax.inject.Inject

class CreateDraftUseCase @Inject constructor(
    private val draftRepository: DraftRepository
) {
    suspend operator fun invoke(
        departmentId: Int,
        createdBy: String,
        title: String,
        assignments: List<ScheduleAssignment>,
        softScore: Float
    ): Result<ScheduleDraft> = runCatching {
        draftRepository.createDraft(
            ScheduleDraft(
                departmentId = departmentId,
                createdBy = createdBy,
                title = title,
                assignments = assignments,
                softScore = softScore
            )
        )
    }
}
