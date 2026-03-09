package com.unischeduler.domain.repository

import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.model.RequestStatus

interface RequestRepository {
    suspend fun createRequest(request: ChangeRequest): ChangeRequest
    suspend fun getRequestsByLecturer(lecturerId: Int): List<ChangeRequest>
    suspend fun getPendingRequestsByDepartment(departmentId: Int): List<ChangeRequest>
    suspend fun getAllPendingRequests(): List<ChangeRequest>
    suspend fun updateDeptHeadReview(requestId: Int, status: RequestStatus, note: String?, reviewedBy: String)
    suspend fun updateAdminReview(requestId: Int, status: RequestStatus, note: String?, reviewedBy: String)
    suspend fun getRequestById(id: Int): ChangeRequest?
}
