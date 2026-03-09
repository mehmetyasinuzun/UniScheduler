package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.ScheduleDraftDto
import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.domain.repository.DraftRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DraftRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : DraftRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getDraftsByDepartment(departmentId: Int): List<ScheduleDraft> {
        return supabase.postgrest.from("schedule_drafts")
            .select { filter { eq("department_id", departmentId) } }
            .decodeList<ScheduleDraftDto>()
            .map { it.toDomain() }
    }

    override suspend fun getDraftById(id: Int): ScheduleDraft? {
        return supabase.postgrest.from("schedule_drafts")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<ScheduleDraftDto>()
            ?.toDomain()
    }

    override suspend fun getPendingDrafts(): List<ScheduleDraft> {
        return supabase.postgrest.from("schedule_drafts")
            .select { filter { eq("status", "pending") } }
            .decodeList<ScheduleDraftDto>()
            .map { it.toDomain() }
    }

    override suspend fun createDraft(draft: ScheduleDraft): ScheduleDraft {
        val dto = ScheduleDraftDto(
            departmentId = draft.departmentId,
            createdBy = draft.createdBy,
            title = draft.title,
            assignments = json.encodeToString(draft.assignments.map { it.toJsonMap() }),
            softScore = draft.softScore,
            status = draft.status.name.lowercase()
        )
        val result = supabase.postgrest.from("schedule_drafts")
            .insert(dto) { select() }
            .decodeSingle<ScheduleDraftDto>()
        return result.toDomain()
    }

    override suspend fun updateDraftStatus(
        draftId: Int,
        status: DraftStatus,
        adminNote: String?,
        reviewedBy: String?
    ) {
        supabase.postgrest.from("schedule_drafts")
            .update({
                set("status", status.name.lowercase())
                if (adminNote != null) set("admin_note", adminNote)
                if (reviewedBy != null) set("reviewed_by", reviewedBy)
                set("reviewed_at", "now()")
            }) {
                filter { eq("id", draftId) }
            }
    }

    override suspend fun deleteDraft(id: Int) {
        supabase.postgrest.from("schedule_drafts")
            .delete { filter { eq("id", id) } }
    }

    private fun ScheduleDraftDto.toDomain(): ScheduleDraft {
        val assignments = try {
            json.decodeFromString<List<AssignmentJson>>(assignments).map {
                ScheduleAssignment(
                    courseId = it.course_id, courseCode = it.course_code, courseName = it.course_name,
                    lecturerId = it.lecturer_id, lecturerName = it.lecturer_name,
                    dayOfWeek = it.day_of_week, slotIndex = it.slot_index,
                    classroom = it.classroom, isLocked = it.is_locked
                )
            }
        } catch (_: Exception) { emptyList() }

        return ScheduleDraft(
            id = id, departmentId = departmentId, createdBy = createdBy,
            title = title, assignments = assignments, softScore = softScore,
            status = DraftStatus.entries.find { it.name.equals(status, ignoreCase = true) } ?: DraftStatus.DRAFT,
            adminNote = adminNote, reviewedBy = reviewedBy,
            createdAt = createdAt, reviewedAt = reviewedAt
        )
    }

    private fun ScheduleAssignment.toJsonMap() = AssignmentJson(
        course_id = courseId, course_code = courseCode, course_name = courseName,
        lecturer_id = lecturerId, lecturer_name = lecturerName,
        day_of_week = dayOfWeek, slot_index = slotIndex,
        classroom = classroom, is_locked = isLocked
    )

    @kotlinx.serialization.Serializable
    private data class AssignmentJson(
        val course_id: Int = 0,
        val course_code: String = "",
        val course_name: String = "",
        val lecturer_id: Int = 0,
        val lecturer_name: String = "",
        val day_of_week: Int = 0,
        val slot_index: Int = 0,
        val classroom: String = "",
        val is_locked: Boolean = false
    )
}
