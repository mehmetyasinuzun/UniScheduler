package com.unischeduler.domain.repository

import com.unischeduler.domain.model.Department
import com.unischeduler.domain.model.Lecturer

interface LecturerRepository {
    suspend fun getLecturersByDepartment(departmentId: Int): List<Lecturer>
    suspend fun getLecturerById(id: Int): Lecturer?
    suspend fun getLecturerByProfileId(profileId: String): Lecturer?
    suspend fun upsertLecturer(lecturer: Lecturer): Lecturer
    suspend fun upsertLecturers(lecturers: List<Lecturer>)
    suspend fun deleteLecturer(id: Int)
    suspend fun getDepartments(): List<Department>
    suspend fun getDepartmentById(id: Int): Department?
    suspend fun upsertDepartment(department: Department): Department
}
