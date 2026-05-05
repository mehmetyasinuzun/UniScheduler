package com.unischeduler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DepartmentInsert(
    @SerialName("org_id") val orgId: Int,
    val name: String
)

@Serializable
data class CourseInsert(
    @SerialName("org_id") val orgId: Int,
    val code: String,
    val name: String,
    @SerialName("theory_hours") val theoryHours: Int = 0,
    @SerialName("lab_hours") val labHours: Int = 0,
    @SerialName("credits") val credits: Int = 0,
    @SerialName("department_id") val departmentId: Int? = null
)

@Serializable
data class ClassroomInsert(
    @SerialName("org_id") val orgId: Int,
    @SerialName("room_code") val roomCode: String,
    val capacity: Int,
    @SerialName("department_id") val departmentId: Int? = null,
    val type: String = "theory"
)

@Serializable
data class UserInsert(
    val id: String,
    @SerialName("org_id") val orgId: Int,
    val username: String,
    val role: String,
    @SerialName("must_change_password") val mustChangePassword: Boolean
)

@Serializable
data class LecturerInsert(
    @SerialName("org_id") val orgId: Int,
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("department_id") val departmentId: Int,
    val email: String? = null
)

@Serializable
data class OfferingInsert(
    @SerialName("org_id") val orgId: Int,
    @SerialName("course_id") val courseId: Int,
    @SerialName("academic_year") val academicYear: String,
    val term: String,
    @SerialName("class_year") val classYear: Int,
    val section: String,
    val capacity: Int
)

@Serializable
data class ScheduleEntryInsert(
    @SerialName("org_id") val orgId: Int,
    @SerialName("offering_id") val offeringId: Int,
    @SerialName("lecturer_id") val lecturerId: Int,
    @SerialName("classroom_id") val classroomId: Int,
    val day: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String
)

@Serializable
data class LecturerAvailabilityInsert(
    @SerialName("org_id") val orgId: Int,
    @SerialName("lecturer_id") val lecturerId: Int,
    val day: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String
)

@Serializable
data class ClientErrorLogInsert(
    @SerialName("org_id") val orgId: Int? = null,
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
    val role: String? = null,
    val screen: String,
    val action: String? = null,
    val message: String,
    @SerialName("stack_trace") val stackTrace: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null
)
