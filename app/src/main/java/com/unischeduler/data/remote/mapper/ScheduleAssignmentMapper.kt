package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.ScheduleAssignmentDto
import com.unischeduler.domain.model.ScheduleAssignment

object ScheduleAssignmentMapper {
    fun toDomain(dto: ScheduleAssignmentDto): ScheduleAssignment = ScheduleAssignment(
        id = dto.id,
        courseId = dto.courseId,
        courseCode = dto.courseCode ?: "",
        courseName = dto.courseName ?: "",
        lecturerId = dto.lecturerId,
        lecturerName = dto.lecturerName ?: "",
        dayOfWeek = dto.dayOfWeek,
        slotIndex = dto.slotIndex,
        classroom = dto.classroom ?: "",
        semester = dto.semester ?: "",
        isLocked = dto.isLocked ?: false
    )

    fun toDto(domain: ScheduleAssignment): ScheduleAssignmentDto = ScheduleAssignmentDto(
        id = domain.id,
        courseId = domain.courseId,
        courseCode = domain.courseCode,
        courseName = domain.courseName,
        lecturerId = domain.lecturerId,
        lecturerName = domain.lecturerName,
        dayOfWeek = domain.dayOfWeek,
        slotIndex = domain.slotIndex,
        classroom = domain.classroom,
        semester = domain.semester,
        isLocked = domain.isLocked
    )
}
