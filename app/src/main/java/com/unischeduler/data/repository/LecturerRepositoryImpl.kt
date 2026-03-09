package com.unischeduler.data.repository

import com.unischeduler.data.remote.dto.DepartmentDto
import com.unischeduler.data.remote.dto.LecturerDto
import com.unischeduler.data.remote.mapper.toDomain
import com.unischeduler.data.remote.mapper.toDto
import com.unischeduler.domain.model.Department
import com.unischeduler.domain.model.DeptHeadPermission
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.repository.LecturerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

class LecturerRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : LecturerRepository {

    override suspend fun getLecturersByDepartment(departmentId: Int): List<Lecturer> {
        return supabase.postgrest.from("lecturers")
            .select { filter { eq("department_id", departmentId) } }
            .decodeList<LecturerDto>()
            .map { it.toDomain() }
    }

    override suspend fun getLecturerById(id: Int): Lecturer? {
        return supabase.postgrest.from("lecturers")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<LecturerDto>()
            ?.toDomain()
    }

    override suspend fun getLecturerByProfileId(profileId: String): Lecturer? {
        return supabase.postgrest.from("lecturers")
            .select { filter { eq("profile_id", profileId) } }
            .decodeSingleOrNull<LecturerDto>()
            ?.toDomain()
    }

    override suspend fun upsertLecturer(lecturer: Lecturer): Lecturer {
        val result = supabase.postgrest.from("lecturers")
            .upsert(lecturer.toDto()) { select() }
            .decodeSingle<LecturerDto>()
        return result.toDomain()
    }

    override suspend fun upsertLecturers(lecturers: List<Lecturer>) {
        supabase.postgrest.from("lecturers")
            .upsert(lecturers.map { it.toDto() })
    }

    override suspend fun deleteLecturer(id: Int) {
        supabase.postgrest.from("lecturers")
            .delete { filter { eq("id", id) } }
    }

    override suspend fun getDepartments(): List<Department> {
        return supabase.postgrest.from("departments")
            .select()
            .decodeList<DepartmentDto>()
            .map { it.toDepartment() }
    }

    override suspend fun getDepartmentById(id: Int): Department? {
        return supabase.postgrest.from("departments")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<DepartmentDto>()
            ?.toDepartment()
    }

    override suspend fun upsertDepartment(department: Department): Department {
        val dto = DepartmentDto(
            id = department.id,
            name = department.name,
            code = department.code,
            deptHeadPermission = department.deptHeadPermission.name.lowercase()
        )
        val result = supabase.postgrest.from("departments")
            .upsert(dto) { select() }
            .decodeSingle<DepartmentDto>()
        return result.toDepartment()
    }

    private fun DepartmentDto.toDepartment() = Department(
        id = id,
        name = name,
        code = code,
        deptHeadPermission = DeptHeadPermission.entries.find {
            it.name.equals(deptHeadPermission, ignoreCase = true)
        } ?: DeptHeadPermission.APPROVAL_REQUIRED
    )
}
