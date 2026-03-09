package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.ChangeRequestDto
import com.unischeduler.domain.model.ApprovalMode
import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.model.RequestStatus
import com.unischeduler.domain.repository.RequestRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

class RequestRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : RequestRepository {

    override suspend fun createRequest(request: ChangeRequest): ChangeRequest {
        val dto = request.toDto()
        val result = supabase.postgrest.from("change_requests")
            .insert(dto) { select() }
            .decodeSingle<ChangeRequestDto>()
        return result.toDomain()
    }

    override suspend fun getRequestsByLecturer(lecturerId: Int): List<ChangeRequest> {
        return supabase.postgrest.from("change_requests")
            .select { filter { eq("lecturer_id", lecturerId) } }
            .decodeList<ChangeRequestDto>()
            .map { it.toDomain() }
    }

    override suspend fun getPendingRequestsByDepartment(departmentId: Int): List<ChangeRequest> {
        val lecturerIds = supabase.postgrest.from("lecturers")
            .select { filter { eq("department_id", departmentId) } }
            .decodeList<com.unischeduler.data.remote.dto.LecturerDto>()
            .map { it.id }

        if (lecturerIds.isEmpty()) return emptyList()

        return supabase.postgrest.from("change_requests")
            .select {
                filter {
                    isIn("lecturer_id", lecturerIds)
                    eq("dept_head_status", "pending")
                }
            }
            .decodeList<ChangeRequestDto>()
            .map { it.toDomain() }
    }

    override suspend fun getAllPendingRequests(): List<ChangeRequest> {
        return supabase.postgrest.from("change_requests")
            .select { filter { eq("admin_status", "pending") } }
            .decodeList<ChangeRequestDto>()
            .map { it.toDomain() }
    }

    override suspend fun updateDeptHeadReview(
        requestId: Int, status: RequestStatus, note: String?, reviewedBy: String
    ) {
        supabase.postgrest.from("change_requests")
            .update({
                set("dept_head_status", status.name.lowercase())
                set("dept_head_reviewed_by", reviewedBy)
                if (note != null) set("dept_head_note", note)
                set("dept_head_reviewed_at", "now()")
                updateOverallStatus(status, isAdmin = false)
            }) { filter { eq("id", requestId) } }
    }

    override suspend fun updateAdminReview(
        requestId: Int, status: RequestStatus, note: String?, reviewedBy: String
    ) {
        supabase.postgrest.from("change_requests")
            .update({
                set("admin_status", status.name.lowercase())
                set("admin_reviewed_by", reviewedBy)
                if (note != null) set("admin_note", note)
                set("admin_reviewed_at", "now()")
            }) { filter { eq("id", requestId) } }
    }

    override suspend fun getRequestById(id: Int): ChangeRequest? {
        return supabase.postgrest.from("change_requests")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<ChangeRequestDto>()
            ?.toDomain()
    }

    private fun ChangeRequest.toDto() = ChangeRequestDto(
        lecturerId = lecturerId,
        requestType = requestType,
        currentData = currentData,
        requestedData = requestedData,
        reason = reason,
        status = status.name.lowercase(),
        approvalMode = approvalMode.name.lowercase()
    )

    private fun ChangeRequestDto.toDomain() = ChangeRequest(
        id = id, lecturerId = lecturerId, requestType = requestType,
        currentData = currentData, requestedData = requestedData, reason = reason,
        status = parseStatus(status), approvalMode = parseApprovalMode(approvalMode),
        deptHeadStatus = parseStatus(deptHeadStatus),
        deptHeadReviewedBy = deptHeadReviewedBy, deptHeadNote = deptHeadNote,
        deptHeadReviewedAt = deptHeadReviewedAt,
        adminStatus = parseStatus(adminStatus),
        adminReviewedBy = adminReviewedBy, adminNote = adminNote,
        adminReviewedAt = adminReviewedAt, createdAt = createdAt
    )

    private fun parseStatus(s: String) =
        RequestStatus.entries.find { it.name.equals(s, ignoreCase = true) } ?: RequestStatus.PENDING

    private fun parseApprovalMode(s: String) =
        ApprovalMode.entries.find { it.name.equals(s, ignoreCase = true) } ?: ApprovalMode.DUAL_APPROVAL

    @Suppress("UNUSED_PARAMETER")
    private fun updateOverallStatus(status: RequestStatus, isAdmin: Boolean) {
        // Overall status is computed by a DB trigger or checked at read time
    }
}
