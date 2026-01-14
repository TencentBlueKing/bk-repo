package com.tencent.bkrepo.auth.pojo.role

import com.tencent.bkrepo.auth.pojo.DeptInfo

data class UpdateRoleRequest(
    val name: String?,
    val description: String?,
    val userIds: Set<String>?,
    val deptInfoList: List<DeptInfo>?
)
