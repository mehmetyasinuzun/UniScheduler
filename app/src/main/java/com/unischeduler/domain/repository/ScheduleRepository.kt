package com.unischeduler.domain.repository

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    suspend fun getAssignmentsByDepartment(departmentId: Int): List<ScheduleAssignment>
    suspend fun getAssignmentsByLecturer(lecturerId: Int): List<ScheduleAssignment>
    suspend fun getAllAssignments(): List<ScheduleAssignment>
    suspend fun upsertAssignment(assignment: ScheduleAssignment): ScheduleAssignment
    suspend fun upsertAssignments(assignments: List<ScheduleAssignment>)
    suspend fun deleteAssignment(id: Int)
    suspend fun lockAssignment(id: Int, locked: Boolean)

    suspend fun getAvailability(lecturerId: Int): List<AvailabilitySlot>
    suspend fun upsertAvailability(slots: List<AvailabilitySlot>)

    suspend fun getScheduleConfig(departmentId: Int): ScheduleConfig?
    suspend fun upsertScheduleConfig(config: ScheduleConfig): ScheduleConfig

    fun observeAssignments(departmentId: Int): Flow<List<ScheduleAssignment>>

    suspend fun uploadExcelFile(fileName: String, bytes: ByteArray): String
}
