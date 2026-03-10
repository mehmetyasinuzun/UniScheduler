package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.AvailabilitySlotDto
import com.unischeduler.data.remote.dto.ScheduleAssignmentDto
import com.unischeduler.data.remote.dto.ScheduleConfigDto
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.repository.ScheduleRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : ScheduleRepository {

    override suspend fun getAssignmentsByDepartment(departmentId: Int): List<ScheduleAssignment> {
        val courseIds = supabase.postgrest.from("courses")
            .select { filter { eq("department_id", departmentId) } }
            .decodeList<com.unischeduler.data.remote.dto.CourseDto>()
            .map { it.id }

        if (courseIds.isEmpty()) return emptyList()

        return supabase.postgrest.from("schedule_assignments")
            .select { filter { isIn("course_id", courseIds) } }
            .decodeList<ScheduleAssignmentDto>()
            .map { it.toDomain() }
    }

    override suspend fun getAssignmentsByLecturer(lecturerId: Int): List<ScheduleAssignment> {
        return supabase.postgrest.from("schedule_assignments")
            .select { filter { eq("lecturer_id", lecturerId) } }
            .decodeList<ScheduleAssignmentDto>()
            .map { it.toDomain() }
    }

    override suspend fun getAllAssignments(): List<ScheduleAssignment> {
        return supabase.postgrest.from("schedule_assignments")
            .select()
            .decodeList<ScheduleAssignmentDto>()
            .map { it.toDomain() }
    }

    override suspend fun upsertAssignment(assignment: ScheduleAssignment): ScheduleAssignment {
        val result = supabase.postgrest.from("schedule_assignments")
            .upsert(assignment.toDto()) { select() }
            .decodeSingle<ScheduleAssignmentDto>()
        return result.toDomain()
    }

    override suspend fun upsertAssignments(assignments: List<ScheduleAssignment>) {
        supabase.postgrest.from("schedule_assignments")
            .upsert(assignments.map { it.toDto() })
    }

    override suspend fun deleteAssignment(id: Int) {
        supabase.postgrest.from("schedule_assignments")
            .delete { filter { eq("id", id) } }
    }

    override suspend fun lockAssignment(id: Int, locked: Boolean) {
        supabase.postgrest.from("schedule_assignments")
            .update({ set("is_locked", locked) }) { filter { eq("id", id) } }
    }

    override suspend fun getAvailability(lecturerId: Int): List<AvailabilitySlot> {
        return supabase.postgrest.from("availability_slots")
            .select { filter { eq("lecturer_id", lecturerId) } }
            .decodeList<AvailabilitySlotDto>()
            .map { AvailabilitySlot(it.id, it.lecturerId, it.dayOfWeek, it.slotIndex, it.isAvailable) }
    }

    override suspend fun upsertAvailability(slots: List<AvailabilitySlot>) {
        val dtos = slots.map {
            AvailabilitySlotDto(it.id, it.lecturerId, it.dayOfWeek, it.slotIndex, it.isAvailable)
        }
        supabase.postgrest.from("availability_slots").upsert(dtos)
    }

    override suspend fun getScheduleConfig(departmentId: Int): ScheduleConfig? {
        return supabase.postgrest.from("schedule_configs")
            .select { filter { eq("department_id", departmentId) } }
            .decodeSingleOrNull<ScheduleConfigDto>()
            ?.let { ScheduleConfig(it.id, it.departmentId, it.slotDurationMinutes, it.dayStartTime, it.dayEndTime, it.activeDays) }
    }

    override suspend fun upsertScheduleConfig(config: ScheduleConfig): ScheduleConfig {
        val dto = ScheduleConfigDto(config.id, config.departmentId, config.slotDurationMinutes, config.dayStartTime, config.dayEndTime, config.activeDays)
        val result = supabase.postgrest.from("schedule_configs")
            .upsert(dto) { select() }
            .decodeSingle<ScheduleConfigDto>()
        return ScheduleConfig(result.id, result.departmentId, result.slotDurationMinutes, result.dayStartTime, result.dayEndTime, result.activeDays)
    }

    override fun observeAssignments(departmentId: Int): Flow<List<ScheduleAssignment>> {
        val channel = supabase.channel("schedule-$departmentId")
        val flow = channel.postgresChangeFlow<PostgresAction>("public") {
            table = "schedule_assignments"
        }
        return flow.map { getAssignmentsByDepartment(departmentId) }
    }

    override suspend fun uploadExcelFile(fileName: String, bytes: ByteArray): String {
        val bucket = supabase.storage.from("excel-uploads")
        val path = "imports/$fileName"
        bucket.upload(path, bytes)
        return bucket.publicUrl(path)
    }

    private fun ScheduleAssignmentDto.toDomain() = ScheduleAssignment(
        id = id, courseId = courseId,
        courseCode = courseCode, courseName = courseName,
        lecturerId = lecturerId, lecturerName = lecturerName,
        dayOfWeek = dayOfWeek, slotIndex = slotIndex,
        classroom = classroom, semester = semester, isLocked = isLocked
    )

    private fun ScheduleAssignment.toDto() = ScheduleAssignmentDto(
        id = id, courseId = courseId,
        courseCode = courseCode, courseName = courseName,
        lecturerId = lecturerId, lecturerName = lecturerName,
        dayOfWeek = dayOfWeek, slotIndex = slotIndex,
        classroom = classroom, semester = semester, isLocked = isLocked
    )
}
