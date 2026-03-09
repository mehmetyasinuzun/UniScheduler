package com.unischeduler.domain.model

data class Department(
    val id: Int,
    val name: String,
    val code: String,
    val deptHeadPermission: DeptHeadPermission = DeptHeadPermission.APPROVAL_REQUIRED
)
