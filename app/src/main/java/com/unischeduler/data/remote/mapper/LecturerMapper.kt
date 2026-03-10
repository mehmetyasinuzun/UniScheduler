package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.LecturerDto
import com.unischeduler.domain.model.Lecturer

fun LecturerDto.toDomain() = Lecturer(
    id = id,
    profileId = profileId,
    fullName = fullName,
    title = title,
    departmentId = departmentId,
    username = username,
    password = password,
    inviteCode = inviteCode
)

fun Lecturer.toDto() = LecturerDto(
    id = id,
    profileId = profileId,
    fullName = fullName,
    title = title,
    departmentId = departmentId,
    username = username,
    password = password,
    inviteCode = inviteCode
)
