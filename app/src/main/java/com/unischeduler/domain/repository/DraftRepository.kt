package com.unischeduler.domain.repository

import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.model.ScheduleDraft

interface DraftRepository {
    suspend fun getDraftsByDepartment(departmentId: Int): List<ScheduleDraft>
    suspend fun getDraftById(id: Int): ScheduleDraft?
    suspend fun getPendingDrafts(): List<ScheduleDraft>
    suspend fun createDraft(draft: ScheduleDraft): ScheduleDraft
    suspend fun updateDraftStatus(draftId: Int, status: DraftStatus, adminNote: String?, reviewedBy: String?)
    suspend fun deleteDraft(id: Int)
}
