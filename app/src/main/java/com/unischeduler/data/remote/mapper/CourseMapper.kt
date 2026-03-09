package com.unischeduler.data.remote.mapper

import com.unischeduler.data.remote.dto.CourseDto
import com.unischeduler.domain.model.Course

fun CourseDto.toDomain() = Course(
    id = id,
    code = code,
    name = name,
    departmentId = departmentId,
    credit = credit,
    colorHex = colorHex
)

fun Course.toDto() = CourseDto(
    id = id,
    code = code,
    name = name,
    departmentId = departmentId,
    credit = credit,
    colorHex = colorHex
)
